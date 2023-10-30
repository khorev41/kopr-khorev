package sk.upjs.kopr.copy.client;

import java.io.*;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.tools.FilePathChanger;
import sk.upjs.kopr.tools.PropertiesManager;

public class Client {

	private final static PropertiesManager props = PropertiesManager.getInstance();
	private int numberOfSockets;
	private BlockingQueue<FileInfo> files;
	private int totalFiles;
	private long totalLength;

	@SuppressWarnings("unchecked")
	public void start() throws Exception {
		System.out.println("Client is started");

		files = loadProgress();

		Socket managingSocket = null;
		try {
			managingSocket = new Socket(props.getIP(), props.getPort());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Server is not available... Reconnecting in 3 seconds");
			Thread.sleep(3000);
			start();
		}
		if (managingSocket == null) {
			deleteProgressFile();
			return;
		}

		ObjectOutputStream oos = new ObjectOutputStream(managingSocket.getOutputStream());
		ObjectInputStream ois = new ObjectInputStream(managingSocket.getInputStream());

//		numberOfSockets = Runtime.getRuntime().availableProcessors();
		numberOfSockets = 2;
		props.setNumberOfSockets(numberOfSockets);
		oos.writeInt(numberOfSockets);

		if (files.size() > 0) {
			System.out.println("RESUME");
			oos.writeUTF("RESUME");

			oos.writeObject(files);
			oos.flush();
		} else {
			oos.writeUTF("START");
			oos.flush();
			totalFiles = ois.readInt();
			totalLength = ois.readLong();

			files = (BlockingQueue<FileInfo>) ois.readObject();
			

			createDirectoriesAndFile(files);
		}
		new File("server_progress.obj").delete();
		receiveFiles();

//		outputStream.close();
//		inputStream.close();
//		managingSocket.close();
	}

	private void createDirectoriesAndFile(BlockingQueue<FileInfo> files2) {
		for (FileInfo fileInfo : files2) {
			File file = new File(FilePathChanger.modifyBasePath(fileInfo.fileName));
			System.out.println(file.getAbsolutePath());
			File parentDir = file.getParentFile();
			if (!parentDir.exists()) {
				parentDir.mkdirs();
			}
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void receiveFiles() throws Exception {
		CountDownLatch latch = new CountDownLatch(files.size());
		ExecutorService executor = Executors.newFixedThreadPool(numberOfSockets);

		List<Future<Integer>> futures = new ArrayList<>();
		List<Socket> sockets = new ArrayList<>();
		for (int i = 0; i < numberOfSockets; i++) {
			Socket socket = new Socket(props.getIP(), props.getPort());
			sockets.add(socket);
			FileReceiveTask task = new FileReceiveTask(files, socket, latch);
			futures.add(executor.submit(task));
		}

		try {
			for (Future<Integer> future : futures) {
				if (future.get() == -1) {
					start();
					executor.shutdownNow();
					return;
				}
			}
			
			
			for (Socket socket : sockets) {
				if(!socket.isClosed()) {
					socket.close();
				}
			}

			latch.await();
			System.out.println("Tu SOM");
			executor.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	private void reconnect() {

	}

	public void saveProgress(BlockingQueue<FileInfo> files) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("client_progress.obj"))) {
			oos.writeObject(files);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public BlockingQueue<FileInfo> loadProgress() {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("client_progress.obj"))) {
			return (BlockingQueue<FileInfo>) ois.readObject();
		} catch (FileNotFoundException e) {
			return new LinkedBlockingQueue<>();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return new LinkedBlockingQueue<>();
	}

	public void deleteProgressFile() {
		File file = new File("client_progress.obj");
		if (file.exists()) {
			file.delete();
		}
	}

}
