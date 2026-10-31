# Thiết kế Lược đồ lớp (Class Diagrams) - DOC-03 (MEL-51)

Thư mục này chứa các sơ đồ lớp thiết kế chi tiết cho 4 module cốt lõi của backend Meloo theo kiến trúc Spring Modulith, đối chiếu mã nguồn tại commit `c0f723e`.

## Danh mục sơ đồ
1. **`identity.puml` / `identity.png`**: Module Quản lý danh tính & Xác thực
   - **Tầng Web**: `IdentityController` hiện thực giao diện `IdentityApi` sinh từ OpenAPI.
   - **Tầng Dịch vụ**:
     - `GuestAuthService`: Xử lý tạo phiên khách, tích hợp `NicknameValidator` (BR-22) và `GuestRateLimiter`.
     - `JwtTokenService` & `RefreshTokenService`: Cấp phát JWT Access Token và quản lý vòng đời Refresh Token theo cơ chế xoay vòng (Token Rotation) nhằm bảo mật phiên.
     - `AccountLockQueryImpl`: Cung cấp API nội bộ cho các module khác kiểm tra trạng thái khóa tài khoản (`AccountLockQuery`).
   - **Tầng Dữ liệu**: Thực thể JPA `AppUser`, `AuthIdentity` (Google/Facebook OAuth2), `RefreshToken` và các Enum (`UserKind`, `UserRole`, `UserStatus`).

2. **`catalog.puml` / `catalog.png`**: Module Kho nhạc & Tìm kiếm bài hát
   - **Tầng Web**: `CatalogController` cung cấp API tìm kiếm bài hát theo gợi ý (`/api/v1/tracks/search`) và danh sách playlist.
   - **Tầng Dịch vụ & Domain**:
     - `CatalogService` (interface): Cung cấp cho `game` và `room` lấy danh sách bài hát ngẫu nhiên theo playlist hoặc bộ lọc (BR-09, BR-27).
     - `TrackSearchService`: Tìm kiếm gợi ý bài hát theo từ khóa sau khi chuẩn hóa bỏ dấu qua `TitleNormalizer`.
     - `MusicProvider` (interface): Trừu tượng hóa nguồn nhạc bên thứ ba. `DeezerMusicProvider` (nguồn chính) và `ItunesMusicProvider` (dự phòng), được bao gói bởi `CachedMusicProvider` để giảm tải API.
   - **Tầng Dữ liệu**: Thực thể `Track`, `Artist`, `Genre`, `Playlist`, `PlaylistTrack` khớp ERD cơ sở dữ liệu.

3. **`game.puml` / `game.png`**: Module Luật chơi, Tính điểm & Chơi đơn
   - **Tầng Web**: `SoloGameController` quản lý REST API chơi đơn (`/api/v1/solo/matches/*`).
   - **Domain Cốt lõi**:
     - `Round`: Thực thể aggregate POJO độc lập với framework, điều phối máy trạng thái của một vòng chơi (WAITING → IN_PROGRESS → CORRECT / WRONG_OUT / GAVE_UP / TIMEOUT).
     - `ScoreCalculator`: Tính điểm cơ bản (1.000 / 800 / 600 / 400 / 200 điểm theo BR-02), thưởng tốc độ (BR-07).
     - `ComboTracker`: Quản lý chuỗi đúng liên tiếp (streak); đúng ở mức ≤ 5s tăng chuỗi (+10% từ vòng 2, +20% từ vòng 3, BR-08).
     - `TargetTrack`, `GameRules`, `GuessEvaluation`, `RoundScoreDetail`: Các Value Object và Record phục vụ chấm điểm.
   - **Quản lý phiên chơi đơn**: `SoloMatchManager` và `SoloMatch` duy trì trạng thái in-memory với thời gian chờ 30 phút (BR-23).
   - **Sự kiện miền**: Phát `MatchStarted`, `MatchFinished` cho module `progression`.

4. **`room.puml` / `room.png`**: Module Phòng chơi & Realtime STOMP
   - **Hàng đợi tuần tự (Actor Model)**:
     - `RoomActor`: Mỗi phòng được quản lý bởi một `RoomActor` riêng biệt chạy trên luồng ảo (Virtual Thread), đảm bảo mọi lệnh gửi đến phòng đều xử lý tuần tự (ADR-007), loại trừ hoàn toàn race condition khi nhiều người chơi cùng đoán bài.
     - `RoomRegistry`: Quản lý vòng đời `RoomRuntime` và ánh xạ phiên WebSocket.
   - **Giao tiếp Realtime**:
     - `RoomEventPublisher`: Phát sự kiện ra các STOMP topic `/topic/rooms/{roomId}/*` (lobby, round, scoreboard) và hàng đợi riêng `/user/queue/*`.
   - **Thông điệp (AsyncAPI 3)**:
     - Commands (Client gửi): `ReadyCommand`, `StartCommand`, `LeaveCommand`, `KickCommand`, `SettingsCommand`, `ChatCommand`, `ListenMoreCommand`, `GuessCommand`, `GiveUpCommand`...
     - Events (Server phát): `RoundStartEvent`, `RoundEndEvent`, `PlayerGuessedEvent`, `ScoreboardUpdateEvent`, `MatchCountdownEvent`...

5. **`domain.puml` / `domain.png`**: Sơ đồ lớp miền tổng quát toàn hệ thống (DES-01).

## Hướng dẫn giải thích khi bảo vệ đồ án
- **Vì sao tách `Round` thành aggregate độc lập không phụ thuộc Spring/JPA?**
  *Trả lời:* Để domain logic và luật chơi (BR-01..BR-08) trở thành Single Source of Truth thuần khiết, dễ viết unit test độc lập, chạy cực nhanh và có thể tái sử dụng cho cả chế độ chơi đơn lẫn chơi nhiều người realtime.
- **Cơ chế chống race condition khi nhiều người đoán bài cùng một mili giây trong phòng?**
  *Trả lời:* Mỗi phòng có một `RoomActor` sở hữu hàng đợi lệnh `SingleThreadExecutor`. Mọi tương tác của người chơi (đoán bài, nghe thêm, sẵn sàng) đều phải đi qua hàng đợi này để xử lý tuần tự, server phân định thứ tự nhận lệnh chính xác và công bằng theo đồng hồ server.
