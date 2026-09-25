package client.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * Màn hình 3: Interview Room (bản tối giản của Phase 2).
 *
 * Phase 2 chỉ cần chứng minh vào/rời phòng hoạt động đúng, nên màn hình này
 * chỉ có: tên phòng + khung log hiển thị SYSTEM_MESSAGE (ai vào/rời phòng)
 * + nút Rời phòng. Khung Chat (Phase 3), Send File (Phase 4), Video (Phase 8)
 * sẽ được thêm dần vào chính file này ở các phase sau, không tạo file mới.
 */
public class RoomView {

    private final Label roomLabel = new Label();
    private final TextArea logArea = new TextArea();
    private final Button leaveButton = new Button("Rời phòng");

    private final Scene scene;

    public RoomView() {
        roomLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setWrapText(true);

        VBox root = new VBox(12, roomLabel, logArea, leaveButton);
        root.setPadding(new Insets(20));

        scene = new Scene(root, 520, 420);
    }

    public Scene getScene() {
        return scene;
    }

    public void setRoomId(String roomId) {
        roomLabel.setText("Phòng phỏng vấn: " + roomId);
    }

    public void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    public void clearLog() {
        logArea.clear();
    }

    public Button getLeaveButton() {
        return leaveButton;
    }
}
