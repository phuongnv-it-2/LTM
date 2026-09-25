package server;

import server.room.RoomManager;
import server.session.SessionManager;
import server.tcp.TCPServer;

/**
 * Điểm khởi đầu (entry point) của Server.
 * Chỉ có nhiệm vụ: tạo các manager dùng chung (RoomManager, SessionManager),
 * tạo TCPServer và chạy nó. Không chứa logic nghiệp vụ (đúng yêu cầu
 * "không viết toàn bộ logic vào MainServer.java").
 */
public class ServerApplication {

    public static final int TCP_PORT = 5000;

    public static void main(String[] args) {
        System.out.println("[INFO] Server starting...");

        // Chỉ tạo DUY NHẤT 1 lần ở đây -> mọi TCPClientHandler dùng chung
        // 2 object này, đảm bảo dữ liệu phòng/user nhất quán giữa các client.
        RoomManager roomManager = new RoomManager();
        SessionManager sessionManager = new SessionManager();

        TCPServer tcpServer = new TCPServer(TCP_PORT, roomManager, sessionManager);

        // Chạy TCPServer ngay trên thread main vì run() có vòng lặp accept() vô hạn.
        // Đây là hành vi mong muốn: tiến trình server sẽ sống mãi cho tới khi bị dừng (Ctrl+C).
        tcpServer.run();
    }
}
