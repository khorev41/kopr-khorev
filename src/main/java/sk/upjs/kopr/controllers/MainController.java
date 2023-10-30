package sk.upjs.kopr.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import sk.upjs.kopr.copy.client.Client;
import sk.upjs.kopr.copy.client.ClientLauncher;
import sk.upjs.kopr.copy.server.Server;
import sk.upjs.kopr.copy.server.ServerLauncher;
import sk.upjs.kopr.tools.PropertiesManager;

public class MainController {
	
	private Server server = new Server();
	private Client client = new Client();
	
	private final static PropertiesManager props = PropertiesManager.getInstance();

	@FXML
	private Button browseButton;

	@FXML
	private ProgressBar bytesSentProgressBar;

	@FXML
	private TextField directoryTextField;

	@FXML
	private ProgressBar filesSentProgressBar;

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
	void onStartCopyButtonClick(MouseEvent event) {
		props.setNumberOfSockets(Integer.valueOf(numberOfThreadsTextField.getText()));
		props.setPathToSave(toSaveTextField.getText());
		
		Thread serverThread = new Thread(() -> {
            try {
                server.start();  // assuming server has a start method
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Define client thread
        Thread clientThread = new Thread(() -> {
            try {
                client.start();  // assuming client has a start method
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start both threads
        serverThread.start();
        clientThread.start();
		
//		ServerLauncher serverLauncher = new ServerLauncher();
//		serverLauncher.start();
//		
//		ClientLauncher clientLauncher = new ClientLauncher();
//		clientLauncher.start();
	}

	@FXML
	void initialize() {
		directoryTextField.setText(props.getDirectory());
		toSaveTextField.setText(props.getPathToSave());
		numberOfThreadsTextField.setText(Runtime.getRuntime().availableProcessors() + "");
	}

}
