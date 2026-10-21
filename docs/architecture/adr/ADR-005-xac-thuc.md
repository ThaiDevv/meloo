# ADR-005: Xác thực bằng JWT 15 phút và refresh cookie HttpOnly xoay vòng
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Meloo cho khách chơi không cần đăng ký, người đã đăng ký (email, Google, Facebook) có tiến trình và bảng xếp hạng. Frontend ở Vercel, backend ở EC2 nhưng cùng site nhờ tên miền chung (ADR-006). Cần phiên đủ ngắn để thu hồi nhanh, đủ tiện để không bắt đăng nhập lại liên tục, và dùng được cho cả REST lẫn frame CONNECT của STOMP (ADR-004).

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Session phía server (cookie session) | Thu hồi tức thì | Cần lưu session; khó dùng với WebSocket và nhiều domain |
| JWT dài hạn lưu localStorage | Đơn giản | XSS lấy được; khó thu hồi |
| **JWT access ngắn + refresh token cookie HttpOnly xoay vòng** | Access token chỉ ở bộ nhớ nên XSS khó lấy refresh; thu hồi trong ≤ 15 phút; phát hiện được token bị đánh cắp | Phức tạp hơn; cần bảng lưu refresh token |

## Quyết định
- Access token: JWT sống 15 phút, client chỉ giữ trong bộ nhớ, ký bằng khóa đọc từ biến môi trường.
- Refresh token: chuỗi ngẫu nhiên ≥ 256 bit, DB chỉ lưu hash SHA-256. Cookie `HttpOnly; Secure; SameSite=Lax`, giới hạn `Path` ở nhóm endpoint auth, hạn 30 ngày.
- Xoay vòng: mỗi lần refresh cấp token mới và thu hồi token cũ. Token cũ bị dùng lại thì thu hồi cả chuỗi (cùng family), vì đó là dấu hiệu bị đánh cắp.
- CSRF: refresh chỉ nhận POST, SameSite=Lax và kiểm header `Origin`.
- OAuth2 (Google, Facebook): authorization code chạy phía server, có state và PKCE; liên kết tài khoản theo email đã xác minh.
- Khách cũng nhận phiên theo cơ chế này; khi đăng ký thì gộp lịch sử khách vào tài khoản.

## Hệ quả
- Tốt: giảm rủi ro XSS và đánh cắp token; có cơ chế phát hiện tái sử dụng; một cơ chế cho REST và STOMP.
- Xấu hoặc rủi ro: khóa tài khoản chỉ có hiệu lực tức thì khi thu hồi refresh; access token còn sống tối đa 15 phút (giảm bằng thu hồi phiên khi khóa). Tải trang phải gọi refresh để lấy access token. Cookie cùng site đòi hỏi tên miền chung, phụ thuộc ADR-006. Mất khóa ký là sự cố nghiêm trọng: giữ ở biến môi trường, không commit.

## Câu hỏi bảo vệ
1. Vì sao access token chỉ giữ trong bộ nhớ? — Tránh XSS đọc từ localStorage; mất khi tải lại thì dùng refresh cookie lấy cái mới.
2. Xoay vòng refresh để làm gì? — Mỗi token dùng một lần; bị dùng lại tức có kẻ sao chép, thu hồi cả chuỗi.
3. Vì sao DB chỉ lưu hash? — Lộ DB cũng không dùng được token.
4. Làm sao khóa một tài khoản ngay? — Thu hồi mọi refresh token của họ; access token tự hết hạn trong ≤ 15 phút.
