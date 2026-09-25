package client.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Màn hình 3: Interview Room.
 *
 * Phase 2: tên phòng + khung log SYSTEM_MESSAGE + nút Rời phòng.
 * Phase 3: thêm khung Chat (ô nhập + nút Gửi), dùng chung logArea để hiển thị
 * cả chat lẫn system message theo đúng thứ tự thời gian thực tế.
 * Phase 4: thêm nút Gửi File + progress bar hiển thị tiến độ gửi/nhận.
 * Video (Phase 8) sẽ tiếp tục được thêm dần vào đây.
 */
public class RoomView {

    private final Label roomLabel = new Label();
    private final TextArea logArea = new TextArea();
    private final TextField chatInput = new TextField();
    private final Button sendButton = new Button("Gửi");
    private final Button sendFileButton = new Button("Gửi File");
    private final ProgressBar transferProgress = new ProgressBar(0);
    private final Label transferLabel = new Label();
    private final Button leaveButton = new Button("Rời phòng");

    private final Scene scene;

    public RoomView() {
        roomLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setWrapText(true);

        chatInput.setPromptText("Nhập tin nhắn...");
        // Cho phép ô input chiếm hết chỗ trống còn lại trong hàng, chỉ chừa
        // đúng phần cần thiết cho nút Gửi bên cạnh.
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        HBox chatRow = new HBox(8, chatInput, sendButton, sendFileButton);

        transferProgress.setPrefWidth(Double.MAX_VALUE);
        transferProgress.setVisible(false);
        transferLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        VBox transferBox = new VBox(2, transferLabel, transferProgress);

        VBox root = new VBox(12, roomLabel, logArea, chatRow, transferBox, leaveButton);
        root.setPadding(new Insets(20));

        scene = new Scene(root, 520, 500);
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

    public TextField getChatInput() {
        return chatInput;
    }

    public Button getSendButton() {
        return sendButton;
    }

    /** Xoá nội dung ô nhập sau khi đã gửi thành công, sẵn sàng cho tin nhắn tiếp theo. */
    public void clearChatInput() {
        chatInput.clear();
    }

    public Button getSendFileButton() {
        return sendFileButton;
    }

    /** Cập nhật thanh tiến độ gửi/nhận file. percent < 0 nghĩa là ẩn thanh (không có transfer nào đang chạy). */
    public void updateTransferProgress(String label, int percent) {
        transferProgress.setVisible(true);
        transferLabel.setText(label + " (" + percent + "%)");
        transferProgress.setProgress(percent / 100.0);
    }

    public void hideTransferProgress() {
        transferProgress.setVisible(false);
        transferLabel.setText("");
    }
}
