package server.session;

import server.room.Room;

import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientSession đại diện cho 1 kết nối TCP đang sống của 1 client.
 * Khác với "User" (chỉ là thông tin tài khoản), ClientSession còn gắn với
 * Socket/PrintWriter cụ thể để server biết gửi dữ liệu đi đâu, và biết
 * người này hiện đang ở phòng nào (currentRoom).
 *
 * Vì sao không tách riêng 1 class "User"?
 * Ở Phase 2 chưa có database, "tài khoản" và "phiên kết nối" là một -
 * hễ mất kết nối là coi như logout. Tách thêm User lúc này sẽ tạo ra
 * 2 object luôn phải đồng bộ với nhau một cách không cần thiết. Nếu sau
 * này thêm database (lưu lịch sử, nhiều thiết bị...), lúc đó tách User
 * ra khỏi ClientSession mới thực sự có ý nghĩa.
 */
public class ClientSession {

    private final Socket socket;
    private final PrintWriter writer;

    // null nghĩa là chưa đăng nhập
    private volatile String username;

    // null nghĩa là chưa ở trong phòng nào
    private volatile Room currentRoom;

    public ClientSession(Socket socket, PrintWriter writer) {
        this.socket = socket;
        this.writer = writer;
    }

    /** Gửi 1 dòng dữ liệu (1 message theo protocol) về cho client này. */
    public void send(String message) {
        writer.println(message);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(Room currentRoom) {
        this.currentRoom = currentRoom;
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
