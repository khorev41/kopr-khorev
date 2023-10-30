package sk.upjs.kopr.copy.server;

import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.io.*;

import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.exceptions.ClientTerminatedConnectionException;
import sk.upjs.kopr.tools.FilePathChanger;

public class FileSendTask implements Callable<Integer> {
	private static final int BLOCK_SIZE = 16384; // 16 kB
	private final BlockingQueue<FileInfo> files;
	private final Socket socket;
	private final CountDownLatch countDownLatch;

	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	public FileSendTask(BlockingQueue<FileInfo> files, Socket socket, CountDownLatch countDownLatch) {
		this.files = files;
		this.socket = socket;
		this.countDownLatch = countDownLatch;
	}

	@Override
	public Integer call() throws Exception {
		createStreams();
		while (true) {
			try {
				FileInfo fileInfo = (FileInfo) ois.readObject();
				if (fileInfo.size == -1L) { // poison pill
					System.out.println(Thread.currentThread().getName() + ": SERVER - Poison pill received");
					countDownLatch.countDown();

					closeConnection();
					break;
				}

				files.remove(fileInfo);

				System.out.println(Thread.currentThread().getName() + ": dostal som FileInfo o " + fileInfo.fileName);

				File fileToSend = new File(fileInfo.fileName);
				send(fileToSend, fileInfo);
				System.out.println(Thread.currentThread().getName() + ": "
						+ FilePathChanger.getLastDirectoryName(fileInfo.fileName) + " saved");

			} catch (ClientTerminatedConnectionException | SocketException e) {
				return -1;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return 1;

	}

	/**
	 * @param fileToSend
	 * @param fileInfo
	 * @throws IOException
	 * @throws ClientTerminatedConnectionException
	 */
	private void send(File fileToSend, FileInfo fileInfo) throws IOException, ClientTerminatedConnectionException {
		long send = 0;
		try (RandomAccessFile raf = new RandomAccessFile(fileToSend, "r")) {

			if (fileInfo.offset < 0 || fileInfo.size < 0 || fileInfo.offset > fileToSend.length()) {
				throw new RuntimeException(socket.getInetAddress() + ":" + socket.getPort() + " : " + fileInfo
						+ " exceeds the file size " + fileToSend.length());
			}
			raf.seek(fileInfo.offset);
			byte[] buffer = new byte[BLOCK_SIZE];

			for (send = 0; fileInfo.offset + send < fileInfo.size; send += BLOCK_SIZE) {
				if (ois.available() > 0) {
					throw new RuntimeException(socket.getInetAddress() + ":" + socket.getPort() + " : "
							+ "Premature closing data stream after " + send + " bytes send for " + fileInfo);
				}
				int size = (int) Math.min(BLOCK_SIZE, fileInfo.size - (fileInfo.offset + send));
				raf.read(buffer, 0, size);
				oos.write(buffer, 0, size);
			}
			oos.flush();

		} catch (IOException e) {
			files.add(new FileInfo(fileInfo.fileName, fileInfo.offset + send, fileInfo.size));
			Server.saveProgress(files);
			System.out.println(Thread.currentThread().getName() + ": SERVER - progress saved: " + files);

			throw new ClientTerminatedConnectionException();
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

	private void createStreams() {
		try {
			oos = new ObjectOutputStream(socket.getOutputStream());
			ois = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
