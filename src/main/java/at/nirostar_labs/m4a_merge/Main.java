package at.nirostar_labs.m4a_merge;

import at.nirostar_labs.m4a_merge.ui.Controller;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {
    final static private Logger[] pin;

    static {
        //Disable loggers
        pin = new Logger[]{Logger.getLogger("org.jaudiotagger")};

        for (Logger l : pin)
            l.setLevel(Level.OFF);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Controller controller = new Controller(primaryStage);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        fxmlLoader.setController(controller);
        primaryStage.setTitle("M4A Merge");
        Parent root = fxmlLoader.load();
        primaryStage.setScene(new Scene(root, 1033, 600));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
