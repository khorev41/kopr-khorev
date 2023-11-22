package sk.upjs.kopr.copy.client;

import javafx.beans.property.BooleanProperty;
import lombok.extern.slf4j.Slf4j;
import sk.upjs.kopr.copy.FileInfo;
import sk.upjs.kopr.tools.FilePathChanger;
import sk.upjs.kopr.tools.PropertiesManager;
import sk.upjs.kopr.tools.ThreadSafeLong;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.sun.javafx.application.PlatformImpl.runLater;

@Slf4j
public class Client implements Runnable {

    private final static PropertiesManager props = PropertiesManager.getInstance();
    private int numberOfSockets;
    private BlockingQueue<FileInfo> files;

    public int totalFiles;
    public long totalLength;

    private final ThreadSafeLong fileSizeProgressProperty;
    private final ThreadSafeLong fileCountProgressProperty;

    private final ThreadSafeLong allFileCountProperty;
    private final ThreadSafeLong allFileSizeProperty;
    private final BooleanProperty finishProperty;

    public Client(ThreadSafeLong fileSizeProgress, ThreadSafeLong fileProgress, ThreadSafeLong totalFileCount, ThreadSafeLong totalFileSize, BooleanProperty finishProperty) {
        this.fileSizeProgressProperty = fileSizeProgress;
        this.fileCountProgressProperty = fileProgress;
        this.allFileCountProperty = totalFileCount;
        this.allFileSizeProperty = totalFileSize;
        this.finishProperty = finishProperty;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        log.info("Client started");
        try {
            Socket managingSocket = getManagingSocket();
            if (managingSocket == null) {
                deleteProgress();
                return;
            }

            ObjectOutputStream oos = new ObjectOutputStream(managingSocket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(managingSocket.getInputStream());

            numberOfSockets = props.getNumberOfSockets();
            oos.writeInt(numberOfSockets);
            oos.flush();

            int allFilesCount = ois.readInt();
            long allFileSize = ois.readLong();

            runLater(() -> allFileCountProperty.set(allFilesCount));
            runLater(() -> allFileSizeProperty.set(allFileSize));

            files = loadProgress();
            if (files.size() > 0) {

                log.info("RESUME copying");
                oos.writeUTF("RESUME");
                oos.writeObject(files);
                oos.flush();

                if (ois.readUTF().equals("DELETE CLIENT PROGRESS")) {
                    new File("client_progress.obj").delete();
                    log.info("Client progress was deleted");
                }
            } else {
                log.info("START copying");
                oos.writeUTF("START");
                oos.flush();

                totalFiles = ois.readInt();
                totalLength = ois.readLong();

                files = (BlockingQueue<FileInfo>) ois.readObject();

                runLater(() -> fileCountProgressProperty.set(allFilesCount - totalFiles));
                runLater(() -> fileSizeProgressProperty.set(allFileSize - totalLength));

                createDirectoriesAndFile(files);
            }
            oos.writeUTF("DELETE SERVER PROGRESS");
            oos.flush();

            receiveFiles();
            runLater(() -> finishProperty.set(true));
        } catch (InterruptedException e) {
            log.error("Client was interrupted");
        } catch (IOException e) {
            log.error("Connection reset");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Socket getManagingSocket() {
        Socket managingSocket = null;
        while (true) {
            try {
                managingSocket = new Socket(props.getIP(), props.getPort());
                return managingSocket;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                log.warn("Server is not available... Reconnecting in 1 second");
                sleep(1000);
            }
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void createDirectoriesAndFile(BlockingQueue<FileInfo> files) {
        for (FileInfo fileInfo : files) {
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
            FileReceiveTask task = new FileReceiveTask(files, socket, latch, fileCountProgressProperty, fileSizeProgressProperty);
            futures.add(executor.submit(task));
        }

        try {
            for (Future<Integer> future : futures) {
                if (future.get() == -1) {
                    log.info("Total downloaded " + fileSizeProgressProperty.get());
                    run();
                    executor.shutdown();
                    return;
                }
            }

            for (Socket socket : sockets) {
                if (!socket.isClosed()) {
                    socket.close();
                }
            }

            latch.await();
            executor.shutdown();
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static synchronized BlockingQueue<FileInfo> loadProgress() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("client_progress.obj"))) {
            return (BlockingQueue<FileInfo>) ois.readObject();
        } catch (FileNotFoundException e) {
            return new LinkedBlockingQueue<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new LinkedBlockingQueue<>();
    }

    public static synchronized void saveProgress(BlockingQueue<FileInfo> files) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("client_progress.obj"))) {
            oos.writeObject(files);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteProgress() {
        File file = new File("client_progress.obj");
        if (file.exists()) {
            file.delete();
        }
    }


}
