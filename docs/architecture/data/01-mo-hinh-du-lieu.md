# Mô hình dữ liệu Meloo (DES-01)

> Ticket: MEL-30 (DES-01) · Trạng thái: **bản nháp chờ Thái duyệt**
> Căn cứ: `docs/srs/02-business-rules.md` (BR bản 22/10), ADR-005, ADR-007. Sơ đồ: [`erd.puml`](erd.puml), [`../../uml/class/domain.puml`](../../uml/class/domain.puml). Chi tiết cột: [`02-tu-dien-du-lieu.md`](02-tu-dien-du-lieu.md). Số migration: [`03-ke-hoach-migration.md`](03-ke-hoach-migration.md). Sự kiện và API giữa module: [`04-su-kien-va-api-module.md`](04-su-kien-va-api-module.md).

## 1. Nguyên tắc
1. Server là trọng tài, nên mọi điểm trong DB là điểm server tính; không bảng nào nhận điểm từ client.
2. Mỗi bảng thuộc đúng một module; chỉ module đó đọc và ghi bảng của mình (Spring Modulith). Module khác hỏi qua `api/` hoặc nghe sự kiện.
3. Trạng thái sống ngắn (phòng, vòng đang chơi) nằm trong bộ nhớ (ADR-007). DB chỉ giữ kết quả bền.
4. Thời gian `timestamptz` (UTC); ngày của thử thách ngày là `date` theo giờ Việt Nam (BR-25).

## 2. Thực thể và module sở hữu

21 bảng. Đối chiếu với danh sách trong ticket: **bớt 1, đổi tên 2, thêm 0**.

| Module | Bảng | Migration | Ghi chú |
|---|---|---|---|
| identity | `app_user`, `auth_identity`, `refresh_token` | V2 | User → `app_user` vì `user` là từ khóa của PostgreSQL |
| catalog | `artist`, `genre`, `track`, `playlist`, `playlist_track` | V3 | |
| progression | `match`, `match_player`, `round`, `round_result` | V4 | Xem mục 3 |
| chat | `chat_report`, `banned_word` | V5 | |
| progression | `player_stats`, `achievement`, `user_achievement` | V6 | |
| daily | `daily_challenge`, `daily_attempt` | V7 | |
| admin | `audit_log` | V8 | |
| admin | `analytics_event` | V9 | |

**Khác với danh sách ở ticket, và lý do:**

| Thay đổi | Lý do |
|---|---|
| Bỏ `Song`, chỉ giữ `track` | BR-05 chấm theo tên bài đã chuẩn hóa và coi mọi bản thu cùng tên là đúng; không use case nào cần khái niệm "tác phẩm" tách khỏi "bản thu". Thêm `Song` chỉ thêm một phép nối. |
| `User` → `app_user` | Tránh từ khóa dành riêng. Khách và người đã đăng nhập chung một bảng, phân biệt bằng `kind` (ADR-005: khách cũng nhận phiên theo cơ chế này). |
| `Guess` → `round_result` | Không use case nào cần từng lần đoán sai; cần điểm cơ bản, hệ số combo, thưởng tốc độ và mức đoán đúng của mỗi người ở mỗi vòng (AC của MEL-30, BR-07, BR-08). Lưu `wrong_count` là đủ. Thuật ngữ "lượt đoán" của glossary vẫn dùng cho thao tác của người chơi; DB chỉ lưu kết quả cuối vòng. |
| Ca sĩ phụ không có bảng nối `track_artist` | BR-06 chỉ xét ca sĩ chính; `track.primary_artist_id` là đủ. |
| Không có bảng tin nhắn chat | Tin chat chỉ sống trong phòng (bộ nhớ). Chỉ tin bị báo cáo mới lưu, kèm 5 tin ngữ cảnh, trong `chat_report`. |
| Không có bảng chuỗi ngày | Chuỗi ngày tính từ `daily_attempt` (các ngày liên tiếp có lượt). Một nguồn dữ liệu nên gộp khách (BR-21) không phải sửa thêm bảng. |

## 3. Quyết định: module sở hữu lịch sử trận

**Quyết định: `progression` sở hữu và ghi `match`, `match_player`, `round`, `round_result`, đồng thời phục vụ `/me/matches`. `game` chỉ giữ engine (`game/domain`) và phiên chơi đơn đang chạy trong bộ nhớ. Entity V4 đặt ở `progression/persistence`.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. `game/persistence` ghi lịch sử, progression đọc qua `game/api` | Khớp DB-01 hiện tại | Progression đã nghe `MatchFinished` để tính thống kê, nên phải gọi ngược game để lấy dữ liệu vừa được game phát ra; hai module cùng đụng một sự kiện. `/me/matches` phải đi vòng qua game. Room cũng phát `MatchFinished`, nên "bảng của game" lại do code của room kích hoạt. |
| **B. `progression` sở hữu, ghi từ `MatchFinished`** | Một chủ cho cả lịch sử, thống kê, bảng xếp hạng; gộp khách (BR-21) chỉ sửa trong một module; engine không phụ thuộc DB nên test nhanh; khớp ADR-007 ("kết quả bền ghi khi trận kết thúc qua `MatchFinished`") | Sự kiện phải mang đủ dữ liệu từng vòng (xem tài liệu 04) |

