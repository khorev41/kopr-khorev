package sk.upjs.kopr.copy.server;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.exceptions.DirectoryNotFoundException;
import sk.upjs.kopr.tools.PropertiesManager;
import sk.upjs.kopr.tools.Searcher;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Server {

    private final PropertiesManager props = PropertiesManager.getInstance();

    private ServerSocket serverSocket;
    private List<Socket> sockets = new ArrayList<>();

    private AtomicBoolean isRunning = new AtomicBoolean();

    private BlockingQueue<FileInfo> filesToSend = new LinkedBlockingQueue<>();
    private int totalFiles;
    private long totalLength;

    private List<File> allFiles;
    private int allFilesCount;
    private long allFileSize;

    private ExecutorService executor;


    public void start() {
        log.info("Server was started");
        try {
            search();
            connect();
            managingConnection();

            connectClients();
            sendFiles();
        } catch (DirectoryNotFoundException e) {
            log.error(e.toString());
            stopServer();
        } catch (InterruptedException e) {
            log.info("Server was terminated");
        } finally {
            deleteProgressFile();
            try {
                clearAllVariables();
                reconnect();
            } catch (InterruptedException e) {
                log.error("Error while sending files");
            }
        }
    }

    private void clearAllVariables() {
        sockets = new ArrayList<>();
        isRunning = new AtomicBoolean(false);
        filesToSend = new LinkedBlockingQueue<>();
        for (File file : allFiles) {
            filesToSend.add(new FileInfo(file.getAbsolutePath(), 0L, file.length()));
        }
        totalFiles = allFilesCount;
        totalLength = allFileSize;

    }

    public void connect() {
        if (!isRunning.get()) {
            isRunning.set(true);
            try {
                serverSocket = new ServerSocket(props.getPort());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void managingConnection() {
        try {
            Socket managingSocket = serverSocket.accept();
            ObjectOutputStream oos = new ObjectOutputStream(managingSocket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(managingSocket.getInputStream());

            props.setNumberOfSockets(ois.readInt());

            oos.writeInt(allFilesCount);
            oos.flush();

            oos.writeLong(allFileSize);
            oos.flush();

            String command = ois.readUTF();
            if ("RESUME".equals(command)) {
                filesToSend = (BlockingQueue<FileInfo>) ois.readObject();
                new File("client_progress.obj").delete();
            } else if ("START".equals(command)) {
                oos.writeInt(totalFiles);
                oos.writeLong(totalLength);
                oos.writeObject(filesToSend);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private void sendFiles() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(sockets.size());
        executor = Executors.newFixedThreadPool(sockets.size());

        List<Future<Integer>> futures = new ArrayList<>();
        for (Socket socket : sockets) {
            FileSendTask task = new FileSendTask(filesToSend, socket, latch);
            futures.add(executor.submit(task));
        }

        boolean flag = false;
        try {
            for (Future<Integer> future : futures) {
                if (future.get() == -1) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                log.error("Client was disconnected.");
                reconnect();
                executor.shutdownNow();
                return;
            }

            latch.await();
            for (Socket socket : sockets) {
                socket.close();
            }
            executor.shutdown();
        } catch (ExecutionException | IOException e) {
            log.error("Error while sending files");
        }

    }

    public void search() throws DirectoryNotFoundException {
        log.info("Searching over directory");

        File directory = new File(props.getDirectory());
        if (!directory.exists() || !directory.isDirectory()) {
            throw new DirectoryNotFoundException(directory);
        }

        allFiles = Searcher.search(directory);
        allFilesCount = allFiles.size();
        allFileSize = allFiles.stream()
                .mapToLong(File::length)
                .sum();

        try {
            filesToSend = loadProgress();
            totalLength = 0;
            if (filesToSend.size() > 0) {
                filesToSend.forEach(file -> totalLength += file.size - file.offset);
            } else {
                for (File file : allFiles) {
                    totalLength += file.length();
                    filesToSend.add(new FileInfo(file.getAbsolutePath(), 0L, file.length()));
                }
            }
            totalFiles = filesToSend.size();

            log.info("Searching ended. Found " + allFilesCount + " files");
            log.info("Remaining files to send: " + totalFiles);
            log.info("Remaining bytes to send: " + totalLength);
        } catch (Exception e) {
            e.printStackTrace();
        }
        new Pair<>(totalFiles, totalLength);
    }

    public synchronized void reconnect() throws InterruptedException {
        log.info("Waiting for connection...");

        isRunning.set(true);
        executor.shutdown();

        totalLength = 0;
        if (filesToSend.size() > 0) {
            filesToSend.forEach(file -> totalLength += file.size - file.offset);
        }
        totalFiles = filesToSend.size();

        managingConnection();
        connectClients();
        sendFiles();
        deleteProgressFile();
    }

    private void connectClients() {
        sockets.clear();
        try {
            for (int i = 0; i < props.getNumberOfSockets(); i++) {
                sockets.add(serverSocket.accept());
            }
        } catch (IOException e) {
            log.error("Error while connecting to clients");
        }
        log.info("Clients connected successfully");
    }

    public static synchronized void saveProgress(BlockingQueue<FileInfo> files) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("server_progress.obj"))) {
            oos.writeObject(files);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized BlockingQueue<FileInfo> loadProgress() {
        File progressFile = new File("server_progress.obj");

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(progressFile))) {
            BlockingQueue<FileInfo> result = (BlockingQueue<FileInfo>) ois.readObject();
            progressFile.delete();
            log.info("Loading progress: " + result);
            return result;
        } catch (FileNotFoundException e) {
            return new LinkedBlockingQueue<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        log.info("Loading progress: " + new LinkedBlockingQueue<>());
        return new LinkedBlockingQueue<>();
    }

    public void deleteProgressFile() {
        File file = new File("server_progress.obj");
        if (file.exists()) {
            file.delete();
        }
    }

    private void stopServer() {
        isRunning.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.error("Error while closing the server socket");
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

}
