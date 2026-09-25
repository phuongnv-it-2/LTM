package client.file;

import client.tcp.TCPClient;
import common.protocol.FileTransferConstants;
import common.protocol.TcpCommand;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FileTransferManager phía client lo 2 việc ngược nhau:
 * 1) GỬI: đọc 1 file từ đĩa theo từng chunk nhỏ, encode base64, gửi qua TCP.
 * 2) NHẬN: nhận từng chunk base64 từ server, decode, ghi nối tiếp xuống 1 file
 *    trong thư mục downloads/.
 *
 * Vì sao dùng BufferedInputStream/BufferedOutputStream thay vì đọc/ghi thẳng?
 * Buffered*Stream gom nhiều lần đọc/ghi nhỏ (từng byte) thành ít lần thao tác
 * đĩa lớn hơn -> nhanh hơn nhiều so với FileInputStream/FileOutputStream trần.
 * Quan trọng hơn: ta luôn CHỈ giữ 1 chunk (4KB) trong RAM tại 1 thời điểm, dù
 * file gốc là 5KB hay 500MB cũng dùng lượng RAM y hệt nhau (đây chính là ý
 * nghĩa của "streaming" - không bao giờ load toàn bộ file vào bộ nhớ).
 *
 * Vì sao có 1 ExecutorService riêng (ioExecutor) chỉ để ghi file khi NHẬN?
 * handleFileChunk() được gọi từ AppController ngay trên JavaFX Application
 * Thread (vì TCPClient gọi callback qua Platform.runLater). Nếu ghi đĩa trực
 * tiếp tại đó, mỗi lần nhận 1 chunk sẽ làm UI khựng nhẹ (ghi đĩa là thao tác
 * chậm so với vẽ UI). Đưa việc ghi đĩa qua 1 thread riêng giúp UI luôn mượt.
 * Dùng ĐÚNG 1 thread (newSingleThreadExecutor, không phải thread pool nhiều
 * luồng) để đảm bảo các chunk được ghi ĐÚNG THỨ TỰ đã nhận - quan trọng vì
 * ghi sai thứ tự sẽ làm hỏng file, kể cả khi TCP đã đảm bảo thứ tự ĐẾN.
 */
public class FileTransferManager {

    /** Được AppController implement để cập nhật UI (progress bar, log...). */
    public interface Listener {
        void onProgress(String label, int percent);

        void onCompleted(String sender, String fileName, Path savedPath);

        void onError(String message);
    }

    private final TCPClient tcpClient;
    private final Path downloadDir;
    private final Listener listener;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // Theo dõi các lượt NHẬN file đang dang dở, key = "sender|fileName".
    // Phase 4 demo với vài client nên chấp nhận đơn giản: không xử lý 2 file
    // trùng tên từ cùng 1 người gửi liên tiếp nhau (ghi đè), việc chống trùng
    // tên phức tạp hơn (thêm timestamp...) để dành khi thực sự cần.
    private final Map<String, BufferedOutputStream> openDownloads = new ConcurrentHashMap<>();
    private final Map<String, Path> downloadPaths = new ConcurrentHashMap<>();
    private final Map<String, Long> expectedSizes = new ConcurrentHashMap<>();
    private final Map<String, Long> receivedBytes = new ConcurrentHashMap<>();

    public FileTransferManager(TCPClient tcpClient, Path downloadDir, Listener listener) {
        this.tcpClient = tcpClient;
        this.downloadDir = downloadDir;
        this.listener = listener;
    }

    // ================= GỬI FILE =================

    /**
     * Gửi 1 file cho cả phòng. Hàm này BLOCKING (đọc file + gửi qua mạng tuần
     * tự), nên PHẢI được gọi từ 1 thread nền (giống connectThenSend ở
     * AppController), không gọi trực tiếp trên JavaFX Application Thread.
     */
    public void sendFile(File file) throws IOException {
        String fileName = file.getName();
        long fileSize = file.length();

        tcpClient.send(TcpCommand.FILE_START + "|" + fileName + "|" + fileSize);

        byte[] buffer = new byte[FileTransferConstants.CHUNK_SIZE];
        long sentBytes = 0;
        int chunkIndex = 0;

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] chunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                String base64 = Base64.getEncoder().encodeToString(chunk);

