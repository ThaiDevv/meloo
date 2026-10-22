# Kế hoạch migration Flyway (DES-01)

> Ticket: MEL-30 · Bản nháp chờ Thái duyệt. Nguồn sự thật về số version; `.claude/skills/meloo-backend/references/migrations.md` chép lại bảng này. Cần version ngoài bảng thì dừng và đề xuất ticket, không tự lấy số.

| Version | File (đề xuất) | Module | Bảng | Ticket |
|---|---|---|---|---|
| V1 | `V1__baseline.sql` | shared | baseline | SET-03 (MEL-22) |
| V2 | `V2__identity_init.sql` | identity | `app_user`, `auth_identity`, `refresh_token` | DB-01 (MEL-32) |
| V3 | `V3__catalog_init.sql` | catalog | `artist`, `genre`, `track`, `playlist`, `playlist_track`; extension `pg_trgm`, `unaccent`; index trigram | DB-01 |
| V4 | `V4__progression_match_history.sql` | progression | `match`, `match_player`, `round`, `round_result` | DB-01 |
| V5 | `V5__chat_init.sql` | chat | `chat_report`, `banned_word` + từ cấm khởi tạo | DB-01 |
| V6 | `V6__progression_stats.sql` | progression | `player_stats`, `achievement` (+ dòng khởi tạo), `user_achievement` | DB-02 (MEL-66) |
| V7 | `V7__daily_init.sql` | daily | `daily_challenge`, `daily_attempt` | DB-02 |
| V8 | `V8__admin_audit_log.sql` | admin | `audit_log` | DB-02 |
| V9 | `V9__admin_analytics_event.sql` | admin | `analytics_event` | DB-02 |

Mỗi version thuộc đúng một ticket DB: V2–V5 là DB-01, V6–V9 là DB-02. V5 (chat) nằm ở DB-01 vì BE-CHAT-01 chạy ở S3, trước DB-02 (S4).

## Quy tắc áp dụng
- Không sửa migration đã merge vào `main`; cần sửa thì thêm version mới (cần ticket và số mới từ Thái).
- Thứ tự V4 → V6 an toàn vì không có khóa ngoại chéo module (tài liệu 01 mục 5); không migration nào tham chiếu bảng của version sau.
- Khóa `hot-migration`: mỗi lúc chỉ một ticket được giữ. DB-01 và DB-02 không chạy cùng lúc.
- Dữ liệu cấu hình nằm trong migration: từ cấm khởi tạo (V5), danh sách `achievement` (V6). Dữ liệu bài hát đặt ở `seed/` (BE-CAT-02), không trong migration.
- Test (Testcontainers PostgreSQL 17): migrate từ DB trống; riêng DB-02 thêm ca V5 → V9; kiểm ràng buộc bằng insert vi phạm và kiểm index qua `pg_indexes`.

## Thay đổi so với kế hoạch trước
| Điểm | Trước | Sau | Lý do |
|---|---|---|---|
| V4 | "game/match", entity ở `game/persistence` | Lịch sử trận, entity ở `progression/persistence` | Tài liệu 01 mục 3 |
| Tên file V4 | — | `V4__progression_match_history.sql` | Quy ước `V<n>__<module>_<mô tả>` theo module sở hữu |
