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

    // Ký tự ngăn cách giữa các trường trong 1 message, ví dụ: LOGIN|username|password
    public static final String DELIMITER = "\\|";

    private TcpCommand() {
        // Class chỉ chứa hằng số, không cho phép tạo object
    }
}
