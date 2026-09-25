package server.room;

import server.session.ClientSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RoomManager là nơi DUY NHẤT quản lý toàn bộ phòng đang tồn tại trên server.
 * Có 1 instance duy nhất (tạo 1 lần trong ServerApplication), được chia sẻ
 * cho mọi TCPClientHandler -> đây là "shared resource" nên bắt buộc thread-safe.
 *
 * ConcurrentHashMap: nhiều thread (nhiều client) có thể gọi createRoom()/joinRoom()
 * cùng lúc mà không làm hỏng cấu trúc dữ liệu bên trong map.
 *
 * AtomicInteger: dùng để sinh roomId tăng dần (ROOM001, ROOM002...).
 * incrementAndGet() là thao tác atomic, nếu dùng "int counter" thường + "counter++"
 * thì 2 thread có thể cùng đọc được 1 giá trị cũ -> sinh trùng roomId.
 */
public class RoomManager {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger(0);

    /** Tạo phòng mới, tự động cho người tạo vào làm participant đầu tiên. */
    public Room createRoom(String roomName, ClientSession creator) {
        String roomId = String.format("ROOM%03d", roomCounter.incrementAndGet());
        Room room = new Room(roomId, roomName);
        rooms.put(roomId, room);

        room.addParticipant(creator);
        creator.setCurrentRoom(room);
        return room;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /** @return Room nếu join thành công, null nếu không tìm thấy roomId. */
    public Room joinRoom(String roomId, ClientSession session) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        room.addParticipant(session);
        session.setCurrentRoom(room);
        return room;
    }

    /** Cho session rời khỏi phòng hiện tại. Nếu phòng trống sau đó -> xoá phòng khỏi map. */
    public void leaveRoom(ClientSession session) {
        Room room = session.getCurrentRoom();
        if (room == null) {
            return;
        }
        room.removeParticipant(session);
        session.setCurrentRoom(null);

        if (room.isEmpty()) {
            rooms.remove(room.getRoomId());
        }
    }
}
