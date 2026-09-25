package client;

import client.controller.AppController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point của JavaFX client.
 * Từ Phase 2: chỉ tạo AppController và giao toàn bộ việc điều phối UI/mạng
 * cho nó, bản thân class này không còn chứa logic (giống ServerApplication
 * bên server).
 */
public class ClientApplication extends Application {

    private AppController controller;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Realtime Interview - Client");

        controller = new AppController(primaryStage);
        controller.start();

        primaryStage.setOnCloseRequest(e -> controller.shutdown());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
