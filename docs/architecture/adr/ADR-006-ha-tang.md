# ADR-006: Hạ tầng Vercel, EC2, RDS và tên miền
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Đồ án có ngân sách gần bằng không và một người vận hành, nhưng phải chạy thật để chơi thử với người dùng (beta). Cần frontend tĩnh có preview cho mỗi PR, backend giữ kết nối WebSocket lâu, và PostgreSQL. Cookie refresh HttpOnly (ADR-005) cần frontend và API **cùng site**; kết nối realtime phải là WSS.

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Tất cả trên một VPS | Rẻ, đơn giản | Frontend không có preview theo PR; DB và app tranh RAM |
| PaaS nguyên khối (Render, Railway, Fly) | Ít vận hành | Giới hạn free tier với WebSocket và tắt máy khi rảnh; phụ thuộc nhà cung cấp |
| **Vercel (frontend) + EC2 (backend) + RDS (PostgreSQL) + tên miền riêng** | Vercel có preview mỗi PR; EC2 chạy được WebSocket liên tục; RDS tách DB, có sao lưu; dùng được free tier AWS | Nhiều mảnh phải cấu hình; EC2 chỉ 1 GB RAM |

## Quyết định
- Frontend deploy lên Vercel từ `main`; preview mỗi PR với dữ liệu mock.
- Backend chạy Docker Compose trên một EC2, Caddy phía trước lo HTTPS tự động và `reverse_proxy` (WebSocket đi qua, nên client dùng **WSS**). Image đẩy lên GHCR rồi `docker compose pull && up -d`.
- PostgreSQL 17 trên RDS.
- Một tên miền: frontend ở tên miền chính, API ở `api.<tên miền>`. Hai bên cùng site (registrable domain) nên cookie refresh `SameSite=Lax` gửi được; CORS chỉ mở cho tên miền chính và URL preview của dự án.
- Một instance backend là chủ ý, khớp ADR-004 và ADR-007 (trạng thái phòng trong bộ nhớ).

## Hệ quả
- Tốt: chi phí thấp; preview PR; DB tách khỏi app; cookie cùng site giữ được cơ chế xác thực an toàn.
- Xấu hoặc rủi ro: EC2 1 GB RAM dễ hết bộ nhớ (giảm bằng `-XX:MaxRAMPercentage=60`, swap 2 GB, giới hạn log Docker). Một instance là điểm lỗi duy nhất và deploy làm rớt WebSocket (client nối lại; trạng thái phòng mất, xem ADR-007). Free tier AWS có hạn: `[CẦN SỐ LIỆU: chi phí thực tế sau free tier]`. Thao tác AWS và DNS do Thái làm tay theo runbook.

## Câu hỏi bảo vệ
1. Vì sao cần cùng site? — Để cookie refresh HttpOnly SameSite=Lax được gửi tới API mà không phải hạ xuống `SameSite=None`.
2. Vì sao không dùng PaaS? — Giới hạn free tier với kết nối WebSocket lâu và việc phải tắt khi rảnh.
3. Vì sao WSS? — Trang HTTPS không được mở WS không mã hóa; Caddy cấp chứng chỉ tự động.
4. Điểm lỗi duy nhất ở đâu và chấp nhận vì sao? — Một EC2; MVP chưa cần HA, chi phí và độ phức tạp cao hơn lợi ích.
