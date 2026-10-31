# Thiết kế Sơ đồ tuần tự (Sequence Diagrams) - DOC-03 (MEL-51)

Thư mục này chứa các sơ đồ tuần tự thể hiện các luồng tương tác chính của Meloo, khớp hợp đồng REST (OpenAPI 3.1) và STOMP WebSocket (AsyncAPI 3) cùng thiết kế kiến trúc cho tính năng đăng nhập mạng xã hội (BE-ID-02), đối chiếu tại commit `c0f723e`.

## Danh mục sơ đồ
1. **`seq-dang-nhap-khach.puml` / `seq-dang-nhap-khach.png`**: Đăng nhập khách bằng biệt danh (UC-01, BR-20, BR-22)
   - Luồng: Client gọi `POST /api/v1/auth/guest` kèm biệt danh.
   - Kiểm tra: `GuestRateLimiter` chặn spam theo IP; `NicknameValidator` kiểm tra độ dài 3–20 ký tự và lọc từ cấm (BR-22).
   - Kết quả: Tạo `AppUser` loại `GUEST`, phát sự kiện `GuestCreated`, sinh JWT Access Token (15 phút) và đặt Cookie HttpOnly Refresh Token (30 ngày), trả về mã `201 Created`.

2. **`seq-dang-nhap-google.puml` / `seq-dang-nhap-google.png`**: Đăng nhập Google & Gộp tài khoản khách (UC-02, BR-21 - Thiết kế đề xuất BE-ID-02)
   - Lưu ý kiến trúc: OpenAPI v1 hiện tại tập trung cho phiên khách (`/api/v1/auth/guest`); endpoint đăng nhập mạng xã hội được chuẩn bị kiến trúc tại sơ đồ này và sẽ được định nghĩa chính thức trong hợp đồng CT-03 (Sprint S2/S3).
   - Luồng: Người dùng xác thực OAuth2 qua Google, gửi mã xác thực về backend.
   - Xử lý: Backend xác minh với máy chủ Google, kiểm tra liên kết `AuthIdentity`. Tạo mới người dùng `REGISTERED` nếu là lần đầu.
   - Gộp tài khoản (BR-21): Nếu đang ở phiên khách, toàn bộ lịch sử đấu, thống kê, huy hiệu và điểm xếp hạng của khách được chuyển giao sang tài khoản đăng ký. Nếu có xung đột thử thách ngày, ưu tiên giữ lượt của tài khoản đăng ký.

3. **`seq-vong-choi-don.puml` / `seq-vong-choi-don.png`**: Một vòng chơi đơn (UC-07, UC-08, BR-01..05, BR-08, ADR-008)
   - Luồng: Client lấy thông tin vòng chơi `GET /api/v1/solo/matches/{matchId}/rounds/current`.
   - Audio Token mờ (ADR-008): Server cấp token HMAC thời hạn ngắn (mặc định 2 phút, tối đa 2 phút theo `AudioProperties` và ADR-008), URL phát audio không lộ id bài hát hay tên bài.
   - Thao tác người chơi:
     - Nghe lại: Không giới hạn, không mất điểm (BR-03).
     - Nghe thêm: `POST .../listen-more` tăng mức nghe 2s → 5s → 10s → 20s → 30s (BR-04).
     - Đoán bài: `POST .../guesses` chuẩn hóa tên không dấu (BR-05). Nếu đúng: cộng điểm cơ bản (1.000..200, BR-02) nhân hệ số combo (BR-08), công bố đáp án và link Deezer (BR-29). Nếu sai: gợi ý đúng ca sĩ (BR-06) và tự động mở mức nghe tiếp theo.

4. **`seq-tao-va-vao-phong.puml` / `seq-tao-va-vao-phong.png`**: Tạo và vào phòng chơi nhiều người (UC-12, UC-13, BR-11, BR-12, BR-15)
   - Tạo phòng: Chủ phòng gọi `POST /api/v1/rooms`, sinh mã phòng 6 ký tự dễ đọc (bỏ 0, O, 1, I, L, BR-11), khởi tạo `RoomActor`.
   - Vào phòng: Người chơi gọi `POST /api/v1/rooms/join` bằng mã phòng. Kiểm tra giới hạn 5 người, trạng thái trận đấu (nếu đang chạy, xếp vào sảnh chờ, BR-15).
   - Realtime: Cả hai bên kết nối WebSocket STOMP `/ws`, lắng nghe `/topic/rooms/{roomId}/lobby`. Khách gửi lệnh `/app/rooms/{roomId}/ready` để sẵn sàng.

5. **`seq-vong-nhieu-nguoi.puml` / `seq-vong-nhieu-nguoi.png`**: Một vòng chơi nhiều người realtime qua STOMP (UC-18, BR-07, BR-08, BR-10, BR-16)
   - Bắt đầu: `RoomActor` chọn bài ngẫu nhiên từ kho nhạc (BR-09), phát sinh audio token mức 1 (2s) cho từng thành viên qua `/user/queue/private`, kích hoạt đồng hồ server 45 giây và gửi `RoundStartEvent` qua `/topic/rooms/{roomId}/round`.
   - Tương tác: Mỗi người tự mở mức nghe của mình (BR-07); tin nhắn chat lộ đáp án bị chặn tự động (BR-16).
   - Đoán bài: Lượt đoán gửi qua `/app/.../guess`. `RoomActor` kiểm tra thời gian < 45.000 ms, tính điểm cơ bản + combo + thưởng tốc độ `floor(200 × ms_còn_lại ÷ 45.000)` (BR-07). Khi có người đoán đúng, thông báo ra phòng nhưng **ẩn tên bài hát** (BR-07).
   - Kết thúc: Hết 45s hoặc tất cả người chơi hoàn thành, server phát `RoundEndEvent` kèm đáp án Deezer (BR-29) và phát `ScoreboardUpdateEvent` cập nhật bảng xếp hạng.

## Hướng dẫn giải thích khi bảo vệ đồ án
- **Làm sao chống người chơi "soi network" xem trước bài hát khi tải audio?**
  *Trả lời:* Theo quyết định kiến trúc ADR-008, URL audio chỉ chứa token HMAC mã hóa ngắn hạn. Server chỉ stream đúng đoạn âm thanh tương ứng với mức nghe hiện tại (2 giây, 5 giây...), client không thể tải trước toàn bộ bài hay đọc tiêu đề từ URL/header.
- **Tại sao tin nhắn chat lộ đáp án lại bị chặn ở server?**
  *Trả lời:* Theo BR-16, để giữ tính công bằng và chống người chơi nhắc bài cho bạn bè, nội dung chat đi qua bộ lọc chuẩn hóa không dấu giống bộ chấm đáp án. Nếu trùng tên bài đáp án của vòng đang chơi, tin nhắn bị chặn ngay tại `RoomActor` và chỉ gửi cảnh báo về người vi phạm.
