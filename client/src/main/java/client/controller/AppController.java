package client.controller;

import client.file.FileTransferManager;
import client.tcp.TCPClient;
import client.ui.LobbyView;
import client.ui.LoginView;
import client.ui.RoomView;
import client.ui.SceneManager;
import common.model.Message;
import common.protocol.FileTransferConstants;
import common.protocol.TcpCommand;
import common.util.FileSizeFormatter;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AppController là "bộ não" của client: nơi duy nhất vừa cầm TCPClient
 * (kết nối mạng) vừa cầm SceneManager (điều khiển UI). Các View (LoginView,
 * LobbyView, RoomView) không biết gì về mạng - chúng chỉ khai báo nút bấm,
 * còn AppController mới là nơi quyết định bấm nút thì gửi gì, và nhận được
 * gì từ server thì cập nhật màn hình nào.
 *
 * Vì sao gom hết vào 1 class thay vì tách LoginController/LobbyController riêng?
 * Ở Phase 2, logic mỗi màn hình chỉ vài dòng. Tách sớm sẽ sinh ra nhiều class
 * chỉ để gọi chéo qua lại. Khi handleServerMessage() hoặc số lượng màn hình
 * phình to ở các phase sau (chat, file, video), sẽ tách theo từng nhóm chức năng.
 */
public class AppController {

    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 5000;

    private final SceneManager sceneManager;
    private final TCPClient tcpClient = new TCPClient(SERVER_HOST, SERVER_PORT);
    private final FileTransferManager fileTransferManager;

    private final LoginView loginView = new LoginView();
    private final LobbyView lobbyView = new LobbyView();
    private final RoomView roomView = new RoomView();

    private String currentRoomId;
    private volatile boolean connected = false;

    public AppController(Stage stage) {
        this.sceneManager = new SceneManager(stage);
        // Thư mục lưu file nhận được, đặt ngay cạnh nơi chạy app cho dễ tìm khi demo.
        Path downloadDir = Paths.get("downloads");
        this.fileTransferManager = new FileTransferManager(tcpClient, downloadDir, new FileTransferManager.Listener() {
            @Override
            public void onProgress(String label, int percent) {
                // onProgress có thể được gọi từ thread gửi file (sendFile chạy
                // nền) hoặc từ ioExecutor (khi nhận file) - cả 2 đều KHÔNG phải
                // JavaFX Application Thread, nên bắt buộc bọc Platform.runLater
                // trước khi động vào bất kỳ control nào của UI.
                Platform.runLater(() -> roomView.updateTransferProgress(label, percent));
            }

            @Override
            public void onCompleted(String sender, String fileName, Path savedPath) {
                Platform.runLater(() -> {
                    roomView.hideTransferProgress();
                    roomView.appendLog("[File] " + sender + " đã gửi " + fileName + " -> đã lưu tại " + savedPath);
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    roomView.hideTransferProgress();
                    roomView.appendLog("[Lỗi file] " + message);
                });
            }
        });
        wireEvents();
    }

    /** Gọi 1 lần khi app khởi động: hiện màn hình Login trước tiên. */
    public void start() {
        sceneManager.switchTo(loginView.getScene());
    }

    /** Gọi khi đóng cửa sổ: ngắt kết nối TCP cho gọn, tránh giữ socket "treo" ở server. */
    public void shutdown() {
        tcpClient.disconnect();
        fileTransferManager.shutdown();
    }

    private void wireEvents() {
        loginView.getLoginButton().setOnAction(e -> onLoginClicked());
        lobbyView.getCreateRoomButton().setOnAction(e -> onCreateRoomClicked());
        lobbyView.getJoinRoomButton().setOnAction(e -> onJoinRoomClicked());
        roomView.getLeaveButton().setOnAction(e -> onLeaveRoomClicked());
        roomView.getSendButton().setOnAction(e -> onSendChatClicked());
        roomView.getSendFileButton().setOnAction(e -> onSendFileClicked());
        // TextField.setOnAction() tự kích hoạt khi người dùng nhấn Enter trong ô
        // input - cho phép gửi tin nhắn bằng Enter, không bắt buộc phải bấm nút Gửi.
        roomView.getChatInput().setOnAction(e -> onSendChatClicked());
    }

