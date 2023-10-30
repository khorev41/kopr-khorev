package sk.upjs.kopr;
import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import sk.upjs.kopr.controllers.MainController;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
    	FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main.fxml"));
    	MainController controller = new MainController();
		fxmlLoader.setController(controller);
		Parent parent = fxmlLoader.load();
		Scene scene = new Scene(parent);
		stage.setMinWidth(380);
		stage.setMinHeight(300);
		stage.setScene(scene);
		stage.setTitle("Copy directory");
		stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}