package sk.upjs.kopr.copy.server;

import java.io.ObjectOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.tools.PropertiesManager;
import sk.upjs.kopr.tools.Searcher;

public class Server {

	private final PropertiesManager props = PropertiesManager.getInstance();
	private BlockingQueue<FileInfo> filesToSend = new LinkedBlockingQueue<>();
	public static final int SERVER_PORT = 5000;
	public static final int NUMBER_OF_WORKERS = Runtime.getRuntime().availableProcessors();
	private ServerSocket serverSocket;
	private final AtomicBoolean isRunning = new AtomicBoolean();
	private List<Socket> sockets = new ArrayList<Socket>();
	private int totalFiles;
	private long totalLength;
	private ExecutorService executor;
	private CountDownLatch latch;

	public void start() {
		System.out.println("Server is started");
		try {
			filesToSend = loadProgress();
			if (filesToSend.size() > 0) {
				filesToSend.forEach(file -> {
					totalLength += file.size;
				});
				totalFiles = filesToSend.size();
			} else {
				startSearching(new File(props.getDirectory()));
			}
			System.out.println("Searching ended. Find " + totalFiles + " files");
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			connect();
		} finally {
			deleteProgressFile();
		}
	}

	public void connect() {
		if (!isRunning()) {
			isRunning.set(true);
			try {
				serverSocket = new ServerSocket(props.getPort());

				managingConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void reconnect() {
		isRunning.set(true);
		executor.shutdown();

		System.out.println("Waiting for reconnecting....");

		managingConnection();
	}

	@SuppressWarnings("unchecked")
	private void managingConnection() {
		try {
			Socket managingSocket = serverSocket.accept();
			ObjectOutputStream oos = new ObjectOutputStream(managingSocket.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(managingSocket.getInputStream());

			props.setNumberOfSockets(ois.readInt());

			String command = ois.readUTF();
			if ("RESUME".equals(command)) {
				filesToSend = (BlockingQueue<FileInfo>) ois.readObject();
				new File("client_progress.obj").delete();
				System.out.println("Prisli: " + filesToSend );

			} else if ("START".equals(command)) {
				oos.writeInt(totalFiles);
				oos.writeLong(totalLength);
				oos.writeObject(filesToSend);
			}

			connectClients();
			sendFiles();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void sendFiles() throws IOException {
		int numbersOfSockets = props.getNumberOfSockets();
		latch = new CountDownLatch(numbersOfSockets);
		executor = Executors.newFixedThreadPool(numbersOfSockets);

		List<Future<Integer>> futures = new ArrayList<>();
		for (Socket socket : sockets) {
			FileSendTask task = new FileSendTask(filesToSend, socket, latch);
			futures.add(executor.submit(task));
		}

		try {

			for (Future<Integer> future : futures) {
				if (future.get() == -1) {
					reconnect();
					break;
				}
			}

			latch.await();
			for (Socket socket : sockets) {
				socket.close();
			}
			executor.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

	}

	private void connectClients() throws IOException {
		sockets.clear();
		for (int i = 0; i < props.getNumberOfSockets(); i++) {
			sockets.add(serverSocket.accept());
		}
		System.out.println("Clients connected");
	}

	public static synchronized void saveProgress(BlockingQueue<FileInfo> files) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("server_progress.obj"))) {
			oos.writeObject(files);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static BlockingQueue<FileInfo> loadProgress() {
		File progressFile = new File("server_progress.obj");

		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(progressFile))) {
			BlockingQueue<FileInfo> result = (BlockingQueue<FileInfo>) ois.readObject();
			progressFile.delete();
			return result;
		} catch (FileNotFoundException e) {
			return new LinkedBlockingQueue<>();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}

		return new LinkedBlockingQueue<>();
	}

	public boolean isRunning() {
		return isRunning.get();
	}

	public void setIsRunning(boolean running) {
		isRunning.set(running);
	}

	public void deleteProgressFile() {
		File file = new File("server_progress.obj");
		if (file.exists()) {
			file.delete();
		}
	}

	public void startSearching(File rootDir) throws Exception {
		for (File file : Searcher.search(rootDir)) {
			totalLength += file.length();
			filesToSend.add(new FileInfo(file.getAbsolutePath(), 0L, file.length()));
		}
		totalFiles = filesToSend.size();
	}
}