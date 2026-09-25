package server.file;

import common.protocol.TcpCommand;
import server.room.Room;
import server.session.ClientSession;

/**
 * FileTransferManager chịu trách nhiệm FORWARD (chuyển tiếp) các dòng
 * FILE_START/FILE_CHUNK/FILE_END từ người gửi tới các participant còn lại
 * trong phòng.
 *
 * Vì sao server KHÔNG lưu file xuống đĩa?
 * Ở kiến trúc Client-Server cho File Transfer, server chỉ đóng vai trò
 * "trạm trung chuyển" (giống bưu tá), không cần quan tâm nội dung file là gì.
 * Mỗi chunk vừa nhận được relay đi NGAY LẬP TỨC cho các client còn lại
 * (streaming thật, không chờ nhận đủ file rồi mới gửi tiếp), nên server
 * không cần bộ nhớ/đĩa để giữ file tạm.
 *
 * Vì sao các hàm ở đây là static (khác RoomManager/SessionManager)?
 * Class này không giữ state riêng nào (không có field) - chỉ có nhiệm vụ
 * "định dạng 1 dòng theo protocol rồi gọi Room.broadcast()". RoomManager/
 * SessionManager bắt buộc phải là 1 instance dùng chung vì chúng NẮM GIỮ
 * danh sách room/session (là state cần chia sẻ giữa các thread/handler).
 */
public final class FileTransferManager {

    private FileTransferManager() {
    }

    /** Forward FILE_START: báo cho các client khác "sắp có file tên gì, dung lượng bao nhiêu". */
    public static void relayFileStart(Room room, ClientSession sender, String fileName, String fileSize) {
        String line = TcpCommand.FILE_START + "|" + room.getRoomId() + "|" + sender.getUsername()
                + "|" + fileName + "|" + fileSize;
        room.broadcast(line, sender);
    }

    /** Forward FILE_CHUNK: relay nguyên vẹn 1 mẩu dữ liệu (đã base64) của file. */
    public static void relayFileChunk(Room room, ClientSession sender, String fileName,
                                       String chunkIndex, String base64Data) {
        String line = TcpCommand.FILE_CHUNK + "|" + room.getRoomId() + "|" + sender.getUsername()
                + "|" + fileName + "|" + chunkIndex + "|" + base64Data;
        room.broadcast(line, sender);
    }

    /** Forward FILE_END: báo cho các client khác "đã gửi xong, có thể đóng file lại". */
    public static void relayFileEnd(Room room, ClientSession sender, String fileName) {
        String line = TcpCommand.FILE_END + "|" + room.getRoomId() + "|" + sender.getUsername()
                + "|" + fileName;
        room.broadcast(line, sender);
    }
}
