package sk.upjs.kopr.copy.client;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import javafx.beans.property.DoubleProperty;
import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.exceptions.ServerTerminatedConnectionException;
import sk.upjs.kopr.tools.FilePathChanger;

import static com.sun.javafx.application.PlatformImpl.runLater;

public class FileReceiveTask implements Callable<Integer> {

	private static final int BUFFER_SIZE = 16384;
	private final BlockingQueue<FileInfo> files;
	private final CountDownLatch latch;

	private final Socket socket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	private final DoubleProperty fileProgress;
	private final DoubleProperty fileSizeProgress;

	public FileReceiveTask(BlockingQueue<FileInfo> files, Socket socket, CountDownLatch latch, DoubleProperty fileProgress, DoubleProperty fileSizeProgress) throws IOException {
		this.socket = socket;

		this.files = files;
		this.latch = latch;

		this.fileProgress = fileProgress;
		this.fileSizeProgress = fileSizeProgress;
	}

	@Override
	public Integer call() throws Exception {
		createStreams();

		while (true) {
			try {
				FileInfo origFile = files.poll();
				if (origFile == null) { // sending poison pill
					break;
				}
				System.out.println(Thread.currentThread().getName() + ": Zacinam kopirovanie od " + origFile.offset);
				File fileToSave = new File(FilePathChanger.modifyBasePath(origFile.fileName));
				MyFileWriter myFileWriter = MyFileWriter.getInstance(fileToSave, origFile.size);

				oos.writeObject(origFile);
				oos.flush();

				receiveFile(origFile, myFileWriter);
			} catch (ServerTerminatedConnectionException e) {
				return -1;
			} catch (SocketException e) {
				System.out.println("Server was disconnected. Trying to reconnect...");
				return -1;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sendPoisonPill();
		return 1;
	}

	private void sendPoisonPill() {
		try {
			oos.writeObject(new FileInfo("", 0L, -1L));
			oos.flush();

			System.out.println(Thread.currentThread().getName() + ": CLIENT - Sending poison pill");
		} catch (EOFException e) {
			closeConnection();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void createStreams() {
		try {
			oos = new ObjectOutputStream(socket.getOutputStream());
			ois = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void receiveFile(FileInfo fileInfo, MyFileWriter myFileWriter) throws ServerTerminatedConnectionException {
		long fileOffset = fileInfo.offset;
		try {
			while (true) {
				long min = Math.min(fileInfo.size - fileOffset, BUFFER_SIZE);
				byte[] bytes = ois.readNBytes((int) min);
				if (bytes.length > 0) {
					myFileWriter.write(fileOffset, bytes, 0, bytes.length);
					runLater(() -> fileSizeProgress.set(fileSizeProgress.get() + bytes.length));
				}
				if (bytes.length < BUFFER_SIZE) {
					latch.countDown();
					myFileWriter.close();
					runLater(() -> fileProgress.set(fileProgress.get() + 1));
					System.out.println("fileProgress:" + fileProgress);
					System.out.println(Thread.currentThread().getName() + ": "
							+FilePathChanger.getLastDirectoryName(fileInfo.fileName) + " saved" );
					break;
				}
				fileOffset += bytes.length;
				if ((fileOffset / BUFFER_SIZE) % 1000 == 0)
					System.out.println(fileInfo.fileName + ": " + fileOffset);
			}

		} catch (IOException e) {
			FileInfo info = new FileInfo(fileInfo.fileName, fileOffset, fileInfo.size);
			files.add(info);
			saveProgress(files);
			System.out.println(Thread.currentThread().getName() + ": Progress saved!!!");
			throw new ServerTerminatedConnectionException();
		}
	}

	public void saveProgress(BlockingQueue<FileInfo> files) {
		synchronized (files) {
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("client_progress.obj"))) {
				oos.writeObject(files);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void closeConnection() {
		try {
			if (oos != null)
				oos.close();
			if (ois != null)
				ois.close();
			if (socket != null && !socket.isClosed())
				socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
