package client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Màn hình 2: Lobby.
 * Hiển thị sau khi LOGIN_SUCCESS. Cho phép: tạo phòng mới (CREATE_ROOM)
 * hoặc vào phòng đã có sẵn bằng roomId (JOIN_ROOM).
 */
public class LobbyView {

    private final Label welcomeLabel = new Label();
    private final TextField roomNameField = new TextField();
    private final Button createRoomButton = new Button("Tạo phòng");
    private final TextField roomIdField = new TextField();
    private final Button joinRoomButton = new Button("Vào phòng");
    private final Label statusLabel = new Label("");

    private final Scene scene;

    public LobbyView() {
        welcomeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        roomNameField.setPromptText("Tên phòng mới (VD: Phỏng vấn Java Dev)");
        roomIdField.setPromptText("Nhập Room ID (VD: ROOM001)");
        statusLabel.setStyle("-fx-text-fill: red;");

        VBox root = new VBox(12,
                welcomeLabel,
                new Separator(),
                new Label("Tạo phòng phỏng vấn mới"),
                roomNameField,
                createRoomButton,
                new Separator(),
                new Label("Vào phòng đã có"),
                roomIdField,
                joinRoomButton,
                statusLabel
        );
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        scene = new Scene(root, 420, 460);
    }

    public Scene getScene() {
        return scene;
    }

    public void setUsername(String username) {
        welcomeLabel.setText("Xin chào, " + username);
    }

    public TextField getRoomNameField() {
        return roomNameField;
    }

    public Button getCreateRoomButton() {
        return createRoomButton;
    }

    public TextField getRoomIdField() {
        return roomIdField;
    }

    public Button getJoinRoomButton() {
        return joinRoomButton;
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }
}
