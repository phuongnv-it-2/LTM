package server.session;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionManager giữ danh sách username đang online (key) map tới
 * ClientSession tương ứng (value).
 *
 * Dùng ConcurrentHashMap vì nhiều thread (mỗi client 1 thread, xem TCPClientHandler)
 * có thể gọi login()/logout() cùng lúc -> map thường (HashMap) sẽ lỗi dữ liệu
 * khi nhiều thread ghi đồng thời.
 *
 * putIfAbsent() là thao tác ATOMIC (không thể bị 2 thread xen vào giữa chừng),
 * nên dùng nó để kiểm tra "username đã tồn tại chưa" và "thêm username" trong
 * đúng 1 bước, tránh race condition kiểu: 2 client cùng gửi LOGIN|A cùng lúc,
 * cả 2 đều kiểm tra "chưa có A" (containsKey false) rồi cùng thêm -> lỗi trùng.
 */
public class SessionManager {

    private final ConcurrentHashMap<String, ClientSession> onlineUsers = new ConcurrentHashMap<>();

    /**
     * @return true nếu đăng nhập thành công (username chưa ai dùng),
     *         false nếu username đã có người khác đang online.
     */
    public boolean login(String username, ClientSession session) {
        ClientSession existing = onlineUsers.putIfAbsent(username, session);
        if (existing == null) {
            session.setUsername(username);
            return true;
        }
        return false;
    }

    /** Gọi khi client ngắt kết nối, để giải phóng username cho người khác dùng lại. */
    public void logout(ClientSession session) {
        String username = session.getUsername();
        if (username != null) {
            // remove(key, value): chỉ xoá nếu value đúng là session này,
            // tránh trường hợp hiếm: session cũ bị xoá nhầm session mới đã login lại cùng username.
            onlineUsers.remove(username, session);
        }
    }

    public boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
}
