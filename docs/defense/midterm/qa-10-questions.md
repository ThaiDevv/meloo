# Bộ 10 Câu Hỏi Bảo Vệ Đồ Án Giữa Kỳ (DEF-00 / MEL-116)

> **Mục đích:** Chuẩn bị 10 câu hỏi trọng tâm giảng viên có thể hỏi trong buổi báo cáo giữa kỳ kèm câu trả lời ngắn gọn, chính xác, trích xuất trực tiếp từ mục **"Câu hỏi bảo vệ"** của các tài liệu nghiệp vụ, SRS, kiến trúc và thiết kế đã duyệt trong kho lưu trữ.

---

### Câu 1 · Thị trường & Đối thủ cạnh tranh (`BIZ-02`, `BIZ-04` / `BIZ-09`)
**Hỏi:** *"Đã có SongTrivia, SongPop, Kahoot! và Heardle rồi, tại sao còn làm Meloo? Và Heardle từng rất nổi tiếng mà vẫn phải đóng cửa, Meloo có lặp lại vết xe đổ đó không?"*
- **Trả lời:**
  - Qua chơi thử và kiểm chứng 6 đối thủ (`docs/business/02-competitors.md` mục 3–4): **SongTrivia** có phòng web realtime nhưng không có tiếng Việt, không có nhạc Việt và không có voice chat; **SongPop** chỉ có bản app, không có tiếng Việt/nhạc Việt, chọn trắc nghiệm 1/4 dễ đoán mò; **musci.io** có tiếng Việt nhưng chỉ chơi đơn 1 mình và toàn nhạc quốc tế; **Kahoot!** có tiếng Việt và có quiz nhạc Việt do cộng đồng tự tạo nhưng là quiz tĩnh soạn sẵn, không có cơ chế mở dần theo mức điểm và không có voice tích hợp. Meloo giải quyết đúng khoảng trống: **mở bằng một link trên web, kho nhạc V-pop chuẩn hóa có tìm kiếm không dấu (`BR-05`), nghe mở dần 5 mức (`BR-01`) và tích hợp sẵn phòng chơi nhóm + chat/voice (`BR-11, BR-18`)**.
  - Với **Heardle**, trò chơi đó chỉ có duy nhất 1 bài mỗi ngày chơi một mình nên khi hiệu ứng mới lạ qua đi thì lượt truy cập giảm (từ 69 triệu xuống 41 triệu/tháng) và Spotify đóng cửa để tập trung vào tính năng khám phá nhạc trong app (`01-problem.md` mục 9 câu 5). Meloo lấy **phòng chơi nhóm bạn bè** làm lõi lặp lại nhiều lần; thử thách ngày (`BR-25`) chỉ là tính năng phụ (`Should`) để giữ nhịp quay lại.
- **Nguồn:** [`docs/business/01-problem.md`](../../business/01-problem.md) (mục 9, câu 1 & 5); [`docs/business/02-competitors.md`](../../business/02-competitors.md) (mục 8, câu 1–4).

---

### Câu 2 · Kiểm chứng giả thuyết bài toán & Khảo sát (`BIZ-02`, `BIZ-05`)
**Hỏi:** *"Làm sao biết người dùng thật sự cần những điểm khác biệt này? Nếu kết quả khảo sát thực tế ngược với giả thuyết ban đầu thì em xử lý thế nào?"*
- **Trả lời:**
  - Nhóm không coi nhận định ban đầu là sự thật hiển nhiên mà phát biểu thành **4 giả thuyết `H1–H4`** kèm ngưỡng định lượng chốt trước ngày 19/10/2026 trước khi phát Google Form (`N \ge 50` mẫu hợp lệ, đóng form ngày 08/11/2026), sau đó kiểm chứng chéo lần hai bằng hành vi thực tế trong đợt Beta ($\ge 30$ tài khoản, $\ge 100$ trận, tỷ lệ quay lại $\ge 30\%$).
  - Nếu số liệu khảo sát bác bỏ một giả thuyết (ví dụ người chơi thích US-UK hơn V-pop ở `H2`, hoặc ít chơi hằng ngày ở `H3`), em giữ nguyên tính liêm chính học thuật: báo cáo đúng số liệu thật và điều chỉnh trọng tâm sản phẩm (đổi thứ tự bộ đề mặc định trên trang chủ, hoặc gắn thử thách ngày với cơ chế thi đua nhóm bạn).
