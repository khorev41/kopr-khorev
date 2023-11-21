package sk.upjs.kopr.copy.server;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class Server {

    private final PropertiesManager props = PropertiesManager.getInstance();

    private ServerSocket serverSocket;
    private List<Socket> sockets = new ArrayList<>();

    private AtomicBoolean isRunning = new AtomicBoolean();

    private BlockingQueue<FileInfo> filesToSend = new LinkedBlockingQueue<>();
    private AtomicInteger totalFiles = new AtomicInteger();
    private AtomicLong totalLength = new AtomicLong();

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
                reconnect();
            } catch (InterruptedException e) {
                log.error("Error while sending files");
            }
        }
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

            oos.writeInt(totalFiles.get());
            oos.flush();

            oos.writeLong(totalLength.get());
            oos.flush();

            String command = ois.readUTF();
            filesToSend = loadProgress().size() > 0 ? loadProgress() : filesToSend;
            totalLength.set(filesToSend.stream().mapToLong(f -> f.size - f.offset).sum());
            totalFiles.set(filesToSend.size());

            if ("RESUME".equals(command)) {
                filesToSend = (BlockingQueue<FileInfo>) ois.readObject();

                oos.writeUTF("DELETE CLIENT PROGRESS");
                oos.flush();
            } else if ("START".equals(command)) {
                oos.writeInt(totalFiles.get());
                oos.writeLong(totalLength.get());
                oos.writeObject(filesToSend);
                oos.flush();
            }
            if(ois.readUTF().equals("DELETE SERVER PROGRESS")){
                new File("server_progress.obj").delete();
                log.info("Server progress was deleted");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sendFiles() throws InterruptedException {
        log.info("Remaining files to send: " + totalFiles);
        log.info("Remaining bytes to send: " + totalLength);

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
                executor.shutdown();
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

        for (File file : Searcher.search(directory)){
            filesToSend.add(new FileInfo(file.getAbsolutePath(),0l,file.length()));
            totalLength.addAndGet(file.length());
            totalFiles.addAndGet(1);
        }

        log.info("Searching ended. Found " + totalFiles + " files");
    }

    private void asda(){
        filesToSend = loadProgress();
        totalLength.set(0);
        if (filesToSend.size() > 0) {
            filesToSend.forEach(file -> totalLength.addAndGet(file.size - file.offset));
        } else {
        }
        totalFiles.set(filesToSend.size());
    }

    public synchronized void reconnect() throws InterruptedException {
        log.info("Waiting for connection...");

        isRunning.set(true);
        executor.shutdown();

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

    @SuppressWarnings("unchecked")
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
