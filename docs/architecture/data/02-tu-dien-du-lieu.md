# Từ điển dữ liệu Meloo (DES-01)

> Ticket: MEL-30 · Bản nháp chờ Thái duyệt. Sơ đồ: [`erd.puml`](erd.puml). Quy ước chung theo `.claude/skills/meloo-backend/references/migrations.md`.

**Quy ước chung.** Tên bảng, cột `snake_case` tiếng Anh. Khóa chính `uuid` (sinh ở ứng dụng, UUIDv7 nếu thư viện có) trừ khi ghi khác; bảng nhật ký dùng `bigint generated always as identity`. Mọi bảng có `created_at timestamptz not null default now()` (không lặp lại ở dưới; bảng có khóa tự nhiên vẫn có). Chuỗi enum lưu `varchar` kèm `check (… in (…))`, không dùng kiểu enum của PostgreSQL để dễ thêm giá trị bằng migration. **FK** là khóa ngoại thật (cùng module); **ref** là tham chiếu logic sang module khác, chỉ có index (tài liệu 01 mục 5).

## identity (V2)

### `app_user`: người chơi (khách hoặc đã đăng nhập), quản trị viên
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| kind | varchar(12) | not null, check `GUEST`/`REGISTERED` | BR-20 |
| role | varchar(8) | not null default `USER`, check `USER`/`ADMIN` | ADMIN đầu tiên theo `meloo.admin.emails` |
| status | varchar(8) | not null default `ACTIVE`, check `ACTIVE`/`LOCKED` | BR-28; khách bị khóa theo id |
| nickname | varchar(20) | not null, `char_length` 3–20 | BR-22; từ cấm kiểm ở ứng dụng |
| email | varchar(254) | unique (không phân biệt hoa thường, lưu chữ thường), null với khách | liên kết nhà cung cấp theo email đã xác minh (ADR-005) |
| lock_reason | text | null; bắt buộc khi `LOCKED` (check) | BR-28 |
| locked_at | timestamptz | null | |
| merged_into_id | uuid | FK `app_user(id)`, null | đặt khi khách đã gộp (BR-21) |
| last_active_at | timestamptz | not null default now() | xóa khách sau 30 ngày không hoạt động (BR-20) |

Index: `(kind, last_active_at)` cho job xóa khách; `(merged_into_id)` where not null; unique `email`.

### `auth_identity`: liên kết nhà cung cấp OAuth
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| user_id | uuid | not null, FK `app_user(id)` on delete cascade | |
| provider | varchar(10) | not null, check `GOOGLE`/`FACEBOOK` | |
| provider_subject | varchar(128) | not null | id người dùng phía nhà cung cấp |

Unique `(provider, provider_subject)`; index `(user_id)`. Khách không có dòng ở bảng này.

### `refresh_token`: phiên đăng nhập (ADR-005)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| user_id | uuid | not null, FK `app_user(id)` on delete cascade | |
| family_id | uuid | not null | dùng lại token cũ thì thu hồi cả family |
| token_hash | char(64) | not null, unique | SHA-256 hex; không lưu token gốc |
| expires_at | timestamptz | not null | 30 ngày |
| revoked_at | timestamptz | null | |
| replaced_by_id | uuid | FK `refresh_token(id)`, null | token xoay vòng |

Index: `(user_id)`, `(family_id)`.

## catalog (V3)
Extension `pg_trgm`, `unaccent`. Cột `*_normalized` là chuỗi đã chuẩn hóa theo BR-05 (bỏ dấu, chữ thường, bỏ phần trong ngoặc và phần ghi phiên bản); cùng một hàm chuẩn hóa dùng để chấm điểm, tìm kiếm và chặn chat.

### `artist`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| name | varchar(200) | not null | |
| name_normalized | varchar(200) | not null | GIN `gin_trgm_ops` |
| provider | varchar(16) | not null | `DEEZER`, `ITUNES` (ADR-003) |
| provider_artist_id | varchar(64) | not null | |

Unique `(provider, provider_artist_id)`.

### `genre`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| code | varchar(40) | not null, unique | VD `vpop`, `kpop` |
| name_vi, name_en | varchar(80) | not null | hiển thị i18n |

### `track`: bản thu dùng làm đáp án
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| provider | varchar(16) | not null | |
| provider_track_id | varchar(64) | not null | không lưu URL preview hay audio (ADR-003) |
| title | varchar(300) | not null | |
| title_normalized | varchar(300) | not null | GIN `gin_trgm_ops`; BR-05 |
| primary_artist_id | uuid | not null, FK `artist(id)` | ca sĩ chính (BR-06) |
| genre_id | uuid | FK `genre(id)`, null | |
| country_code | char(2) | null | bộ lọc quốc gia |
| release_year | smallint | null | thập niên = `release_year / 10 * 10`, tính khi truy vấn |
| popularity_rank | int | null | tự chọn bài thử thách ngày khi admin chưa đặt |
| enabled | boolean | not null default true | BR-27 |
| disabled_reason | varchar(200) | null | |