Chọn B. Hệ quả: `MatchFinished` thành hợp đồng dữ liệu lớn hơn; mọi ticket phát hoặc nghe nó phải theo. Hợp đồng nằm ở tài liệu 04.

**Ticket phải sửa theo (Thái cập nhật Jira trước khi chúng chạy):**

| Ticket | Sửa gì |
|---|---|
| DB-01 (MEL-32) | V4 là lịch sử trận, thuộc module progression: entity và repository đặt ở `progression/persistence`, không phải `game/persistence`. Thêm `progression/persistence` vào Phạm vi file; bỏ `game` khỏi danh sách package `persistence`. Tên file `V4__progression_match_history.sql`. Ghi chú "chạy song song BE-GAME-01" không còn cần. |
| BE-SOLO-01 (MEL-45) | Không ghi bảng nào. Phát `MatchStarted` và `MatchFinished` đủ payload ở tài liệu 04 (loại trận, cờ xếp hạng, trạng thái `FINISHED`/`ABANDONED`, kết quả từng vòng). Hạn giữ phiên: AC ghi 2 giờ, BR-23 ghi 30 phút; cần thống nhất (xem mục 7). |
| BE-ROOM-02 (MEL-56) | Phát `MatchFinished` đủ payload như trên cho `PRIVATE_ROOM`, `PUBLIC_ROOM`, `QUICK_MATCH`. |
| BE-PROG-01 (MEL-68) | Sở hữu bảng V4 (DB-01 tạo bảng và entity, BE-PROG-01 viết logic). Phục vụ `/me/matches`. Dùng `matchId` làm khóa chính để ghi lặp vẫn idempotent. Phạm vi file không đổi (`progression/**` đã gồm `persistence`). |

Ngoài danh sách của ticket, DB-02 (MEL-66) cần biết V6 không còn chứa bảng lịch sử; AC hiện tại của DB-02 ("V6 progression: thống kê, XP, huy hiệu") vẫn đúng.

## 4. Dữ liệu trong DB và dữ liệu chỉ ở bộ nhớ

| Dữ liệu | Ở đâu | Vì sao | Mất gì khi khởi động lại |
|---|---|---|---|
| Người dùng, phiên (refresh token), kho nhạc, bộ đề | DB | Bền, dùng chung | Không mất |
| Kết quả trận đã kết thúc, thống kê, huy hiệu, bảng xếp hạng | DB | Ghi khi nhận `MatchFinished` | Không mất |
| Thử thách ngày: bài của ngày, lượt chơi | DB | Mỗi người một lượt mỗi ngày phải sống qua restart | Không mất |
| Báo cáo chat, từ cấm, nhật ký admin, số liệu sử dụng | DB | Truy vết, kiểm duyệt | Không mất |
| Phòng: thành viên, chủ phòng, sẵn sàng, đếm ngược ghép trận, hàng đợi lệnh | Bộ nhớ (`RoomActor`, ADR-007) | Sống vài phút, cần đọc ghi nhanh | Phòng đang chơi bị hủy (SRS 2.2, NFR-15) |
| Vòng đang chơi: mức đã mở, đồng hồ, điểm tạm, token âm thanh đã cấp | Bộ nhớ | Server là trọng tài, giá trị đổi từng giây | Trận đang chạy bị hủy |
| Phiên chơi đơn đang dở (giữ 30 phút, BR-23) | Bộ nhớ | Giống phòng; chỉ ghi DB khi kết thúc hoặc bỏ dở | Phiên mất, không ghi DB; người chơi bắt đầu lại |
| Tin chat bình thường | Bộ nhớ, không lưu | Quyền riêng tư, MVP không cần lịch sử chat | Mất |
| URL preview của nhà cung cấp, cache từ cấm, bộ đếm giới hạn tần suất | Bộ nhớ | ADR-003 cấm lưu URL; cache có TTL | Nạp lại |

Ghi chú: phiên chơi đơn bị mất khi restart không được ghi `ABANDONED`; chấp nhận ở MVP vì cùng lý do với phòng (ADR-007).

## 5. Tham chiếu giữa các module

Cột tham chiếu sang bảng của module khác (VD `match_player.user_id`, `round.track_id`, `daily_attempt.user_id`) là **uuid thường, không có khóa ngoại trong DB**, nhưng có index. Lý do:
1. Modulith cấm module này đọc bảng module kia; khóa ngoại kéo theo phụ thuộc thứ tự migration và buộc xóa dây chuyền.
2. Xóa tài khoản và xóa khách sau 30 ngày (BR-20, BR-21) do identity thực hiện rồi phát `AccountDeleted`; các module khác ẩn danh hoặc xóa phần của mình qua listener. Khóa ngoại sẽ chặn bước này.
3. Tính toàn vẹn do ứng dụng giữ: id lấy từ sự kiện hoặc từ `api/` của module sở hữu.

