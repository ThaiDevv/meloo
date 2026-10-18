# Token và phiên đăng nhập (ADR-005, ADR-008)

- Access token: JWT 15 phút, phía client chỉ giữ trong bộ nhớ; ký bằng khóa đọc từ biến môi trường.
- Refresh token:
  - Chuỗi ngẫu nhiên ≥ 256 bit; DB chỉ lưu hash (SHA-256).
  - Cookie `HttpOnly; Secure; SameSite=Lax`, giới hạn `Path` ở nhóm endpoint auth; hạn 30 ngày.
  - Mỗi lần refresh cấp token mới và thu hồi token cũ. Token cũ bị dùng lại thì thu hồi cả chuỗi (cùng family).
- Audio token: HMAC-SHA256 trên (roundId, playerId, level, expiresAt). So sánh bằng `MessageDigest.isEqual`. Hết hạn ≤ 2 phút. URL không chứa id hay tên bài.
- OAuth2 (Google, Facebook): authorization code chạy phía server, có state + PKCE. Liên kết tài khoản theo email đã xác minh.
- Rate limit bằng Bucket4j theo IP hoặc người dùng, ở những chỗ ticket yêu cầu.
- CSRF: cookie refresh dùng SameSite=Lax và chỉ nhận POST; kiểm header Origin ở endpoint refresh.
