package client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Màn hình 1: Login.
 * View chỉ chịu trách nhiệm HIỂN THỊ, không tự gửi dữ liệu qua mạng.
 * AppController sẽ đọc giá trị từ các field này khi người dùng bấm nút.
 */
public class LoginView {

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Đăng nhập");
    private final Label statusLabel = new Label("");

    private final Scene scene;

    public LoginView() {
        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        statusLabel.setStyle("-fx-text-fill: red;");

        Label title = new Label("Realtime Interview");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox root = new VBox(12, title, usernameField, passwordField, loginButton, statusLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        scene = new Scene(root, 380, 320);
    }

    public Scene getScene() {
        return scene;
    }

    public TextField getUsernameField() {
        return usernameField;
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }

    public Button getLoginButton() {
        return loginButton;
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }
}
