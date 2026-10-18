# Realtime: phòng, hàng đợi lệnh, STOMP

## Hàng đợi lệnh của phòng (ADR-007)
- `RoomRegistry` giữ `RoomActor` theo roomId. Mỗi actor có một hàng đợi lệnh và xử lý tuần tự trên executor riêng (VD single-thread executor hoặc virtual thread cho mỗi phòng).
- MỌI thay đổi trạng thái là một lệnh `submit(cmd)`: vào, rời, sẵn sàng, bắt đầu, nghe thêm, đoán, hết giờ, mất kết nối. Bộ hẹn giờ không sửa state; đến giờ thì nó gửi lệnh `Timeout(roundId)` vào hàng đợi.
- Lệnh mang `roundId` (hoặc số phiên bản). Lệnh đến muộn cho vòng đã qua thì bỏ qua. Nhờ vậy "hết giờ trùng lúc trả lời" tự hết race: lệnh nào vào hàng đợi trước thì thắng, lệnh sau thấy vòng đã đóng.
- State bên trong actor không chia sẻ ra ngoài. Bên ngoài chỉ đọc snapshot bất biến (record).
- Xử lý xong mỗi lệnh thì phát sự kiện tới `/topic/rooms/{id}/…` kèm `serverTime`; tin riêng gửi qua `/user/queue/…`.

## STOMP
- Endpoint `/ws`. JWT được kiểm ở frame CONNECT (ChannelInterceptor) rồi gắn Principal.
- SUBSCRIBE `/topic/rooms/{id}/**`: chỉ thành viên phòng (kiểm qua RoomRegistry).
- SEND `/app/rooms/{id}/…`: kiểm người gửi thuộc phòng và có đúng vai trò (chỉ chủ phòng được start hay đổi settings).
- Bật heartbeat. Mất kết nối → lệnh `Disconnected(player)` → giữ chỗ 30 giây (BR-14). Vào lại → gửi snapshot đầy đủ.
- Dùng simple broker trong bộ nhớ (một instance). Mở rộng ngang là việc của Phase 2 (Redis + broker relay), đừng thêm sớm.

## Test đồng thời
- Integration dùng `WebSocketStompClient` thật, 2–10 client gửi lệnh đồng thời (CountDownLatch hoặc executor), rồi assert trạng thái cuối nhất quán (VD đúng một người vào được chỗ cuối).
- Đồng hồ: inject `Clock` hoặc scheduler giả để test hết giờ không phải chờ thật. Chỗ nào cần chờ thì dùng Awaitility, không `Thread.sleep`.
