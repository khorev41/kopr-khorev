package sk.upjs.kopr.copy.client;

import lombok.extern.slf4j.Slf4j;
import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.exceptions.ServerTerminatedConnectionException;
import sk.upjs.kopr.tools.FilePathChanger;
import sk.upjs.kopr.tools.ThreadSafeLong;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.sun.javafx.application.PlatformImpl.runLater;

@Slf4j
public class FileReceiveTask implements Callable<Integer> {

	private static final int BUFFER_SIZE = 16384;
	private final BlockingQueue<FileInfo> files;
	private final CountDownLatch latch;

	private final Socket socket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	private final ThreadSafeLong fileProgress;
	private final ThreadSafeLong fileSizeProgress;

	public FileReceiveTask(BlockingQueue<FileInfo> files, Socket socket, CountDownLatch latch, ThreadSafeLong fileProgress, ThreadSafeLong fileSizeProgress) {
		this.socket = socket;
		this.files = files;
		this.latch = latch;
		this.fileProgress = fileProgress;
		this.fileSizeProgress = fileSizeProgress;
	}

	@Override
	public Integer call() {
		createStreams();

		while (true) {
			try {
				FileInfo origFile = files.poll();
				if (origFile == null) { // sending poison pill
					break;
				}

				log.info("Starting saving file " + FilePathChanger.getLastDirectoryName(origFile.fileName) + " from offset=" + origFile.offset);
				File fileToSave = new File(FilePathChanger.modifyBasePath(origFile.fileName));
				MyFileWriter myFileWriter = MyFileWriter.getInstance(fileToSave, origFile.size);

				oos.writeObject(origFile);
				oos.flush();

				receiveFile(origFile, myFileWriter);
			} catch (ServerTerminatedConnectionException | SocketException e) {
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

			log.info("Sending poison pill");
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
					log.info("File " +FilePathChanger.getLastDirectoryName(fileInfo.fileName) + " saved"  );
					break;
				}
				fileOffset += bytes.length;
			}

		} catch (IOException e) {
			FileInfo info = new FileInfo(fileInfo.fileName, fileOffset, fileInfo.size);
			files.add(info);
			Client.saveProgress(files);
			log.info("Progress saved");
			throw new ServerTerminatedConnectionException();
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