- **Nguồn:** [`docs/business/01-problem.md`](../../business/01-problem.md) (mục 4 & mục 9, câu 2–3); [`docs/business/03-survey.md`](../../business/03-survey.md) (mục 6, câu 1 & 3).

---

### Câu 3 · Luật chơi mở dần, Combo & Chống cày điểm xếp hạng (`DOC-01` / `BR`)
**Hỏi:** *"Vì sao đoán sai cũng bị tụt mức điểm như bấm 'Nghe thêm'? Và tại sao Chơi đơn lẫn Phòng tự tạo đều không được tính điểm lên Bảng xếp hạng chính?"*
- **Trả lời:**
  - Theo **`BR-04`**, nếu đoán sai mà không mất gì thì người chơi sẽ chọn bừa liên tục trong ô gợi ý cho đến khi trúng. Việc mỗi lần đoán sai tự động mở mức nghe kế tiếp (làm giảm 200 điểm cơ bản tiềm năng theo `BR-02`: 1.000 $\rightarrow$ 800 $\rightarrow$ 600 $\rightarrow$ 400 $\rightarrow$ 200) buộc người chơi phải cân nhắc kỹ trước khi chốt đáp án.
  - Theo **`BR-23` và `BR-24`** (cập nhật ngày 01/11/2026 tại `MEL-149`), ở chế độ Chơi đơn và Phòng tự tạo, người chơi tự chọn được bộ nhạc và đối thủ nên rất dễ dùng tài khoản phụ hoặc học thuộc một bộ đề tủ để cày điểm. Do đó, **chỉ duy nhất chế độ Ghép trận nhanh (`BR-13`, dành cho người đã đăng nhập, hệ thống tự ghép người và chia bài)** mới được tính điểm rank trên Bảng xếp hạng chính toàn thời gian; còn Chơi đơn (chế độ luyện tập) và Phòng tự tạo vẫn được lưu lịch sử, thống kê và mở khóa huy hiệu cá nhân (`BR-26`).
- **Nguồn:** [`docs/srs/02-business-rules.md`](../../srs/02-business-rules.md) (mục 2.4, mục 3.3 & mục 7, câu 1–3).

---

### Câu 4 · Thiết kế Use Case & Phân quyền Khách vãng lai (`DOC-02`)
**Hỏi:** *"Vì sao trong mô hình Use Case, tác nhân 'Chủ phòng' lại kế thừa từ 'Khách' chứ không phải từ 'Người chơi đã đăng nhập', và vì sao tách riêng `UC-08` (Đoán bài hát trong vòng)?"*
- **Trả lời:**
  - Theo **`BR-20`**, giá trị cốt lõi số 1 của Meloo là vào chơi ngay bằng một đường link không cần đăng ký: Khách vãng lai tạo được phòng riêng và làm Chủ phòng để mời bạn bè. Chủ phòng là vai trò điều phối trong một phòng tự tạo (`BR-12`), không phải cấp bậc tài khoản; chỉ riêng Ghép trận nhanh tính rank (`UC-15`, `BR-13`) mới yêu cầu đăng nhập để chống clone phá trận.
  - **`UC-08` (Đoán bài hát trong vòng)** được tách riêng làm use case `<<include>>` vì cả 3 chế độ (Chơi đơn `UC-07`, Thử thách ngày `UC-10`, Trận nhiều người `UC-18`) đều dùng chung một quy trình mở 5 mức nghe, chuẩn hóa tên bài `BR-05` và chống lộ đáp án; tách riêng giúp đặc tả luật lõi đúng một lần và nhất quán tuyệt đối.
- **Nguồn:** [`docs/srs/usecases/00-tong-quan.md`](../../srs/usecases/00-tong-quan.md) (mục 6, câu 1–2); [`docs/srs/02-business-rules.md`](../../srs/02-business-rules.md) (mục 7, câu 5).