Khóa ngoại thật chỉ có trong cùng module (đường liền ở ERD).

## 6. Quy tắc nghiệp vụ bản 22/10 trong dữ liệu

| BR | Dữ liệu |
|---|---|
| BR-23 | `match.type` (`SOLO`, `PRIVATE_ROOM`, `PUBLIC_ROOM`, `QUICK_MATCH`), `match.ranked`, `match.status` (`ABANDONED` cho chơi đơn quá 30 phút: lưu vòng đã xong, vòng dở 0 điểm, không xếp hạng) |
| BR-07, BR-08 | `round_result.base_score`, `combo_percent`, `speed_bonus`, `round_score`: đủ để hiện chi tiết điểm |
| BR-10 | `match_player.correct_time_ms` (tổng thời gian trả lời đúng, mili giây theo đồng hồ server) và `rank` |
| BR-20, BR-21 | `app_user.kind`; `match_player.is_guest`, `player_stats.is_guest`, `daily_attempt.is_guest`. Khách vẫn có dòng dữ liệu; `counts_for_leaderboard` bằng sai cho tới khi gộp. Khi gộp: identity đặt `merged_into_id`, progression và daily đổi `user_id` từ khách sang tài khoản rồi tính lại (idempotent). Cùng ngày thử thách ngày thì giữ lượt của tài khoản, xóa lượt của khách |
| BR-24 | Bảng tuần: tổng `total_score` của `match_player` có `counts_for_leaderboard` trong tuần, bằng điểm thì `max(ended_at)` nhỏ hơn xếp trên. Toàn thời gian: `player_stats.ranked_score`, `ranked_reached_at` |
| BR-25 | `daily_attempt`: `started_at` do server cấp, `correct_level`, `elapsed_ms`; trần 5 phút; ngày thuộc ngày server cấp bài |
| BR-28 | `app_user.status`, `lock_reason`; khách bị khóa theo id; mọi thao tác ghi `audit_log` |
| BR-21 (xóa tài khoản) | Identity xóa dòng `app_user`. Progression: xóa `player_stats`, `user_achievement`; ẩn danh `match_player` (`user_id` = null, biệt danh "ẩn danh"). Daily: ẩn danh `daily_attempt` tương tự. BE-ID-03 viết listener |

## 7. Điểm còn mở

- **Hạn giữ phiên chơi đơn:** BE-SOLO-01 ghi "hết hạn sau 2 giờ", BR-23 ghi 30 phút rồi chuyển `ABANDONED`. Đề xuất theo BR-23 (BR là nguồn sự thật): sửa AC của BE-SOLO-01. `[HỎI THÁI]`
- **Danh sách từ cấm khởi tạo** (V5): nội dung do Thái cung cấp hoặc duyệt; tài liệu này chỉ quy định cấu trúc. `[HỎI THÁI]`
- **Thời hạn giữ `analytics_event`:** chưa có chính sách; để DB-02 chốt cùng Thái. `[HỎI THÁI]`
- **`cover_url`:** không có trong thiết kế. Nếu giao diện cần ảnh bìa, thêm khi LEG-02 (MEL-120) làm rõ quyền dùng.

## 8. Câu hỏi bảo vệ

1. **Vì sao lịch sử trận thuộc progression mà không phải game?** Progression đã nghe `MatchFinished` để tính thống kê và bảng xếp hạng; để nó ghi luôn lịch sử thì gộp khách và xóa tài khoản chỉ sửa một module. Game chỉ giữ luật và phiên đang chơi trong bộ nhớ nên test được không cần DB.
2. **Vì sao không có khóa ngoại giữa các module?** Mỗi module sở hữu bảng của mình; khóa ngoại chéo module phá ranh giới Modulith và chặn việc xóa tài khoản. Toàn vẹn do sự kiện và API giữ.
3. **Vì sao không lưu từng lần đoán?** Không tính năng nào cần; kết quả vòng (điểm cơ bản, combo, thưởng, số lần sai) đủ để hiện chi tiết điểm. Lưu ít hơn thì ghi nhanh hơn và ít dữ liệu cá nhân hơn.
4. **Khách đăng nhập thì dữ liệu đi đâu?** Identity đánh dấu khách đã gộp và phát `GuestMerged`; progression và daily đổi chủ dữ liệu, chạy lại được nhiều lần mà không nhân đôi.
5. **Server khởi động lại thì mất gì?** Phòng và phiên đang chơi; mọi thứ đã ghi DB (trận xong, thống kê, lượt thử thách ngày) còn nguyên.
