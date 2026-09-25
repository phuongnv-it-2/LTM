package server.tcp;

import common.model.Message;
import common.protocol.TcpCommand;
import server.file.FileTransferManager;
import server.room.Room;
import server.room.RoomManager;
import server.session.ClientSession;
import server.session.SessionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Mỗi TCPClientHandler chạy trên 1 thread riêng (được TCPServer submit vào thread pool),
 * đại diện cho phiên làm việc của 1 client TCP.
 *
 * Từ Phase 2: handler nhận RoomManager + SessionManager qua constructor (dependency
 * injection thủ công) thay vì tự tạo mới -> đảm bảo TẤT CẢ client dùng chung 1
 * RoomManager/SessionManager duy nhất (shared state), chứ không phải mỗi client
 * có 1 bản sao riêng.
 *
 * handleCommand() đóng vai trò "router": tách message theo COMMAND, gọi đúng hàm
 * xử lý. Nếu sau này 1 lệnh có logic quá dài (ví dụ FILE_TRANSFER ở Phase 4),
 * sẽ tách riêng ra class xử lý (FileTransferManager...) thay vì nhồi hết vào đây.
 */
public class TCPClientHandler implements Runnable {

    private final Socket socket;
    private final RoomManager roomManager;
    private final SessionManager sessionManager;

    // Được khởi tạo ngay khi bắt đầu run(), đại diện cho phiên kết nối này.
    private ClientSession session;