---

### Câu 5 · Lựa chọn kiến trúc Modular Monolith & Hợp đồng API-First (`ADR-001`, `ADR-002`, `DOC-04`)
**Hỏi:** *"Tại sao lại chọn kiến trúc Modular Monolith (Spring Modulith) thay vì Microservices, và làm sao đảm bảo Backend với Frontend không bị lệch dữ liệu khi nhiều AI agent cùng code?"*
- **Trả lời:**
  - Dự án do một sinh viên thực hiện trong 7 tuần và triển khai trên máy chủ **AWS EC2 `t3.micro` miễn phí chỉ có 1 GB RAM** (`ADR-006`). Chạy Microservices sẽ làm bùng nổ bộ nhớ cho nhiều tiến trình JVM và chi phí mạng nội bộ. **Spring Modulith (`ADR-001`)** giữ toàn bộ 11 module trong một tiến trình duy nhất nhưng kiểm soát ranh giới package nghiêm ngặt qua `api/` và `events/`, được CI kiểm chứng tự động bằng `ApplicationModules.verify()`.
  - Để chống lệch dữ liệu giữa Java và TypeScript, nhóm áp dụng **Contract-First (`ADR-002`)**: chốt hợp đồng REST (`contracts/openapi.yaml`) và WebSocket STOMP (`contracts/asyncapi.yaml`) trước, sinh tự động interface/DTO cho cả hai phía và đối chiếu bằng quy trình QA khớp nối.
- **Nguồn:** [`docs/architecture/adr/ADR-001-modular-monolith.md`](../../architecture/adr/ADR-001-modular-monolith.md) (mục Câu hỏi bảo vệ); [`docs/architecture/adr/ADR-002-api-first.md`](../../architecture/adr/ADR-002-api-first.md); [`docs/architecture/01-architecture.md`](../../architecture/01-architecture.md) (mục 6, câu 1).

---

### Câu 6 · Chống gian lận mở DevTools xem đáp án & Bảo mật Audio (`ADR-008`, `NFR-07`)
**Hỏi:** *"Làm sao ngăn chặn người chơi mở tab Network trong trình duyệt (DevTools) để xem trước tên bài hát hoặc tải cả đoạn 30 giây về nghe trước khi mới ở mức 2 giây?"*
- **Trả lời:**
  - Hệ thống tuân thủ nguyên tắc **Zero-Knowledge Client (`NFR-07`, `ADR-008`)**: trước khi vòng kết thúc (`ROUND_END`), không có bất kỳ response REST hay sự kiện STOMP nào chứa `trackId`, tên bài hát, ca sĩ hay URL gốc của CDN.
  - Client chỉ nhận một đường dẫn mờ `/api/v1/audio/{token}` với `token` là mã băm **HMAC-SHA256** ký trên bộ tứ `(roundId, playerId, level, expiresAt)`, có thời hạn sống ngắn ($\le 2$ phút) và được kiểm tra bằng `MessageDigest.isEqual` chống timing attack.
  - Backend đóng vai trò **Streaming Proxy** cắt luồng byte trực tiếp theo bitrate CBR 128 kbps (~16 KB/giây: mức 2s chỉ truyền tối đa ~32 KB rồi đóng luồng) và từ chối header `Range` vượt quá giới hạn mức đã mở. Do đó người chơi không thể thấy tên bài cũng không thể tua nghe trước.
- **Nguồn:** [`docs/architecture/adr/ADR-008-chong-gian-lan-audio.md`](../../architecture/adr/ADR-008-chong-gian-lan-audio.md) (mục Câu hỏi bảo vệ, câu 1–5); [`docs/architecture/01-architecture.md`](../../architecture/01-architecture.md) (mục 6, câu 2).

---

