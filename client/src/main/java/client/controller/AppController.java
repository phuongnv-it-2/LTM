package client.controller;

import client.tcp.TCPClient;
import client.ui.LobbyView;
import client.ui.LoginView;
import client.ui.RoomView;
import client.ui.SceneManager;
import common.protocol.TcpCommand;
import javafx.application.Platform;
import javafx.stage.Stage;

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

    private final LoginView loginView = new LoginView();
    private final LobbyView lobbyView = new LobbyView();
    private final RoomView roomView = new RoomView();

    private String currentRoomId;
    private volatile boolean connected = false;

    public AppController(Stage stage) {
        this.sceneManager = new SceneManager(stage);
        wireEvents();
    }

    /** Gọi 1 lần khi app khởi động: hiện màn hình Login trước tiên. */
    public void start() {
        sceneManager.switchTo(loginView.getScene());
    }

    /** Gọi khi đóng cửa sổ: ngắt kết nối TCP cho gọn, tránh giữ socket "treo" ở server. */
    public void shutdown() {
        tcpClient.disconnect();
    }

    private void wireEvents() {
        loginView.getLoginButton().setOnAction(e -> onLoginClicked());
        lobbyView.getCreateRoomButton().setOnAction(e -> onCreateRoomClicked());
        lobbyView.getJoinRoomButton().setOnAction(e -> onJoinRoomClicked());
        roomView.getLeaveButton().setOnAction(e -> onLeaveRoomClicked());
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
            case TcpCommand.ERROR -> handleError(parts);
            default -> System.out.println("Client nhận lệnh không xác định: " + line);
        }
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
