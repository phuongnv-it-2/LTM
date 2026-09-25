package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * TCPClient bọc lại 1 Socket kết nối tới server.
 *
 * Vì sao cần 1 thread riêng để đọc dữ liệu?
 * JavaFX chỉ có 1 thread để vẽ UI (JavaFX Application Thread). Nếu ta gọi
 * reader.readLine() (lệnh BLOCKING) ngay trên thread đó, toàn bộ giao diện sẽ
 * bị "đứng hình" (không vẽ lại, không phản hồi click) cho tới khi có dữ liệu mới.
 * Do đó việc đọc dữ liệu từ server phải chạy trên 1 thread nền (listenerThread),
 * và mỗi khi có dữ liệu, ta gọi callback để đưa dữ liệu đó về cho UI xử lý.
 */
public class TCPClient {

    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;

    private volatile boolean connected = false;

    public TCPClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Mở kết nối TCP tới server. Đây là lệnh BLOCKING (đợi tới khi bắt tay TCP xong),
     * nên hàm này nên được gọi từ 1 thread nền, không gọi trực tiếp trên JavaFX thread.
     *
     * @param onMessageReceived callback được gọi mỗi khi nhận được 1 dòng dữ liệu từ server
     * @param onDisconnected    callback được gọi khi mất kết nối
     */
    public void connect(Consumer<String> onMessageReceived, Runnable onDisconnected) throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        // autoFlush = true: mỗi lần gọi println() dữ liệu được đẩy đi ngay, không bị giữ trong buffer
        writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        connected = true;

        listenerThread = new Thread(() -> listenLoop(onMessageReceived, onDisconnected));
        // daemon = true: thread này sẽ tự động bị JVM tắt khi đóng ứng dụng,
        // không cần tự viết code để "giết" thread khi thoát app.
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop(Consumer<String> onMessageReceived, Runnable onDisconnected) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                onMessageReceived.accept(line);
            }
        } catch (IOException e) {
            // Xảy ra khi socket bị đóng đột ngột (server tắt, mất mạng...)
        } finally {
            connected = false;
            onDisconnected.run();
        }
    }

    public void send(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
