package sk.upjs.kopr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import sk.upjs.kopr.controllers.MainController;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
    	FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main.fxml"));
    	MainController controller = new MainController();
		fxmlLoader.setController(controller);
		Parent parent = fxmlLoader.load();
		Scene scene = new Scene(parent);

		stage.setOnCloseRequest(e -> Platform.exit());

		stage.setMinWidth(380);
		stage.setMinHeight(300);
		stage.setScene(scene);
		stage.setTitle("Multi-thread copy");
		stage.show();
    }


    public static void main(String[] args) {
        launch();
    }

}