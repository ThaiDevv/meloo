# Spring Modulith: API, sự kiện, listener

## Sự kiện miền
- Sự kiện là record trong `com.meloo.<module>.events` của module PHÁT ra. Package này có `package-info.java` gắn `@org.springframework.modulith.NamedInterface("events")`.
- Không đặt sự kiện vào `shared`: nếu đặt ở đó, ticket nào phát sự kiện cũng phải sửa `shared` và phải tranh khóa `hot-shared`.
- Phát bằng `ApplicationEventPublisher.publishEvent(...)` bên trong transaction của use case.
- Nghe bằng `@ApplicationModuleListener` (chạy sau commit, transaction riêng, bất đồng bộ). Bật Event Publication Registry để sự kiện không mất khi có lỗi, theo cấu hình ở SET-03.
- Listener phải idempotent. Sự kiện mang `eventId` (UUID); phía nhận dùng unique constraint theo `eventId`, hoặc kiểm tra trước khi cộng điểm.

| Sự kiện | Module sở hữu | Ticket định nghĩa / phát | Ai nghe |
|---|---|---|---|
| `GuestCreated` | identity | BE-ID-01 | admin (analytics) |
| `UserRegistered`, `GuestMerged` | identity | BE-ID-02 | admin, progression |
| `MatchStarted`, `MatchFinished` | game | Định nghĩa ở BE-GAME-01; chơi đơn phát ở BE-SOLO-01, phòng phát ở BE-ROOM-02 | progression, admin |
| `DailyAttemptFinished` | daily | BE-DAILY-01 | progression (huy hiệu, BE-PROG-02), admin (BE-ADM-02) |
| `AchievementUnlocked` | progression | BE-PROG-02 | frontend đọc qua `/me/achievements` |
| `AccountDeleted` | identity | BE-ID-03 (cả job xóa khách 30 ngày, BR-20) | progression, chat, admin: listener nằm trong package `account/` của từng module, do BE-ID-03 viết |

`MatchFinished` chứa: matchId, loại trận (SOLO, PRIVATE_ROOM, PUBLIC_ROOM; thử thách ngày dùng `DailyAttemptFinished`), có tính xếp hạng không, danh sách người chơi kèm điểm, thứ hạng, mức đoán đúng từng vòng, thời điểm kết thúc.

`GuestMerged(guestId, userId)`: progression chuyển lịch sử và thống kê của khách sang tài khoản (BE-PROG-01). Identity không sửa bảng của module khác.

## API giữa các module
| API | Module | Ticket | Dùng bởi |
|---|---|---|---|
| Chọn N bài ngẫu nhiên hợp lệ theo bộ lọc hoặc bộ đề (không trùng); lấy thông tin bài | catalog `api/` | BE-CAT-03 | game, room, daily |
| Quản trị kho nhạc: bật/tắt bài, sửa thể loại/quốc gia, CRUD bộ đề, nhập theo truy vấn | catalog `api/` | BE-CAT-04 | admin |
| Cấp audio token cho (vòng, người chơi, mức) | audio `api/` | BE-AUD-01 | game, room, daily |
| Engine luật chơi | game `domain/` (NamedInterface "engine") | BE-GAME-01 | room, daily |
| Tra vòng đang chơi của phòng (`ActiveRoundQuery`) | room `api/` | BE-RT-01 | chat |
| Thông tin phòng: tồn tại, thành viên, riêng hay công khai (`RoomDirectory`) | room `api/` | BE-RT-01 | chat, voice |
| Quản trị người dùng: tìm, khóa/mở (thu hồi phiên ngay), đổi vai trò | identity `api/` | BE-ID-02 | admin |
| Quản trị từ cấm (cache TTL ≤ 5 phút); đọc và xử lý báo cáo chat | chat `api/` | BE-CHAT-01 | admin |
| Đặt lịch bài cho thử thách các ngày tới | daily `api/` | BE-DAILY-01 | admin |

Thiếu API khi đang làm ticket thì ghi vào "Ticket bổ sung" trong PR, không tự sửa module khác.