### Câu 7 · Xử lý đồng thời (Race Condition) & Rớt mạng trong phòng realtime (`ADR-007`, `BR-07`, `BR-14`)
**Hỏi:** *"Nếu trong phòng 5 người, có 2 người bấm đoán cùng một mili-giây hoặc đoán đúng ngay sát mốc hết giờ 45 giây thì server phân xử thế nào để không bị race condition? Nếu rớt mạng giữa trận thì sao?"*
- **Trả lời:**
  - Theo **`ADR-007`**, trạng thái mỗi phòng được giữ trong bộ nhớ và điều phối bởi một **`RoomActor`** riêng biệt với hàng đợi lệnh tuần tự đơn luồng (`SingleThreadExecutor`). Mọi sự kiện (đoán bài, nghe thêm, hết giờ) đều là lệnh xếp vào hàng đợi này và xử lý tuần tự từng lệnh một, loại bỏ hoàn toàn khóa DB và race condition (`NFR-08`).
  - Theo **`BR-07`**, khi lượt đoán và sự kiện hết giờ đến sát nhau, server phân xử khách quan dựa trên **thời điểm server nhận lượt đoán so với mốc cố định `45.000 ms`** (nhận trước `45.000 ms` thì được chấm, từ `45.000 ms` trở đi thì từ chối), không phụ thuộc vào độ trễ lập lịch của bộ hẹn giờ.
  - Khi một người mất kết nối (**`BR-14`**), trận đấu **không dừng lại chờ**; người đó được giữ chỗ **30 giây** (`Reconnect grace`) để vào lại chơi tiếp vòng hiện tại (vòng bị lỡ tính 0 điểm). Quá 30 giây thì coi như rời phòng và xếp hạng chót nếu là trận xếp hạng.
- **Nguồn:** [`docs/architecture/adr/ADR-007-trang-thai-phong.md`](../../architecture/adr/ADR-007-trang-thai-phong.md) (mục Câu hỏi bảo vệ, câu 1–4); [`docs/srs/02-business-rules.md`](../../srs/02-business-rules.md) (mục 2.2 & mục 7, câu 4).

---

### Câu 8 · Thiết kế Cơ sở dữ liệu & Ranh giới Module (`DES-01`)
**Hỏi:** *"Tại sao bảng lưu lịch sử trận đấu (`match`, `round`, `round_result`) lại thuộc module `progression` mà không nằm ở module `game`? Và tại sao các bảng giữa các module khác nhau không đặt khóa ngoại (Foreign Key)?"*
- **Trả lời:**
  - Module `game` chỉ giữ engine luật chơi thuần túy (`Round`, `ScoreCalculator`) và phiên chơi đơn đang chạy trong bộ nhớ nên unit test chạy cực nhanh mà không cần CSDL. Trong khi đó, cả `game` (chơi đơn) và `room` (nhiều người) đều phát sự kiện `MatchFinished` khi hết trận, và `progression` vốn đã lắng nghe sự kiện này để tính thống kê, điểm rank và huy hiệu. Giao `progression` sở hữu luôn các bảng lịch sử trận giúp gom toàn bộ việc lưu trữ, gộp tài khoản khách (`BR-21`) và ẩn danh khi xóa tài khoản vào đúng một module duy nhất.
  - Các cột tham chiếu chéo module (như `match_player.user_id`, `round.track_id`) chỉ lưu `uuid` mà **không đặt khóa ngoại DB** để bảo toàn tính độc lập của Spring Modulith, tránh phụ thuộc cứng thứ tự migration và cho phép xóa/ẩn danh tài khoản bất đồng bộ qua sự kiện `AccountDeleted`. Khóa ngoại vật lý chỉ đặt giữa các bảng trong cùng một module.
- **Nguồn:** [`docs/architecture/data/01-mo-hinh-du-lieu.md`](../../architecture/data/01-mo-hinh-du-lieu.md) (mục 3, mục 5 & mục 8, câu 1–5).

---

