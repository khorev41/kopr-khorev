package sk.upjs.kopr.controllers;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import lombok.extern.slf4j.Slf4j;
import sk.upjs.kopr.copy.client.Client;
import sk.upjs.kopr.tools.PropertiesManager;
import sk.upjs.kopr.tools.ThreadSafeLong;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class MainController {

    private final static PropertiesManager props = PropertiesManager.getInstance();

    private final ThreadSafeLong fileCountProgressProperty = new ThreadSafeLong();
    private final ThreadSafeLong fileSizeProgressProperty = new ThreadSafeLong();

    private final ThreadSafeLong allFileCount = new ThreadSafeLong();
    private final ThreadSafeLong allFileSize = new ThreadSafeLong();

    private final BooleanProperty finishProperty = new SimpleBooleanProperty();

    @FXML
    private Label copyingFinishedLabel;
    @FXML
    private Button browseButton;
    @FXML
    private ProgressBar bytesSentProgressBar;
    @FXML
    private TextField directoryTextField;
    @FXML
    private Label filesPercentLabel;
    @FXML
    private ProgressBar filesSentProgressBar;
    @FXML
    private Label filesizePercentLabel;
    @FXML
    private Button killClientButton;
    @FXML
    private Button killServerButton;
    @FXML
    private TextField numberOfThreadsTextField;
    @FXML
    private Button startCopyButton;
    @FXML
    private TextField toSaveTextField;


    @FXML
    void initialize() {
        directoryTextField.setText(props.getDirectory());
        toSaveTextField.setText(props.getPathToSave());
        numberOfThreadsTextField.setText(Runtime.getRuntime().availableProcessors() + "");
        if (checkFileExistence()){
            startCopyButton.setText("Pokračovať v kopírovaní");
        }

        setListeners();
    }

    @FXML
    void onStartCopyButtonClick(MouseEvent event) {
        props.setNumberOfSockets(Integer.parseInt(numberOfThreadsTextField.getText()));
        props.setPathToSave(toSaveTextField.getText());

        ExecutorService executor = Executors.newFixedThreadPool(1);

        Runnable clientTask = new Client(fileSizeProgressProperty, fileCountProgressProperty, allFileCount, allFileSize, finishProperty);
        executor.submit(clientTask);
        executor.shutdown();
        createFile();



        startCopyButton.setText("Kopirovanie beži");
    }

    private void setListeners() {

        fileCountProgressProperty.addListener(( oldValue, newValue) -> {
            filesPercentLabel.setText((int) (((double)newValue / allFileCount.get()) * 100) + "%");
            filesSentProgressBar.setProgress(((double)newValue / allFileCount.get()));
        });

        fileSizeProgressProperty.addListener(( oldValue, newValue) -> {
            filesizePercentLabel.setText((int) ( (double) newValue / (1024 * 1024)) + "/" + allFileSize.get() / (1024 * 1024) + " MB");
            bytesSentProgressBar.setProgress((double) newValue / allFileSize.get());
        });

        allFileSize.addListener(( oldValue, newValue) -> filesizePercentLabel.setText(0 / (1024 * 1024) + "/" + (double)newValue/ (1024 * 1024) + " MB"));
        allFileCount.addListener((oldValue, newValue) -> filesPercentLabel.setText(((0 / (double) newValue) * 100) + "%"));

        finishProperty.addListener((observable, oldValue, newValue) -> {
            deleteFile();
            startCopyButton.setText("Začať kopírovanie");
            startCopyButton.setDisable(true);
            copyingFinishedLabel.setVisible(true);
        });
    }

    public static boolean checkFileExistence() {
        File file = new File("wasFinished.txt");
        return file.exists();
    }

    public static void createFile() {
        File file = new File("wasFinished.txt");
        try {
            if (file.createNewFile()) {
                System.out.println("File created successfully.");
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.err.println("An error occurred while creating the file: " + e.getMessage());
        }
    }

    public static void deleteFile() {
        File file = new File("wasFinished.txt");

        if (file.exists()) {
            if (file.delete()) {
                System.out.println("File deleted successfully.");
            } else {
                System.err.println("Unable to delete the file.");
            }
        } else {
            System.err.println("File does not exist.");
        }
    }

}