Unique `(provider, provider_track_id)`. Index: `(primary_artist_id)`, `(genre_id)`, `(country_code)`, `(release_year)`, `(enabled)`. Bài không có preview ở nhà cung cấp không được nhập (ADR-003), nên không có cột `preview_available`.

### `playlist`: bộ nhạc / bộ đề
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| code | varchar(60) | not null, unique | |
| name_vi, name_en | varchar(120) | not null | |
| kind | varchar(10) | not null, check `STANDARD`/`CURATED` | `STANDARD` = bộ chuẩn của chơi đơn xếp hạng (BR-23) |
| enabled | boolean | not null default true | |

### `playlist_track`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| playlist_id | uuid | PK, FK `playlist(id)` on delete cascade | |
| track_id | uuid | PK, FK `track(id)` | |
| position | int | not null | |

Index `(track_id)`.

## chat (V5)

### `chat_report`: tin bị báo cáo (BR-17, BR-28)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| message_text | varchar(200) | not null | BR-16 |
| sender_id | uuid | not null, ref `app_user` | khách bị khóa theo id này (BR-28) |
| sender_nickname | varchar(20) | not null | chụp lại lúc báo |
| reporter_id | uuid | not null, ref `app_user` | |
| context | jsonb | not null default `[]` | 5 tin trước đó |
| status | varchar(10) | not null default `OPEN`, check `OPEN`/`DISMISSED`/`ACTIONED` | |
| handled_by | uuid | null, ref `app_user` | |
| handled_at | timestamptz | null | |

Index `(status, created_at)`, `(sender_id)`.

### `banned_word`: từ cấm (BR-17, BR-22)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| word | varchar(80) | not null, unique | đã chuẩn hóa như BR-05 |
| created_by | uuid | null, ref `app_user` | null với dòng khởi tạo trong migration |

## progression: lịch sử trận (V4)

### `match`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | chính là `matchId` của sự kiện, nên ghi lặp thì trùng khóa (idempotent) |
| type | varchar(16) | not null, check `SOLO`/`PRIVATE_ROOM`/`PUBLIC_ROOM`/`QUICK_MATCH` | BR-23 |
| ranked | boolean | not null | BR-23 |
| status | varchar(10) | not null, check `FINISHED`/`ABANDONED` | `ABANDONED`: chơi đơn quá 30 phút |
| round_count | smallint | not null, check 5/10/15 | BR-09 |
| playlist_id | uuid | null, ref `playlist` | |
| filter | jsonb | null | bộ lọc đã dùng (thể loại, quốc gia, thập niên, ca sĩ) |
| started_at, ended_at | timestamptz | not null | |

Index `(ended_at)`, `(type, ended_at)`.

### `match_player`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| match_id | uuid | not null, FK `match(id)` on delete cascade | |
| user_id | uuid | null, ref `app_user` | null sau khi ẩn danh (BR-21) |
| nickname | varchar(20) | not null | chụp lại; ẩn danh thì thay bằng giá trị cố định |
| is_guest | boolean | not null | |
| total_score | int | not null | |
| rank | smallint | not null | BR-10 |
| correct_count | smallint | not null | |
| correct_time_ms | bigint | not null | tổng thời gian trả lời đúng, phân hòa (BR-10) |
| counts_for_leaderboard | boolean | not null | = `match.ranked` và không phải khách; đổi thành đúng khi gộp khách (BR-20, BR-21) |
| ended_at | timestamptz | not null | sao chép từ `match` để index bảng xếp hạng |

Unique `(match_id, user_id)`. Index: `(user_id, ended_at desc)` cho `/me/matches`; partial `(ended_at, user_id) include (total_score) where counts_for_leaderboard` cho bảng tuần.

### `round`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| match_id | uuid | not null, FK `match(id)` on delete cascade | |
| round_no | smallint | not null | |
| track_id | uuid | not null, ref `track` | |
| track_title, artist_name | varchar | not null | chụp lại để lịch sử không đổi khi bài bị sửa hoặc tắt |

Unique `(match_id, round_no)`.