### Câu 9 · Bản quyền âm thanh & Phương án dự phòng nguồn nhạc (`ADR-003`, `LEG-02`, `BR-27`, `BR-29`)
**Hỏi:** *"Vấn đề bản quyền nhạc được xử lý thế nào? Tại sao chọn Deezer thay vì iTunes, và nếu Deezer chặn truy cập giữa chừng thì hệ thống có sập không?"*
- **Trả lời:**
  - Về kỹ thuật, hệ thống chỉ stream đoạn preview 30 giây công khai từ API, không phát cả bài, không lưu file MP3 hay URL có chữ ký xuống ổ cứng/CSDL (`ADR-003, ADR-008`), và sau mỗi vòng đều hiển thị nguồn nhạc kèm link nghe bản đầy đủ trên nền tảng gốc (`BR-29`).
  - Nhóm không chọn **iTunes** vì điều khoản Apple cấm dùng preview làm giá trị giải trí độc lập và định dạng AAC 268 kbps tốn gấp đôi băng thông so với MP3 128 kbps của Deezer (`SPK-01`). Với **Deezer**, thẩm định pháp lý `LEG-02` (`MEL-120`) ghi nhận minh bạch rằng Deezer hiện không nhận đăng ký app API mới và chưa xác nhận cách dùng của Meloo ("Chưa được phép"); Product Owner quyết định tự chấp nhận rủi ro cho phạm vi đồ án phi thương mại (ngoại lệ ngày 28/10/2026).
  - Nếu Deezer chặn truy cập hoặc bài hát lỗi preview giữa trận: (1) ở mức vòng chơi, server tự động đổi bài khác mà không trừ điểm ai (`BR-27`); (2) ở mức kiến trúc, nguồn nhạc được trừu tượng hóa qua interface `MusicProvider`, sẵn sàng chuyển ngay sang `FakeMusicProvider` (âm thanh tổng hợp không bản quyền) hoặc kho nhạc Creative Commons (Jamendo API) mà không phải sửa engine game hay cơ chế proxy token HMAC.
- **Nguồn:** [`docs/legal/music-preview-licensing.md`](../../legal/music-preview-licensing.md) (mục 6.1 & mục 7, câu 1–5); [`docs/srs/01-srs.md`](../../srs/01-srs.md) (mục 7, câu 4).

---

### Câu 10 · Chịu tải trên máy chủ 1 GB RAM & Quy trình điều phối AI Agent (`DOC-04`, `PM-09`)
**Hỏi:** *"Làm thế nào một máy chủ EC2 `t3.micro` 1 GB RAM chịu được 20 phòng (100 người chơi đồng thời), và làm sao một sinh viên kiểm soát được chất lượng của 136 ticket khi làm cùng các AI Agent?"*
- **Trả lời:**
  - Để đạt mục tiêu **`NFR-03` (20 phòng $\times$ 5 người trên 1 GB RAM)**, hệ thống kết hợp 4 chiến thuật (`docs/architecture/01-architecture.md` mục 6 câu 5): (1) giới hạn Heap JVM `-XX:MaxRAMPercentage=60` (~600 MB) kèm 2 GB swapfile chống OOM Killer; (2) giữ trạng thái phòng trong RAM, bằng 0 lượt đọc/ghi đĩa trong suốt các vòng đấu; (3) proxy audio truyền theo chunk nhỏ 8–64 KB, không nạp cả file MP3 vào Heap (tổng băng thông ra ở đỉnh 100 người chỉ khoảng 1,6 MB/s theo `ADR-008`); (4) dùng Caddy Alpine nhẹ thay cho Nginx. Ngưỡng này sẽ được đo thực nghiệm bằng k6 ở `QA-03`.
  - Về quy trình (`PM-09` / `CLO3`), em đóng vai trò **Product Owner & Tech Lead**, áp dụng mô hình **Contract-First + 10 luật chống xung đột (`AGENTS.md`)**: mỗi ticket một worktree và một phạm vi file cứng (`scope-guard.yml`), khóa file nóng (`hot-*`), kiểm tra 5 cổng Definition of Ready trước khi code, bắt buộc tác giả khác người review (review chéo giữa Claude, Codex, Antigravity) và chỉ PO mới có quyền nghiệm thu, merge PR.
- **Nguồn:** [`docs/architecture/01-architecture.md`](../../architecture/01-architecture.md) (mục 5.2–5.3 & mục 6, câu 4–5); [`docs/process/01-project-plan.md`](../../process/01-project-plan.md) (mục 6–8).
