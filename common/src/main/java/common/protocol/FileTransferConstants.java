package common.protocol;

import java.util.Set;

/**
 * Các hằng số liên quan tới file transfer, dùng chung cho cả người gửi (client
 * đọc file, chia chunk) và server (chỉ đọc phần mở rộng để validate).
 */
public final class FileTransferConstants {

    /**
     * Kích thước 1 chunk ĐỌC TỪ FILE GỐC (trước khi encode base64), tính bằng byte.
     * 4KB là con số vừa phải cho đồ án: đủ nhỏ để UI cập nhật % tiến độ mượt mà
     * (không phải chờ lâu mới thấy chunk đầu tiên), đủ lớn để không tốn quá
     * nhiều lần gửi/nhận riêng lẻ khi file vài MB (ví dụ file 4MB chỉ cần ~1000
     * chunk thay vì gửi từng byte một).
     */
    public static final int CHUNK_SIZE = 4096;

    /** Các định dạng file được phép gửi, theo đúng yêu cầu đề bài. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "txt", "jpg", "jpeg", "png"
    );

    private FileTransferConstants() {
    }

    /** Lấy phần mở rộng (không kèm dấu chấm, viết thường) từ tên file, ví dụ "CV.PDF" -> "pdf". */
    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    public static boolean isAllowed(String fileName) {
        return ALLOWED_EXTENSIONS.contains(extensionOf(fileName));
    }
}
