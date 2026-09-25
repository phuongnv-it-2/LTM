package client.ui;

import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX chỉ có 1 Stage (cửa sổ) nhưng ứng dụng có nhiều màn hình
 * (Login, Lobby, Interview Room...). SceneManager giữ tham chiếu duy nhất
 * tới Stage, cho phép các phần code khác (AppController) chuyển màn hình
 * mà không cần biết Stage nằm ở đâu.
 */
public class SceneManager {

    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void switchTo(Scene scene) {
        stage.setScene(scene);
    }

    public Stage getStage() {
        return stage;
    }
}
