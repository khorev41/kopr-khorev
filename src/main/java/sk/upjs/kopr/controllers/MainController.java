package sk.upjs.kopr.controllers;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.util.Pair;
import sk.upjs.kopr.copy.client.Client;
import sk.upjs.kopr.copy.server.Server;
import sk.upjs.kopr.tools.PropertiesManager;

public class MainController {

    private final static PropertiesManager props = PropertiesManager.getInstance();

    private final DoubleProperty totalFilesProgress = new SimpleDoubleProperty();
    private final DoubleProperty totalSizeProgress = new SimpleDoubleProperty();

    private int totalFileCount;
    private long totalFileSize;

    private Server server;
    private Client client;

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
        server = new Server();
        Pair<Integer, Long> search = server.search();

        totalFileCount = search.getKey();
        totalFileSize = search.getValue();

        client = new Client(totalSizeProgress, totalFilesProgress);

        directoryTextField.setText(props.getDirectory());
        toSaveTextField.setText(props.getPathToSave());
        numberOfThreadsTextField.setText(Runtime.getRuntime().availableProcessors() + "");

        setListeners();
    }

    @FXML
    void onStartCopyButtonClick(MouseEvent event) {
        props.setNumberOfSockets(Integer.valueOf(numberOfThreadsTextField.getText()));
        props.setPathToSave(toSaveTextField.getText());

        setInitialProgress();


        Thread serverThread = new Thread(() -> {
            server.start();
        });

        Thread clientThread = new Thread(() -> {
            client.start();
        });

        serverThread.start();
        clientThread.start();

        startCopyButton.setText("Stop copying");
    }

    private void setInitialProgress() {
        totalFilesProgress.set(0);
        filesPercentLabel.setText(((0 / totalFileCount) * 100) + "%");

        totalSizeProgress.set(0);
        filesizePercentLabel.setText(0 / (1024 * 1024) + "/" + totalFileSize / (1024 * 1024) + " MB");
    }


    private void setListeners() {
        totalFilesProgress.addListener((observable, oldValue, newValue) -> {
            filesPercentLabel.setText((int) ((newValue.doubleValue() / totalFileCount) * 100) + "%");
            filesSentProgressBar.setProgress(newValue.doubleValue() / totalFileCount);
        });

        totalSizeProgress.addListener((observable, oldValue, newValue) -> {
            filesizePercentLabel.setText((int) (newValue.doubleValue() / (1024 * 1024)) + "/" + totalFileSize / (1024 * 1024) + " MB");
            bytesSentProgressBar.setProgress(newValue.doubleValue() / totalFileSize);
        });
    }

}
