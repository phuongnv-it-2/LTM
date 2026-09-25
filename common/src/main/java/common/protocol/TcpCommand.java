package common.protocol;

/**
 * Các hằng số lệnh (command) dùng trong TCP text-based protocol.
 * Client và Server đều import class này để không bị gõ sai chuỗi lệnh.
 *
 * Phase 1 chỉ cần 2 lệnh: HELLO_SERVER (client gửi) và HELLO_CLIENT (server trả lời).
 * Các lệnh khác (LOGIN, CHAT, FILE_START...) sẽ được thêm dần ở các phase sau.
 */
public final class TcpCommand {

    public static final String HELLO_SERVER = "HELLO_SERVER";
    public static final String HELLO_CLIENT = "HELLO_CLIENT";

    // ===== Phase 2: Login + Room Management =====
    public static final String LOGIN = "LOGIN";                 // LOGIN|username|password
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS"; // LOGIN_SUCCESS|username

    public static final String CREATE_ROOM = "CREATE_ROOM";     // CREATE_ROOM|roomName
    public static final String ROOM_CREATED = "ROOM_CREATED";   // ROOM_CREATED|roomId

    public static final String JOIN_ROOM = "JOIN_ROOM";         // JOIN_ROOM|roomId
    public static final String JOIN_SUCCESS = "JOIN_SUCCESS";   // JOIN_SUCCESS|roomId

    public static final String LEAVE_ROOM = "LEAVE_ROOM";       // LEAVE_ROOM|roomId
    public static final String LEAVE_SUCCESS = "LEAVE_SUCCESS"; // LEAVE_SUCCESS|roomId

    public static final String SYSTEM_MESSAGE = "SYSTEM_MESSAGE"; // SYSTEM_MESSAGE|nội dung
    public static final String ERROR = "ERROR";                   // ERROR|code|message

    // ===== Phase 3: TCP Chat =====
    // Client gửi lên:   CHAT|noi_dung
    //   (không kèm roomId/username vì server đã biết qua ClientSession -> tránh
    //   client tự xưng "tôi là ai" hoặc "tôi đang ở phòng nào" giả mạo)
    // Server broadcast xuống: CHAT|roomId|sender|timestamp|noi_dung
    public static final String CHAT = "CHAT";

    // ===== Phase 4: TCP File Transfer =====
    // Cùng nguyên tắc với CHAT: client KHÔNG gửi roomId/username, server tự
    // suy ra từ ClientSession đang giữ (session.getCurrentRoom(), session.getUsername()).
    //
    // Client gửi lên (người gửi file):
    //   FILE_START|fileName|fileSize
    //   FILE_CHUNK|fileName|chunkIndex|base64Data
    //   FILE_END|fileName
    // Server forward xuống cho các participant còn lại trong phòng (thêm roomId,
    // sender vào giữa để người nhận biết ai gửi, phòng nào):
    //   FILE_START|roomId|sender|fileName|fileSize
    //   FILE_CHUNK|roomId|sender|fileName|chunkIndex|base64Data
    //   FILE_END|roomId|sender|fileName
    //
    // Vì sao base64Data mà không gửi thẳng byte thô?
    // Kết nối TCP hiện tại dùng BufferedReader/PrintWriter theo DÒNG VĂN BẢN
    // (readLine/println) cho TOÀN BỘ protocol, kể cả LOGIN, CHAT... Nếu gửi byte
    // thô của file (có thể chứa byte 0x0A giống ký tự xuống dòng), readLine() ở
    // đầu nhận sẽ hiểu nhầm là hết dòng giữa chừng, làm hỏng dữ liệu. Base64 chỉ
    // dùng các ký tự an toàn (A-Z a-z 0-9 + / =), luôn nằm gọn trong 1 dòng, nên
    // có thể tái sử dụng đúng pipe đọc/ghi dòng đã xây từ Phase 1 mà không cần
    // đổi sang chế độ đọc byte thô (phức tạp hơn nhiều cho 1 đồ án sinh viên).
    public static final String FILE_START = "FILE_START";
    public static final String FILE_CHUNK = "FILE_CHUNK";
    public static final String FILE_END = "FILE_END";

    // Ký tự ngăn cách giữa các trường trong 1 message, ví dụ: LOGIN|username|password
    public static final String DELIMITER = "\\|";

    private TcpCommand() {
        // Class chỉ chứa hằng số, không cho phép tạo object
    }
}
