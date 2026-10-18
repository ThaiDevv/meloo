# Checklist backend (Spring Boot 4, Modulith)

## Bảo mật
- [ ] Endpoint mới có quy tắc trong `ModuleSecurityRules` của module; không mở `permitAll` rộng hơn cần.
- [ ] Kiểm quyền sở hữu tài nguyên: người chơi chỉ đọc được phiên/trận của mình. API admin chỉ cho role ADMIN (USER gọi bị 403).
- [ ] STOMP: xác thực ở frame CONNECT. SUBSCRIBE `/topic/rooms/{id}/**` chỉ cho thành viên phòng. Lệnh `/app/rooms/{id}/…` kiểm người gửi thuộc phòng và đúng vai trò (chủ phòng mới được start/settings).
- [ ] Không lộ đáp án: không trả title, artist, trackId hay URL preview của bài đang chơi trước ROUND_END, qua REST, STOMP, log, header hay thông báo lỗi. Audio chỉ đi qua token.
- [ ] Token: HMAC so sánh constant-time (`MessageDigest.isEqual`), có hạn, gắn đúng vòng + người chơi + mức. Refresh token lưu hash, xoay vòng, phát hiện dùng lại.
- [ ] Validate input bằng Bean Validation trên DTO; giới hạn độ dài theo BR (chat 200, biệt danh 3–20); rate limit đúng chỗ ticket yêu cầu (Bucket4j).
- [ ] Không log dữ liệu nhạy cảm (token, email đầy đủ, đáp án).
- [ ] Không có secret trong code hay config; đọc từ biến môi trường.
- [ ] Lỗi trả ProblemDetail, không lộ stack trace hay SQL.

## Dữ liệu và transaction
- [ ] `@Transactional` đặt ở service ứng dụng; query thuần dùng `readOnly = true`; không đặt ở controller.
- [ ] Không gọi HTTP ra ngoài (API nhạc) trong transaction DB.
- [ ] N+1: duyệt collection trong vòng lặp → dùng fetch join, `@EntityGraph`, projection hoặc batch. Nhớ mặc định của JPA: `@ManyToOne` và `@OneToOne` là EAGER, nên nạp một collection có quan hệ này sinh thêm truy vấn cho từng phần tử dù vòng lặp không đọc field nào; `break` giữa chừng không làm collection nạp ít đi. Mapping không có trong diff thì mở file entity để xem trước khi kết luận.
- [ ] Danh sách có thể lớn (lịch sử trận, người dùng, báo cáo) phải phân trang.
- [ ] Cột lọc/sắp xếp mới có index (index phải nằm trong migration của ticket DB).
- [ ] Listener sự kiện idempotent: sự kiện lặp không cộng điểm 2 lần (unique constraint hoặc bảng sự kiện đã xử lý).
- [ ] Entity không lọt ra ngoài module; API công khai trả record/DTO.

## Đồng thời và realtime
- [ ] Mọi thay đổi trạng thái phòng/trận đi qua hàng đợi lệnh của phòng (actor). Bộ hẹn giờ cũng gửi lệnh vào hàng đợi, không sửa trạng thái trực tiếp.
- [ ] Không chia sẻ mutable state giữa các thread bên ngoài actor; không rải `synchronized` thay cho actor.
- [ ] Race có test: hết giờ trùng lúc đoán, 2 người đúng cùng lúc, 2 người tranh chỗ cuối cùng, ghép trận đồng thời.
- [ ] Mọi sự kiện có `serverTime`; thứ tự sự kiện trong một phòng được giữ nguyên.
- [ ] Mất kết nối rồi vào lại: gửi lại trạng thái đầy đủ, giữ chỗ 30 giây (BR-14).

## Spring Boot 4 / Modulith
- [ ] Dùng Jackson 3 (`tools.jackson.*`). Không import `com.fasterxml.jackson.databind`; riêng annotation vẫn ở `com.fasterxml.jackson.annotation`.
- [ ] Spring Security 7: lambda DSL, không `.and()`, không `authorizeRequests`, không `AntPathRequestMatcher`.
- [ ] Module chỉ gọi module khác qua package gốc, `api/` hoặc `events/` (NamedInterface); `ApplicationModules.verify()` xanh.
- [ ] Cấu hình mới nằm trong `config/<module>.yml`, đọc bằng `@ConfigurationProperties` có validate.

## Test
- [ ] Có đủ test trong mục "Test bắt buộc" của ticket; tên test nói rõ hành vi.
- [ ] Integration dùng Testcontainers PostgreSQL, không dùng H2.
- [ ] Test đồng thời dùng nhiều client thật (STOMP) hoặc executor; chờ bằng Awaitility, không `Thread.sleep`.
- [ ] Assertion kiểm kết quả nghiệp vụ (điểm, trạng thái, sự kiện đã phát), không chỉ "không ném lỗi".
- [ ] Engine luật chơi: test bảng cho từng BR; coverage domain ≥ 80% (BE-GAME-01 ≥ 90%).
