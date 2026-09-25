package common.model;

import common.protocol.TcpCommand;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Message đóng gói 1 tin nhắn chat: ai gửi, ở phòng nào, nội dung gì, lúc nào.
 *
 * Vì sao đặt trong module common (dùng chung server + client) thay vì để mỗi
 * bên tự parse String bằng tay?
 * - Server tạo Message lúc nhận CHAT từ client (gắn sender + timestamp).
 * - Client tạo lại Message lúc nhận broadcast từ server (để hiển thị UI).
 * Cả 2 bên cùng cần đúng 1 định dạng field, nên gom logic parse/format vào
 * đúng 1 chỗ (ở đây), tránh trường hợp server ghi "timestamp|content" còn
 * client lỡ tay đọc "content|timestamp" (sai thứ tự sẽ crash rất khó tìm lỗi).
 *
 * timestamp lưu dạng epoch millis (số nguyên, System.currentTimeMillis()) vì
 * gửi qua text protocol dễ hơn LocalTime, và chỉ format ra giờ:phút:giây khi
 * cần hiển thị (getFormattedTime()).
 */
public class Message {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String roomId;
    private final String sender;
    private final String content;
    private final long timestamp;

    public Message(String roomId, String sender, String content, long timestamp) {
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }

    /**
     * Dựng lại Message từ dòng dữ liệu server broadcast xuống, đã được split
     * với limit=5: [CHAT, roomId, sender, timestamp, content].
     * limit=5 (không phải -1) rất quan trọng: nếu nội dung chat của người dùng
     * chứa ký tự "|", ta KHÔNG muốn nó bị tách tiếp thành field 6, 7... mà phải
     * giữ nguyên trong content (field cuối cùng).
     */
    public static Message fromBroadcastParts(String[] parts) {
        String roomId = parts[1];
        String sender = parts[2];
        long timestamp = Long.parseLong(parts[3]);
        String content = parts[4];
        return new Message(roomId, sender, content, timestamp);
    }

    /** Chuyển thành 1 dòng theo protocol để server gửi broadcast qua TCP. */
    public String toProtocolLine() {
        return TcpCommand.CHAT + "|" + roomId + "|" + sender + "|" + timestamp + "|" + content;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Định dạng timestamp theo giờ hệ thống, dùng để hiển thị trên UI/log, ví dụ "14:05:32". */
    public String getFormattedTime() {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FORMAT);
    }
}
