# ADR-004: Realtime bằng STOMP qua WebSocket
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Phòng 2–5 người cần cập nhật tức thời: sảnh chờ, bắt đầu vòng, mở thêm đoạn nghe, bảng điểm, chat, và tín hiệu (offer, answer, ice) cho voice. Server là trọng tài nên mọi sự kiện phát từ server kèm `serverTime`. Chỉ có một instance backend (ADR-006), nên chưa cần mở rộng ngang.

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Polling REST | Đơn giản | Trễ, tốn request, khó đồng bộ đồng hồ vòng |
| Server-Sent Events | Nhẹ, một chiều | Lệnh lên vẫn phải qua REST; không có mô hình topic và tin riêng |
| WebSocket thuần | Linh hoạt | Tự làm định tuyến, đăng ký topic, heartbeat |
| **STOMP trên WebSocket (Spring simple broker)** | Có sẵn topic, user queue, heartbeat; Spring Security chặn được ở frame CONNECT/SUBSCRIBE; AsyncAPI mô tả được | Giao thức nặng hơn WS thuần; simple broker nằm trong bộ nhớ |

## Quyết định
Dùng STOMP qua WebSocket, endpoint `/ws`:
- Lệnh lên: `/app/rooms/{roomId}/{command}`. Sự kiện phòng: `/topic/rooms/{roomId}/{lobby|round|scoreboard|chat|rtc}`. Tin riêng: `/user/queue/{errors|private}`.
- JWT kiểm ở frame CONNECT (ChannelInterceptor) rồi gắn Principal. SUBSCRIBE `/topic/rooms/{id}/**` chỉ cho thành viên phòng; SEND kiểm người gửi thuộc phòng và đúng vai trò.
- Message nào cũng có `type` và `serverTime`; không message nào chứa đáp án trước `ROUND_END`.
- Bật heartbeat. Mất kết nối giữ chỗ 30 giây (BR-14); vào lại nhận snapshot đầy đủ.
- Dùng simple broker trong bộ nhớ. Mở rộng ngang (Redis, broker relay) để Phase 2, không thêm sớm.

## Hệ quả
- Tốt: một kênh cho lệnh và sự kiện; phân quyền theo destination; hợp đồng AsyncAPI rõ; đồng hồ thống nhất nhờ `serverTime`.
- Xấu hoặc rủi ro: simple broker không chạy nhiều instance và mất hết khi restart: chấp nhận ở MVP, client tự nối lại và nhận snapshot. WebSocket sau proxy cần cấu hình đúng (Caddy cho đi qua, xem ADR-006). Test đồng thời phải dùng `WebSocketStompClient` thật.

## Câu hỏi bảo vệ
1. Vì sao STOMP mà không WebSocket thuần? — Có sẵn topic, tin riêng, heartbeat và chặn quyền theo destination, đỡ tự viết.
2. Simple broker giới hạn gì? — Một instance, trạng thái trong bộ nhớ; muốn nhiều instance cần broker relay (Phase 2).
3. Làm sao biết ai được nghe topic phòng? — Interceptor kiểm thành viên phòng ở SUBSCRIBE.
4. Rớt mạng giữa vòng thì sao? — Giữ chỗ 30 giây; vào lại nhận snapshot và `serverTime` để căn đồng hồ.
