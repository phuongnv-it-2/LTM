package common.util;

/** Định dạng số byte thành chuỗi dễ đọc (KB/MB), dùng khi log hoặc hiển thị UI. */
public final class FileSizeFormatter {

    private FileSizeFormatter() {
    }

    public static String format(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }
}
