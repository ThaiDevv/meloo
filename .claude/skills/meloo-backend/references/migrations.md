# Migration Flyway

## Cấp số version (DES-01 là nguồn sự thật)
| Version | Nội dung | Ticket |
|---|---|---|
| V1 | baseline | SET-03 |
| V2 | identity | DB-01 |
| V3 | catalog (pg_trgm, unaccent, index trigram) | DB-01 |
| V4 | game/match | DB-01 |
| V5 | chat: chat_report, banned_word + danh sách từ cấm khởi tạo | DB-01 |
| V6 | progression | DB-02 |
| V7 | daily | DB-02 |
| V8 | admin: audit_log | DB-02 |
| V9 | analytics_event | DB-02 |

Cần version ngoài bảng thì dừng và đề xuất ticket, không tự lấy số.

## Quy tắc
- Tên file `V<n>__<module>_<mo_ta>.sql`, snake_case tiếng Anh.
- Không sửa migration đã merge vào main. Muốn sửa thì viết version mới (cần ticket).
- Bảng nào cũng có khóa chính (`uuid` hoặc `bigint generated always as identity`, theo DES-01) và `created_at timestamptz not null default now()`.
- Thời gian lưu kiểu `timestamptz` (UTC). Tiền không dùng kiểu float.
- Index cho: mọi khóa ngoại dùng để join; cột lọc/sắp xếp chính.
- Tìm kiếm không dấu: lưu cột đã chuẩn hóa (bỏ dấu, chữ thường) rồi đánh GIN trigram (`gin_trgm_ops`). Hàm `unaccent()` không IMMUTABLE nên không dùng thẳng trong index biểu thức được; nếu cần thì tạo hàm bọc IMMUTABLE.
- Extension: `create extension if not exists pg_trgm;` và `create extension if not exists unaccent;` (RDS hỗ trợ cả hai).
- Dữ liệu cấu hình (danh sách từ cấm khởi tạo) đặt trong migration. Dữ liệu bài hát đặt ở `seed/` (BE-CAT-02), không đặt trong migration.

## Test
- Testcontainers PostgreSQL 17: migrate từ DB trống. Riêng DB-02 thêm ca migrate từ V5 lên V9.
- Kiểm ràng buộc bằng insert vi phạm (mong đợi lỗi); kiểm index có tồn tại qua `pg_indexes`.