    public TCPClientHandler(Socket socket, RoomManager roomManager, SessionManager sessionManager) {
        this.socket = socket;
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            session = new ClientSession(socket, writer);

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(log("Received from " + socket.getRemoteSocketAddress() + ": " + line));
                handleCommand(line);
            }
        } catch (IOException e) {
            System.out.println(log("Connection error with " + socket.getRemoteSocketAddress() + ": " + e.getMessage()));
        } finally {
            cleanup();
        }
    }

    /**
     * Tách 1 dòng message dạng "COMMAND|arg1|arg2..." và định tuyến tới hàm xử lý phù hợp.
     * split(..., -1): giữ lại phần tử rỗng ở cuối, ví dụ "LOGIN|abc|" vẫn tách ra 3 phần
     * (username="abc", password=""), tránh mất dữ liệu khi 1 trường để trống.
     */
    private void handleCommand(String line) {
        String[] parts = line.split(TcpCommand.DELIMITER, -1);
        String command = parts[0];

        switch (command) {
            case TcpCommand.HELLO_SERVER -> session.send(TcpCommand.HELLO_CLIENT);
            case TcpCommand.LOGIN -> handleLogin(parts);
            case TcpCommand.CREATE_ROOM -> handleCreateRoom(parts);
            case TcpCommand.JOIN_ROOM -> handleJoinRoom(parts);
            case TcpCommand.LEAVE_ROOM -> handleLeaveRoom();
            case TcpCommand.CHAT -> handleChat(line);
            case TcpCommand.FILE_START -> handleFileStart(parts);
            case TcpCommand.FILE_CHUNK -> handleFileChunk(parts);
            case TcpCommand.FILE_END -> handleFileEnd(parts);
            default -> session.send(TcpCommand.ERROR + "|UNKNOWN_COMMAND|" + command);
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            session.send(TcpCommand.ERROR + "|INVALID_LOGIN|Thieu username");
            return;
        }
        String username = parts[1];

        // Phase 2 chưa có database nên chưa kiểm tra password thật (mật khẩu chỉ được
        // gửi kèm để đúng định dạng protocol, sẽ dùng thật khi thêm database sau này).
        boolean success = sessionManager.login(username, session);
        if (success) {
            session.send(TcpCommand.LOGIN_SUCCESS + "|" + username);
            System.out.println(log("User logged in: " + username));
        } else {
            session.send(TcpCommand.ERROR + "|USERNAME_TAKEN|Username da duoc su dung");
        }
    }

    private void handleCreateRoom(String[] parts) {
        if (!requireLogin()) return;
        if (parts.length < 2 || parts[1].isBlank()) {
            session.send(TcpCommand.ERROR + "|INVALID_ROOM_NAME|Thieu ten phong");
            return;
        }
        String roomName = parts[1];
        Room room = roomManager.createRoom(roomName, session);

        session.send(TcpCommand.ROOM_CREATED + "|" + room.getRoomId());
        System.out.println(log("Room created: " + room.getRoomId() + " (" + roomName + ") by " + session.getUsername()));
    }

    private void handleJoinRoom(String[] parts) {
        if (!requireLogin()) return;
        if (parts.length < 2 || parts[1].isBlank()) {
            session.send(TcpCommand.ERROR + "|INVALID_ROOM_ID|Thieu roomId");
            return;
        }
        String roomId = parts[1];
        Room room = roomManager.joinRoom(roomId, session);

        if (room == null) {
            session.send(TcpCommand.ERROR + "|ROOM_NOT_FOUND|Khong tim thay phong " + roomId);
            return;
        }

        session.send(TcpCommand.JOIN_SUCCESS + "|" + roomId);
        // Báo cho các participant còn lại trong phòng biết có người mới vào.
        room.broadcast(TcpCommand.SYSTEM_MESSAGE + "|" + session.getUsername() + " da vao phong", session);
        System.out.println(log(session.getUsername() + " joined room " + roomId));
    }

    private void handleLeaveRoom() {
        Room room = session.getCurrentRoom();
        if (room == null) {
            session.send(TcpCommand.ERROR + "|NOT_IN_ROOM|Ban chua o trong phong nao");
            return;
        }
        String roomId = room.getRoomId();
        String username = session.getUsername();

        roomManager.leaveRoom(session);

        session.send(TcpCommand.LEAVE_SUCCESS + "|" + roomId);
        room.broadcast(TcpCommand.SYSTEM_MESSAGE + "|" + username + " da roi phong", session);
        System.out.println(log(username + " left room " + roomId));
    }

    /**
     * Xử lý CHAT|noi_dung.
     * Dùng "line" (dòng gốc) thay vì "parts" đã split sẵn ở handleCommand(), vì
     * handleCommand() split với limit=-1 (không giới hạn số lần tách) - nếu người
     * dùng gõ tin nhắn có chứa ký tự "|", nó sẽ bị cắt vụn thành nhiều phần tử,
     * mất mất nội dung. Ở đây ta tự split lại với limit=2, đảm bảo chỉ tách
     * đúng 1 lần đầu tiên ("CHAT" và toàn bộ phần còn lại là content), giữ
     * nguyên nội dung dù có bao nhiêu ký tự "|" đi nữa.
     */
    private void handleChat(String line) {
        Room room = requireRoom();
        if (room == null) return;

        String[] chatParts = line.split(TcpCommand.DELIMITER, 2);
        if (chatParts.length < 2 || chatParts[1].isBlank()) {
            session.send(TcpCommand.ERROR + "|EMPTY_MESSAGE|Tin nhan khong duoc de trong");
            return;
        }
        String content = chatParts[1];

        // Server tự gắn sender (lấy từ session, không tin client tự khai) và
        // timestamp (lấy giờ server, không tin đồng hồ máy client) -> đảm bảo
        // 2 field quan trọng này không thể bị giả mạo.
        Message message = new Message(room.getRoomId(), session.getUsername(), content, System.currentTimeMillis());

        room.broadcastAll(message.toProtocolLine());
        System.out.println(log("Chat in room " + room.getRoomId() + " - " + session.getUsername() + ": " + content));
    }

    // ===== Phase 4: TCP File Transfer =====
    // Cả 3 lệnh FILE_START/FILE_CHUNK/FILE_END đều cần đúng 2 điều kiện giống
    // nhau: đã login + đang ở trong 1 phòng -> gom logic kiểm tra room vào 1
    // hàm dùng chung (requireRoom()) thay vì lặp lại if ở cả 3 chỗ.

    private void handleFileStart(String[] parts) {
        Room room = requireRoom();
        if (room == null) return;
        if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
            session.send(TcpCommand.ERROR + "|INVALID_FILE_START|Thieu ten file hoac kich thuoc");
            return;
        }
        String fileName = parts[1];
        String fileSize = parts[2];

        FileTransferManager.relayFileStart(room, session, fileName, fileSize);
        System.out.println(log(session.getUsername() + " bat dau gui file '" + fileName
                + "' (" + fileSize + " bytes) vao phong " + room.getRoomId()));
    }

    private void handleFileChunk(String[] parts) {
        Room room = requireRoom();
        if (room == null) return;
        if (parts.length < 4) {
            session.send(TcpCommand.ERROR + "|INVALID_FILE_CHUNK|Du lieu chunk khong hop le");
            return;
        }
        String fileName = parts[1];
        String chunkIndex = parts[2];
        String base64Data = parts[3];

        // Không log ở mức từng chunk (1 file vài MB có thể ra hàng ngàn chunk,
        // log sẽ trôi mất các dòng log quan trọng khác như "Client connected").
        FileTransferManager.relayFileChunk(room, session, fileName, chunkIndex, base64Data);
    }

    private void handleFileEnd(String[] parts) {
        Room room = requireRoom();
        if (room == null) return;
        if (parts.length < 2 || parts[1].isBlank()) {
            session.send(TcpCommand.ERROR + "|INVALID_FILE_END|Thieu ten file");
            return;
        }
        String fileName = parts[1];

        FileTransferManager.relayFileEnd(room, session, fileName);
        System.out.println(log(session.getUsername() + " da gui xong file '" + fileName
                + "' trong phong " + room.getRoomId()));
    }

    /**
     * Kiểm tra session đã LOGIN và đang ở trong phòng nào đó chưa - điều kiện
     * bắt buộc cho cả CHAT lẫn FILE_*. Trả về Room nếu hợp lệ, trả về null (và
     * đã tự gửi ERROR cho client) nếu chưa đủ điều kiện, để hàm gọi chỉ cần
     * viết "if (room == null) return;" mà không cần lặp lại logic kiểm tra.
     */
    private Room requireRoom() {
        if (!requireLogin()) return null;
        Room room = session.getCurrentRoom();
        if (room == null) {
            session.send(TcpCommand.ERROR + "|NOT_IN_ROOM|Ban can vao phong truoc");
            return null;
        }
        return room;
    }

    /** Kiểm tra session đã LOGIN chưa trước khi cho phép các lệnh cần username (CREATE_ROOM, JOIN_ROOM...). */
    private boolean requireLogin() {
        if (session.getUsername() == null) {
            session.send(TcpCommand.ERROR + "|NOT_LOGGED_IN|Ban can dang nhap truoc");
            return false;
        }
        return true;
    }

    /**
     * Dọn dẹp khi client ngắt kết nối (đóng app, mất mạng, Ctrl+C...).
     * Quan trọng: nếu đang ở trong phòng, phải rời phòng giúp họ và báo cho
     * người còn lại trong phòng biết, nếu không phòng sẽ giữ mãi 1 "ghost participant".
     */
    private void cleanup() {
        if (session != null) {
            Room room = session.getCurrentRoom();
            if (room != null) {
                String username = session.getUsername();
                roomManager.leaveRoom(session);
                room.broadcast(TcpCommand.SYSTEM_MESSAGE + "|" + username + " da mat ket noi", session);
            }
            sessionManager.logout(session);
        }
        System.out.println(log("Client disconnected: " + socket.getRemoteSocketAddress()));
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private String log(String message) {
        return "[INFO][" + java.time.LocalTime.now().withNano(0) + "] " + message;
    }
}
