package client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Giao diện Phase 1: chỉ có 1 nút "Connect" và 2 label hiển thị trạng thái.
 * Đây là UI tạm thời để chứng minh TCP hoạt động; từ Phase 2 sẽ tách thành
 * nhiều màn hình (Login, Lobby...) dùng SceneManager để chuyển màn hình.
 */
public class MainView {

    private final Button connectButton = new Button("Connect to Server");
    private final Label statusLabel = new Label("TCP: Disconnected");
    private final Label messageLabel = new Label("Chưa có dữ liệu từ server");

    private final Scene scene;

    public MainView() {
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");

        VBox root = new VBox(15, statusLabel, connectButton, messageLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        this.scene = new Scene(root, 400, 250);
    }

    public Scene getScene() {
        return scene;
    }

    public Button getConnectButton() {
        return connectButton;
    }

    public void setConnected(boolean connected) {
        if (connected) {
            statusLabel.setText("TCP: Connected");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            connectButton.setDisable(true);
        } else {
            statusLabel.setText("TCP: Disconnected");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
            connectButton.setDisable(false);
        }
    }

    public void setLastMessage(String message) {
        messageLabel.setText("Server trả về: " + message);
    }
}