                tcpClient.send(TcpCommand.FILE_CHUNK + "|" + fileName + "|" + chunkIndex + "|" + base64);

                sentBytes += bytesRead;
                chunkIndex++;
                int percent = (fileSize == 0) ? 100 : (int) Math.min(100, sentBytes * 100 / fileSize);
                listener.onProgress("Đang gửi " + fileName, percent);
            }
        }

        tcpClient.send(TcpCommand.FILE_END + "|" + fileName);
    }

    // ================= NHẬN FILE =================
    // 3 hàm dưới đây được gọi từ AppController khi nhận FILE_START/FILE_CHUNK/
    // FILE_END do SERVER forward xuống, đã split sẵn theo protocol:
    //   FILE_START: [FILE_START, roomId, sender, fileName, fileSize]
    //   FILE_CHUNK: [FILE_CHUNK, roomId, sender, fileName, chunkIndex, base64Data]
    //   FILE_END:   [FILE_END,   roomId, sender, fileName]

    public void handleFileStart(String[] parts) {
        String sender = parts[2];
        String fileName = parts[3];
        long fileSize = Long.parseLong(parts[4]);
        String key = key(sender, fileName);

        ioExecutor.submit(() -> {
            try {
                Files.createDirectories(downloadDir);
                Path target = downloadDir.resolve(fileName);
                openDownloads.put(key, new BufferedOutputStream(new FileOutputStream(target.toFile())));
                downloadPaths.put(key, target);
                expectedSizes.put(key, fileSize);
                receivedBytes.put(key, 0L);
                listener.onProgress("Đang nhận " + fileName + " từ " + sender, 0);
            } catch (IOException e) {
                listener.onError("Không tạo được file để nhận '" + fileName + "': " + e.getMessage());
            }
        });
    }

    public void handleFileChunk(String[] parts) {
        String sender = parts[2];
        String fileName = parts[3];
        String base64Data = parts[5];
        String key = key(sender, fileName);

        ioExecutor.submit(() -> {
            BufferedOutputStream out = openDownloads.get(key);
            if (out == null) {
                // Chưa từng nhận FILE_START tương ứng (ví dụ do lỗi thứ tự
                // hiếm gặp) -> bỏ qua chunk này thay vì làm crash cả app.
                return;
            }
            try {
                byte[] data = Base64.getDecoder().decode(base64Data);
                out.write(data);

                long total = receivedBytes.merge(key, (long) data.length, Long::sum);
                long expected = expectedSizes.getOrDefault(key, 1L);
                int percent = (expected <= 0) ? 100 : (int) Math.min(100, total * 100 / expected);
                listener.onProgress("Đang nhận " + fileName + " từ " + sender, percent);
            } catch (IOException e) {
                listener.onError("Lỗi khi ghi file '" + fileName + "': " + e.getMessage());
            }
        });
    }

    public void handleFileEnd(String[] parts) {
        String sender = parts[2];
        String fileName = parts[3];
        String key = key(sender, fileName);

        ioExecutor.submit(() -> {
            BufferedOutputStream out = openDownloads.remove(key);
            Path savedPath = downloadPaths.remove(key);
            expectedSizes.remove(key);
            receivedBytes.remove(key);

            if (out == null) return;
            try {
                out.close();
                listener.onCompleted(sender, fileName, savedPath);
            } catch (IOException e) {
                listener.onError("Lỗi khi đóng file '" + fileName + "': " + e.getMessage());
            }
        });
    }

    private String key(String sender, String fileName) {
        return sender + "|" + fileName;
    }

    /** Gọi khi đóng ứng dụng, tránh giữ thread ioExecutor sống mãi. */
    public void shutdown() {
        ioExecutor.shutdownNow();
    }
}
