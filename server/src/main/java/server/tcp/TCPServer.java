package server.tcp;

import server.room.RoomManager;
import server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCPServer chịu trách nhiệm:
 * 1. Mở 1 ServerSocket lắng nghe ở port cố định (5000).
 * 2. Lặp vô hạn để accept() các kết nối TCP mới từ client.
 * 3. Mỗi khi có 1 client kết nối, giao Socket đó cho 1 thread riêng (TCPClientHandler)
 *    xử lý, để không bị block khi đang phục vụ client khác.
 *
 * Vì sao cần ServerSocket riêng 1 class?
 * - Tách trách nhiệm "lắng nghe kết nối" (TCPServer) ra khỏi "xử lý logic từng client"
 *   (TCPClientHandler). Sau này thêm Room/Login/Chat chỉ cần sửa TCPClientHandler,
 *   không đụng vào phần accept loop.
 */
public class TCPServer implements Runnable {

    private final int port;
    private final RoomManager roomManager;
    private final SessionManager sessionManager;
    private ServerSocket serverSocket;

    // ExecutorService = thread pool. Thay vì tự tay "new Thread()" cho từng client
    // (tốn tài nguyên nếu có hàng trăm client), ta dùng pool và tái sử dụng thread.
    // newCachedThreadPool: tạo thread mới khi cần, thu hồi thread rảnh -> phù hợp
    // với số lượng client không cố định của ứng dụng phỏng vấn (vài chục người).
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    private volatile boolean running = false;

    public TCPServer(int port, RoomManager roomManager, SessionManager sessionManager) {
        this.port = port;
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public void run() {
        try {
            // ServerSocket(port): mở 1 cổng TCP, hệ điều hành sẽ chuyển mọi kết nối
            // TCP gửi tới port này cho tiến trình Java của ta.
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println(log("TCP listening on " + port));

            while (running) {
                // accept() là lệnh BLOCKING: thread này sẽ dừng lại ở đây cho tới khi
                // có 1 client mới gọi connect() tới. Đây là lý do bắt buộc phải chạy
                // TCPServer trên 1 thread riêng (không phải thread chính của JavaFX/main),
                // nếu không cả server sẽ bị "đứng" chờ ở accept().
                Socket clientSocket = serverSocket.accept();
                System.out.println(log("Client connected: " + clientSocket.getRemoteSocketAddress()));

                // Giao socket này cho 1 thread khác xử lý, rồi quay lại accept() ngay
                // để tiếp tục nhận client tiếp theo -> đây chính là cách server
                // phục vụ NHIỀU client đồng thời.
                clientThreadPool.submit(new TCPClientHandler(clientSocket, roomManager, sessionManager));
            }
        } catch (IOException e) {
            if (running) {
                System.out.println(log("TCP server error: " + e.getMessage()));
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println(log("Error while stopping server: " + e.getMessage()));
        }
        clientThreadPool.shutdownNow();
    }

    private String log(String message) {
        return "[INFO][" + java.time.LocalTime.now().withNano(0) + "] " + message;
    }
}
