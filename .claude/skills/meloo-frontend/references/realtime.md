# STOMP và đồng hồ phía client

- Dùng `Client` của `@stomp/stompjs`:
  - `brokerURL` là `wss://api.<tên miền>/ws` (dev: `ws://localhost:8080/ws`).
  - `connectHeaders: { Authorization: 'Bearer <access token>' }`, lấy token mới mỗi lần kết nối lại.
  - `reconnectDelay` tăng dần; heartbeat 10 giây.
- Subscribe theo phòng: `/topic/rooms/{id}/lobby|round|scoreboard|chat` và `/user/queue/...`. Rời màn thì hủy subscribe.
- Vào lại sau khi mất mạng: chờ snapshot từ server rồi mới hiển thị; trong lúc chờ hiện banner "Đang kết nối lại".
- Lệch giờ: sự kiện nào cũng có `serverTime`.
  - `offset = serverTime - Date.now()`, lấy trung vị của vài mẫu gần nhất.
  - Thời gian còn lại của vòng = `endsAt - (Date.now() + offset)`.
- Store Zustand: một store cho phòng, reducer theo loại sự kiện. Bỏ qua sự kiện của vòng cũ (`roundId` khác vòng hiện tại).
- Type của sự kiện lấy từ `src/shared/realtime/messages.ts` (viết ở CT-02, khớp AsyncAPI), không tự khai báo lại.
