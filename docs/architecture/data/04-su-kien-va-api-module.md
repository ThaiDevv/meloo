# Sự kiện miền và API giữa các module (DES-01)

> Ticket: MEL-30 · Bản nháp chờ Thái duyệt. Đối chiếu với bảng trong `.claude/skills/meloo-backend/references/modulith-and-events.md`. Cột "Khác bảng harness" là việc cần cập nhật ở harness (ngoài phạm vi ticket này, ghi trong PR).

## 1. Sự kiện miền

Record nằm ở `com.meloo.<module>.events` của module phát; listener `@ApplicationModuleListener`, idempotent theo `eventId` hoặc khóa tự nhiên.

| Sự kiện | Module sở hữu | Ticket | Ai nghe | Khác bảng harness |
|---|---|---|---|---|
| `GuestCreated` | identity | BE-ID-01 | admin (`analytics_event`) | Không |
| `UserRegistered` | identity | BE-ID-02 | admin | Harness ghi thêm progression; chưa thấy nhu cầu (`player_stats` tạo khi có trận đầu), giữ lại nếu BE-PROG-01 cần |
| `GuestMerged(guestId, userId)` | identity | BE-ID-02 | progression (lịch sử, thống kê, điểm xếp hạng, huy hiệu), **daily** (lượt và chuỗi ngày) | Harness ghi "admin, progression"; thêm **daily** theo BE-ID-02 và BE-DAILY-01; admin không cần nghe |
| `MatchStarted` | game | BE-GAME-01 định nghĩa; BE-SOLO-01, BE-ROOM-02 phát | admin | Harness ghi "progression, admin"; progression chỉ cần `MatchFinished` |
| `MatchFinished` | game | như trên | progression (ghi lịch sử, thống kê, bảng), admin | Payload mở rộng, thêm `QUICK_MATCH` và `status` (mục 2) |
| `DailyAttemptFinished` | daily | BE-DAILY-01 | progression (huy hiệu, BE-PROG-02), admin | Không |
| `AchievementUnlocked` | progression | BE-PROG-02 | frontend đọc qua `/me/achievements` | Không |
| `AccountDeleted(userId)` | identity | BE-ID-03 (gồm job xóa khách 30 ngày, BR-20) | progression, **daily**, chat, admin; listener ở package `account/` của từng module | Harness thiếu **daily** (`daily_attempt` có `user_id`) |

## 2. Payload của `MatchFinished` (hợp đồng cho BE-SOLO-01, BE-ROOM-02, BE-PROG-01)

| Trường | Ghi chú |
|---|---|
| `eventId`, `matchId` | `matchId` là khóa chính của `match`, nên nhận lặp là bỏ qua |
| `type` | `SOLO`, `PRIVATE_ROOM`, `PUBLIC_ROOM`, `QUICK_MATCH` (harness hiện thiếu `QUICK_MATCH`; thử thách ngày dùng `DailyAttemptFinished`) |
| `ranked` | BR-23 |
| `status` | `FINISHED` hoặc `ABANDONED` (chơi đơn quá 30 phút; chỉ vòng đã xong) |
| `roundCount`, `playlistId`, `filter`, `startedAt`, `endedAt` | |
| `rounds[]` | `roundNo`, `trackId`, `trackTitle`, `artistName` |
| `players[]` | `userId`, `nickname`, `isGuest`, `totalScore`, `rank`, `correctCount`, `correctTimeMs`, và `results[]` theo vòng |
| `results[]` | `roundNo`, `outcome`, `correctLevel`, `baseScore`, `comboPercent`, `speedBonus`, `roundScore`, `wrongCount`, `answerTimeMs` |

Payload không chứa token âm thanh, URL preview hay bất kỳ dữ liệu nào lộ đáp án trước khi trận kết thúc: sự kiện chỉ phát sau khi trận xong.

## 3. API giữa các module (`api/`)

| API | Module | Ticket | Dùng bởi | Khác bảng harness |
|---|---|---|---|---|
| Chọn N bài hợp lệ theo bộ lọc hoặc bộ đề; lấy bài theo id | catalog | BE-CAT-03 | game, room, daily | Không |
| Quản trị kho nhạc | catalog | BE-CAT-04 | admin | Không |
| Cấp audio token | audio | BE-AUD-01 | game, room, daily | Không |
| Engine luật chơi | game `domain/` (NamedInterface "engine") | BE-GAME-01 | room, daily | Không |
| `ActiveRoundQuery` | room | BE-RT-01 | chat | Không |
| `RoomDirectory` | room | BE-RT-01 | chat, voice | Không |
| Quản trị người dùng | identity | BE-ID-02 | admin | Thêm: đếm người dùng theo loại và ngày tạo cho dashboard (đã có trong AC BE-ID-02) |
| Từ cấm, báo cáo chat | chat | BE-CHAT-01 | admin | Không |
| Lịch bài thử thách ngày | daily | BE-DAILY-01 | admin | Không |
| **Lịch sử trận, thống kê, bảng xếp hạng** (`/me/matches`, `/leaderboard`) | progression | BE-PROG-01 | web (REST); admin cần số trận và người chơi cho dashboard: dùng `analytics_event`, không đọc bảng progression | Mới: harness chưa ghi progression là chủ của lịch sử trận |

Không có API nào trả đáp án trước `ROUND_END`. Thiếu API khi làm ticket thì ghi vào "Ticket bổ sung" của PR, không tự sửa module khác.
