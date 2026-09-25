package server.room;

import server.session.ClientSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Room đại diện 1 phòng phỏng vấn, chứa danh sách các ClientSession đang tham gia.
 *
 * Dùng CopyOnWriteArrayList thay vì ArrayList thường vì:
 * - Nhiều thread có thể đọc (broadcast tin nhắn) trong khi 1 thread khác ghi
 *   (thêm/xoá participant khi có người join/leave) cùng lúc.
 * - CopyOnWriteArrayList cho phép duyệt (for-each) an toàn ngay cả khi có thread
 *   khác đang sửa danh sách, không ném ConcurrentModificationException.
 * - Phòng phỏng vấn chỉ có vài người (2-5), số lần ghi (join/leave) rất ít so với
 *   số lần đọc (broadcast chat/video liên tục ở phase sau) -> CopyOnWriteArrayList
 *   là lựa chọn phù hợp (tối ưu cho đọc nhiều, ghi ít).
 */
public class Room {

    private final String roomId;
    private final String roomName;
    private final CopyOnWriteArrayList<ClientSession> participants = new CopyOnWriteArrayList<>();

    public Room(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public void addParticipant(ClientSession session) {
        participants.add(session);
    }

    public void removeParticipant(ClientSession session) {
        participants.remove(session);
    }

    public List<ClientSession> getParticipants() {
        return participants;
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    /** Gửi message cho tất cả participant TRỪ 1 người (thường là người vừa gây ra sự kiện). */
    public void broadcast(String message, ClientSession exclude) {
        for (ClientSession p : participants) {
            if (p != exclude) {
                p.send(message);
            }
        }
    }
}