    private void onLoginClicked() {
        String username = loginView.getUsernameField().getText().trim();
        String password = loginView.getPasswordField().getText();

        if (username.isEmpty()) {
            loginView.setStatus("Vui lòng nhập username");
            return;
        }

        if (!connected) {
            connectThenSend(TcpCommand.LOGIN + "|" + username + "|" + password);
        } else {
            tcpClient.send(TcpCommand.LOGIN + "|" + username + "|" + password);
        }
    }

    /**
     * Mở kết nối TCP (blocking) trên 1 thread nền, sau đó gửi luôn message đầu tiên.
     * Chỉ dùng cho lần LOGIN đầu tiên vì lúc đó chưa có kết nối nào.
     */
    private void connectThenSend(String firstMessage) {
        Thread connectThread = new Thread(() -> {
            try {
                tcpClient.connect(
                        line -> Platform.runLater(() -> handleServerMessage(line)),
                        () -> Platform.runLater(this::handleDisconnected)
                );
                connected = true;
                tcpClient.send(firstMessage);
            } catch (Exception ex) {
                Platform.runLater(() -> loginView.setStatus("Không kết nối được server: " + ex.getMessage()));
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void onCreateRoomClicked() {
        String roomName = lobbyView.getRoomNameField().getText().trim();
        if (roomName.isEmpty()) {
            lobbyView.setStatus("Vui lòng nhập tên phòng");
            return;
        }
        tcpClient.send(TcpCommand.CREATE_ROOM + "|" + roomName);
    }

    private void onJoinRoomClicked() {
        String roomId = lobbyView.getRoomIdField().getText().trim();
        if (roomId.isEmpty()) {
            lobbyView.setStatus("Vui lòng nhập Room ID");
            return;
        }
        tcpClient.send(TcpCommand.JOIN_ROOM + "|" + roomId);
    }

    private void onLeaveRoomClicked() {
        if (currentRoomId != null) {
            tcpClient.send(TcpCommand.LEAVE_ROOM + "|" + currentRoomId);
        }
    }

    private void onSendChatClicked() {
        String content = roomView.getChatInput().getText().trim();
        if (content.isEmpty()) {
            return;
        }
        // Không tự vẽ tin nhắn của mình lên UI ngay ở đây. Server sẽ broadcast
        // ngược lại (kể cả cho chính người gửi) qua handleServerMessage() ->
        // đảm bảo UI luôn hiển thị đúng 1 nguồn dữ liệu duy nhất từ server.
        tcpClient.send(TcpCommand.CHAT + "|" + content);
        roomView.clearChatInput();
    }

    private void onSendFileClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file để gửi");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Tài liệu / Ảnh (pdf, docx, txt, jpg, png)",
                "*.pdf", "*.docx", "*.txt", "*.jpg", "*.jpeg", "*.png"));

        File file = chooser.showOpenDialog(sceneManager.getStage());
        if (file == null) {
            return; // người dùng bấm Cancel
        }
        // Kiểm tra lại lần nữa (không chỉ dựa vào bộ lọc của FileChooser, vì
        // người dùng vẫn có thể gõ tay tên file khác định dạng trong 1 số hệ
        // điều hành) - validate 2 lớp cho chắc, giống nguyên tắc "không tin
        // dữ liệu đầu vào" đã áp dụng ở phía server.
        if (!FileTransferConstants.isAllowed(file.getName())) {
            roomView.appendLog("[Lỗi file] Định dạng không được hỗ trợ: " + file.getName());
            return;
        }

        roomView.appendLog("Bắt đầu gửi " + file.getName() + " (" + FileSizeFormatter.format(file.length()) + ")");

        // sendFile() đọc file + gửi qua mạng tuần tự (BLOCKING), nên phải chạy
        // trên thread nền - giống hệt lý do connectThenSend() không chạy trực
        // tiếp trên JavaFX Application Thread.
        Thread sendThread = new Thread(() -> {
            try {
                fileTransferManager.sendFile(file);
                Platform.runLater(() -> {
                    roomView.hideTransferProgress();
                    roomView.appendLog("Đã gửi xong " + file.getName());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    roomView.hideTransferProgress();
                    roomView.appendLog("[Lỗi file] Gửi thất bại: " + ex.getMessage());
                });
            }
        });
        sendThread.setDaemon(true);
        sendThread.start();
    }

    /**
     * TOÀN BỘ message từ server đều đi qua đây. Được gọi thông qua Platform.runLater
     * (xem connectThenSend), nên đang chạy trên JavaFX Application Thread -> được phép
     * sửa UI trực tiếp, không cần bọc thêm runLater bên trong hàm này.
     */
    private void handleServerMessage(String line) {
        String[] parts = line.split(TcpCommand.DELIMITER, -1);
        String command = parts[0];

        switch (command) {
            case TcpCommand.LOGIN_SUCCESS -> {
                lobbyView.setUsername(parts[1]);
                lobbyView.setStatus("");
                sceneManager.switchTo(lobbyView.getScene());
            }
            case TcpCommand.ROOM_CREATED -> {
                currentRoomId = parts[1];
                roomView.clearLog();
                roomView.setRoomId(currentRoomId);
                roomView.appendLog("Bạn đã tạo phòng " + currentRoomId);
                sceneManager.switchTo(roomView.getScene());
            }
            case TcpCommand.JOIN_SUCCESS -> {
                currentRoomId = parts[1];
                roomView.clearLog();
                roomView.setRoomId(currentRoomId);
                roomView.appendLog("Bạn đã vào phòng " + currentRoomId);
                sceneManager.switchTo(roomView.getScene());
            }
            case TcpCommand.LEAVE_SUCCESS -> {
                currentRoomId = null;
                lobbyView.setStatus("");
                sceneManager.switchTo(lobbyView.getScene());
            }
            case TcpCommand.SYSTEM_MESSAGE -> roomView.appendLog("[Hệ thống] " + parts[1]);
            case TcpCommand.CHAT -> handleChatReceived(line);
            case TcpCommand.FILE_START -> fileTransferManager.handleFileStart(parts);
            case TcpCommand.FILE_CHUNK -> fileTransferManager.handleFileChunk(parts);
            case TcpCommand.FILE_END -> fileTransferManager.handleFileEnd(parts);
            case TcpCommand.ERROR -> handleError(parts);
            default -> System.out.println("Client nhận lệnh không xác định: " + line);
        }
    }

    /**
     * Nhận CHAT|roomId|sender|timestamp|content từ server.
     * Tự split lại "line" với limit=5 (không dùng "parts" đã split sẵn ở trên
     * với limit=-1), vì lý do giống hệt phía server: nếu content chứa ký tự
     * "|", split limit=-1 sẽ cắt vụn content thành nhiều phần tử dư thừa.
     */
    private void handleChatReceived(String line) {
        String[] chatParts = line.split(TcpCommand.DELIMITER, 5);
        Message message = Message.fromBroadcastParts(chatParts);
        roomView.appendLog("[" + message.getFormattedTime() + "] " + message.getSender() + ": " + message.getContent());
    }

    /** ERROR|code|message -> hiển thị message ở màn hình đang mở (login hoặc lobby). */
    private void handleError(String[] parts) {
        String message = parts.length > 2 ? parts[2] : "Lỗi không xác định";
        loginView.setStatus(message);
        lobbyView.setStatus(message);
    }

    private void handleDisconnected() {
        connected = false;
        loginView.setStatus("Mất kết nối tới server");
        sceneManager.switchTo(loginView.getScene());
    }
}