### `round_result`: kết quả một người ở một vòng
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| round_id | uuid | not null, FK `round(id)` on delete cascade | |
| match_player_id | uuid | not null, FK `match_player(id)` on delete cascade | |
| outcome | varchar(10) | not null, check `CORRECT`/`WRONG_OUT`/`GAVE_UP`/`TIMEOUT`/`MISSED` | `MISSED`: lỡ vòng do mất kết nối (BR-14) |
| correct_level | smallint | null, check 1–5 | mức nghe đoán đúng (2s=1 … 30s=5); null nếu không đúng |
| base_score | int | not null | BR-02 |
| combo_percent | smallint | not null default 0, check 0/10/20 | BR-08 |
| speed_bonus | int | not null default 0 | BR-07; 0 ở chơi đơn |
| round_score | int | not null | = `base_score` × (1 + `combo_percent`/100) + `speed_bonus` |
| wrong_count | smallint | not null default 0 | số lần đoán sai |
| answer_time_ms | int | null | từ lúc server bắt đầu vòng đến lúc nhận lượt đoán đúng |

Unique `(round_id, match_player_id)`; index `(match_player_id)`.

## progression: tiến trình (V6)

### `player_stats`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| user_id | uuid | PK, ref `app_user` | một dòng cho mỗi người, kể cả khách |
| is_guest | boolean | not null | |
| xp | int | not null default 0 | |
| matches_played, matches_won, rounds_correct | int | not null default 0 | |
| best_combo | smallint | not null default 0 | |
| ranked_score | bigint | not null default 0 | bảng toàn thời gian (BR-24) |
| ranked_reached_at | timestamptz | null | thời điểm đạt `ranked_score` hiện tại; bằng điểm thì ai đạt trước xếp trên |

Index: `(ranked_score desc, ranked_reached_at) where not is_guest`.

### `achievement`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| code | varchar(60) | PK | điều kiện mở khóa nằm trong code (BE-PROG-02), không trong DB |
| sort_order | smallint | not null | tên và mô tả lấy từ file i18n theo `code` |

### `user_achievement`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| user_id | uuid | PK, ref `app_user` | |
| achievement_code | varchar(60) | PK, FK `achievement(code)` | |
| unlocked_at | timestamptz | not null | BR-26: không thu hồi |

## daily (V7)

### `daily_challenge`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| challenge_date | date | PK | ngày theo giờ Việt Nam (BR-25) |
| track_id | uuid | not null, ref `track` | |
| source | varchar(10) | not null, check `SCHEDULED`/`AUTO` | admin đặt hoặc tự chọn |
| set_by | uuid | null, ref `app_user` | |

### `daily_attempt`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | uuid | PK | |
| challenge_date | date | not null, FK `daily_challenge` | ngày server cấp bài, kể cả khi xong sau 00:00 |
| user_id | uuid | null, ref `app_user` | null sau khi ẩn danh |
| is_guest | boolean | not null | khách chơi được nhưng không lên bảng |
| status | varchar(11) | not null, check `IN_PROGRESS`/`SOLVED`/`UNSOLVED` | `UNSOLVED` gồm hết lượt và quá trần 5 phút |
| started_at | timestamptz | not null | server cấp |
| finished_at | timestamptz | null | |
| correct_level | smallint | null, check 1–5 | |
| elapsed_ms | int | null | từ `started_at` đến lúc đoán đúng |

Unique `(challenge_date, user_id)`: mỗi người một lượt mỗi ngày. Index `(challenge_date, status, correct_level, elapsed_ms)` cho bảng ngày; `(user_id, challenge_date desc)` cho chuỗi ngày.

## admin (V8, V9)

### `audit_log` (V8): nhật ký thao tác admin (BR-28)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | bigint | PK identity | |
| actor_id | uuid | not null, ref `app_user` | |
| action | varchar(40) | not null | VD `USER_LOCK`, `USER_UNLOCK`, `BANNED_WORD_ADD`, `TRACK_DISABLE` |
| target_type | varchar(30) | not null | |
| target_id | varchar(64) | null | |
| reason | text | null; bắt buộc với `USER_LOCK` ở ứng dụng | |
| detail | jsonb | null | |

Chỉ thêm, không sửa, không xóa (thu quyền `update`/`delete` của role ứng dụng). Index `(created_at desc)`, `(actor_id, created_at)`, `(target_type, target_id)`.

### `analytics_event` (V9): số liệu sử dụng cho dashboard (FR-16)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | bigint | PK identity | |
| event_type | varchar(40) | not null | `GUEST_CREATED`, `USER_REGISTERED`, `MATCH_STARTED`, `MATCH_FINISHED`, `DAILY_ATTEMPT_FINISHED` |
| user_id | uuid | null, ref `app_user` | |
| props | jsonb | null | VD loại trận |
| occurred_at | timestamptz | not null | |

Index `(event_type, occurred_at)`. Thời hạn giữ dữ liệu: xem tài liệu 01 mục 7.
