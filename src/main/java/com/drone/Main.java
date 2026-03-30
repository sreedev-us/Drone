package com.drone;

import com.drone.ui.MainUI;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainUI mainUI = new MainUI(primaryStage);
        mainUI.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
