# PM-09 · Kế hoạch dự án Meloo (CLO3)

> Ticket: MEL-115 · Phiên bản: 1.0 · Cập nhật: 02/11/2026  
> Chuẩn đầu ra môn học: **CLO3** ("Xây dựng và vận hành quy trình hoạt động nhóm hiệu quả, chuyên nghiệp").  
> Tài liệu liên quan: Quy trình làm việc nhóm [02-working-process.md](02-working-process.md), Hướng dẫn Harness [harness.md](harness.md), Luật chung [AGENTS.md](../../AGENTS.md).

---

## 1. Tổng quan và mục tiêu dự án

### 1.1. Bối cảnh và bài toán
Meloo ra đời nhằm giải quyết nhu cầu giải trí và gắn kết nhóm của giới trẻ Việt Nam (học sinh, sinh viên, nhân viên văn phòng). Bối cảnh thực tế:
- Theo khảo sát của Q&Me ([01-problem.md](../business/01-problem.md), nguồn [S1]), **75%** người Việt nghe nhạc mỗi ngày và 93% nghe trên điện thoại. Tuy nhiên, trải nghiệm nghe nhạc hiện tại trên Spotify hay YouTube mang tính cá nhân, thụ động và thiếu tính tương tác, cạnh tranh.
- Khảo sát nhu cầu người dùng của dự án (BIZ-03 tại [03-survey.md](../business/03-survey.md)) đang được triển khai thu thập với mục tiêu N ≥ 50 mẫu, dự kiến chốt và xuất dữ liệu chính thức vào ngày 08/11/2026 (`[CẦN SỐ LIỆU]`) để phục vụ tổng kết Sprint 2 (BIZ-05).
- Các trò chơi đoán nhạc phổ biến quốc tế (Heardle, SongTrivia, SongPop) hoặc đã đóng cửa, hoặc thiếu kho bài hát tiếng Việt, giao diện tiếng Anh và bắt buộc cài đặt ứng dụng phức tạp.
- Khi muốn chơi cùng nhau, các nhóm bạn phải kết hợp thủ công giữa web game nước ngoài và Discord/Google Meet để trò chuyện, khiến thời gian chuẩn bị và rủ rê kéo dài quá mức so với ván chơi 10 phút.

### 1.2. Mục tiêu dự án (Project Objectives)
- **Mục tiêu sản phẩm:** Xây dựng một web game đoán nhạc thời gian thực (realtime) có giao diện tiếng Việt thân thiện, vào phòng tức thì qua một đường link (không cần cài app hay đăng ký tài khoản bắt buộc), tích hợp sẵn phòng chơi 2–5 người, chat văn bản và voice chat WebRTC.
- **Mục tiêu kỹ thuật:** Triển khai kiến trúc hướng module (Spring Modulith) kết hợp STOMP WebSocket trên nền Java 21 / Spring Boot 4.x và React / TypeScript. Server đóng vai trò trọng tài độc quyền bảo mật đáp án tuyệt đối bằng token âm thanh mờ HMAC hết hạn ngắn.
- **Mục tiêu học phần & nhóm:** Vận hành mô hình phối hợp giữa một người (Product Owner / Tech Lead) và các AI Coding Agent (Codex, Claude, Antigravity) theo quy trình Agile/Scrum chuyên nghiệp, kiểm soát chất lượng qua CI/CD và hợp đồng API-first, đáp ứng chuẩn đầu ra CLO3.

*Vì sao làm vậy:* Bài toán xuất phát từ nhu cầu thật đã qua khảo sát thị trường; mục tiêu rõ ràng giúp kiểm soát chặt phạm vi đồ án trong thời gian 7 tuần.

---

## 2. Phạm vi dự án theo mô hình MoSCoW

Toàn bộ 136 ticket công việc (không tính 14 Epic quản lý danh mục) trong Jira backlog của dự án Meloo được phân loại nghiêm ngặt theo mô hình MoSCoW (snapshot JQL `project = MEL` ngày 02/11/2026):

| Phân loại | Số lượng | Tỷ lệ | Định nghĩa & Phạm vi chức năng cụ thể |
|---|---|---|---|
| **Must Have** | **99 tickets** | **72.8%** | **Bắt buộc phải có để hệ thống hoạt động và nộp đồ án:**<br>- Chơi đơn: 5 mức nghe mở dần (2s, 5s, 10s, 20s, 30s), tìm kiếm không dấu, tính điểm Heardle chuẩn hóa, token âm thanh mờ.<br>- Phòng nhiều người: Tạo phòng, vào bằng mã/link, hàng đợi lệnh tuần tự qua single-thread executor (ADR-007), đồng bộ đồng hồ server 45s, bảng xếp hạng trận đấu.<br>- Hợp đồng API-first (OpenAPI, AsyncAPI), hạ tầng Docker/Caddy, xác thực JWT khách & tài khoản, CI/CD tự động, tài liệu môn học bắt buộc (SRS, UML, Architecture, Test Plan, Báo cáo cuối kỳ). |
| **Should Have** | **28 tickets** | **20.6%** | **Tính năng quan trọng, hoàn thiện trải nghiệm người dùng:**<br>- Voice chat WebRTC trong phòng riêng qua STOMP signaling.<br>- Đăng nhập mạng xã hội (Google OAuth2, Facebook Login), cơ chế xóa tài khoản chuẩn bảo mật.<br>- Hệ thống tiến trình: Cấp bậc, huy hiệu thành tích, lịch sử thi đấu, bảng xếp hạng tuần.<br>- Giao diện PWA có thể cài đặt trên điện thoại di động, trang quản trị Admin cơ bản, kiểm thử tải k6. |
| **Could Have** | **8 tickets** | **5.9%** | **Tính năng giá trị gia tăng, chỉ làm khi còn thừa thời gian:**<br>- Bộ đề tùy chỉnh chuyên biệt dành cho giáo viên, streamer, sự kiện nhóm.<br>- Thử nghiệm tính năng chia sẻ kết quả mở rộng.<br>- Dọn dẹp tài liệu và tối ưu hóa harness hợp đồng nâng cao (HAR-02). |
| **Won't Have (MVP)** | **0 tickets (Jira backlog) / Theo SRS** | **0.0%** | **Không thực hiện trong phạm vi đồ án môn học (theo SRS `docs/srs/01-srs.md`):**<br>- Thanh toán tiền thật qua cổng thanh toán (chỉ giữ mô hình gói Premium giả lập cho đề án kinh doanh Phase 2).<br>- Ứng dụng di động Native iOS / Android trên App Store / CH Play (dùng PWA để thay thế).<br>- Kiến trúc microservices phân tán lớn; nhận diện giọng nói để đoán bài. |
| *Chưa gán nhãn MoSCoW* | 1 ticket | 0.7% | MEL-128 (Ticket bổ sung hợp đồng trong S1: ghi `REFRESH_RACE` vào contract). |
| **Tổng cộng (Tasks/Stories)** | **136 tickets** | **100%** | **136 tasks/stories thực tế (cộng 14 Epics quản lý danh mục = 150 Jira issues).** |

*Vì sao làm vậy:* Phương pháp DSDM khuyến nghị tỷ lệ Must Have ≤ 60% cho các dự án thương mại thông thường. Trong dự án Meloo, tỷ lệ Must Have ở mức 72.8% (99/136 ticket công việc) là hoàn toàn hợp lý và có chủ đích: đồ án môn học đại học đòi hỏi khối lượng tài liệu học thuật bắt buộc rất lớn (SRS, use cases, 7 loại sơ đồ UML, C4, kiến trúc, ERD, test plan, báo cáo cuối kỳ, slide, video). Toàn bộ các hạng mục tài liệu và khung nền tảng này đều buộc phải xếp vào Must Have để đủ điều kiện nghiệm thu học phần.

---

## 3. Ba mốc nộp quan trọng của học phần (Milestones)

Dự án được căn chỉnh trực tiếp với lịch học phần và các mốc đánh giá chính thức của nhà trường:

```
[24/10/2026]                    [16/11/2026]                     [02/12/2026]
   MỐC 1                           MỐC 2                            MỐC 3
Đăng ký đề tài            Thuyết minh chi tiết thiết kế       Báo cáo cuối kỳ & Bảo vệ
  (BIZ-01)                       (DOC-05)                         (DOC-08, DEF-01)
     │                              │                                │
  Sprint 0                       Sprint 3                         Sprint 6
```

### Mốc 1: Đăng ký đề tài & Phê duyệt bài toán
- **Hạn chót:** 24/10/2026, 09:00 (Đã hoàn thành ở Sprint 0).
- **Ticket Jira:** BIZ-01 (MEL-17).
- **Nội dung bàn giao:** Bản mô tả đề tài, phát biểu bài toán, personas, giá trị cốt lõi, kế hoạch khảo sát nhu cầu người dùng Google Forms.
- **Trạng thái:** Đã nộp và được giảng viên phê duyệt.

### Mốc 2: Thuyết minh chi tiết thiết kế hệ thống
- **Hạn chót:** 16/11/2026, 09:07 (Hạn nộp chính xác theo lịch của hệ thống trường ghi nhận trên ticket DOC-05 / MEL-62).
- **Ticket Jira:** DOC-05 (MEL-62).
- **Nội dung bàn giao:** Bộ hồ sơ thiết kế kỹ thuật hoàn chỉnh:
  - Tài liệu đặc tả yêu cầu phần mềm SRS v1 (`docs/srs/01-srs.md`, `docs/srs/02-business-rules.md`).
  - Đặc tả 10 use case trọng tâm kèm sơ đồ use case PlantUML (`docs/srs/usecases/`).
  - Thiết kế kiến trúc tổng thể C4 Model + Sơ đồ triển khai hạ tầng (`docs/architecture/01-architecture.md`).
  - Thiết kế chi tiết: Sơ đồ lớp (Class), Sơ đồ tuần tự (Sequence), Sơ đồ hoạt động (Activity), Sơ đồ máy trạng thái (State Machine) (`docs/uml/`).
  - Thiết kế cơ sở dữ liệu: ERD, từ điển dữ liệu, kế hoạch migration Flyway (`docs/architecture/data/`).

### Mốc 3: Báo cáo tổng kết cuối kỳ & Bảo vệ đồ án
- **Hạn chót:** 02/12/2026 (Trong Sprint 6).
- **Ticket Jira:** DOC-08 (MEL-101), DEF-01 (MEL-102).
- **Nội dung bàn giao:**
  - Báo cáo cuối kỳ bản hoàn chỉnh (PDF/Word đạt chuẩn rubric A5.4, lưu tại `docs/submissions/`).
  - Sản phẩm phần mềm Meloo v1.0.0 chạy trực tiếp trên môi trường production (AWS EC2 + Vercel + RDS).
  - Báo cáo kiểm thử tự động, kết quả kiểm thử tải k6 và biên bản nghiệm thu người dùng beta (UAT).
  - Slide thuyết trình, video demo luồng chơi thực tế và bộ ngân hàng câu hỏi bảo vệ phản biện.

*Vì sao làm vậy:* Ba mốc nộp tạo nên "thước đo nhịp tim" (cadence) cho toàn bộ dự án, gắn kết tiến độ kỹ thuật của các agent với yêu cầu học thuật của nhà trường.

---

## 4. Kế hoạch chi tiết 7 Sprint

Dự án được tổ chức thành 7 sprint liên hoàn với mục tiêu và tiến độ thực tế (snapshot Jira ngày 02/11/2026):

```
┌─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┐
│   S0    │   S1    │   S2    │   S3    │   S4    │   S5    │   S6    │
│Khởi động│Nền tảng │Chơi đơn │Realtime │Beta công│Hoàn     │Phát hành│
│(14 tkts)│(27 tkts)│(29 tkts)│(16 tkts)│khai(16) │thiện(18)│& Nộp(8) │
└─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┘
```

> **Ghi chú về ngày sprint:** Trong giai đoạn khởi động (S0, S1, S2), ngày bắt đầu giữa các sprint có sự gối đầu (overlap) 3–4 ngày theo đúng metadata của Jira Sprint: S1 được kích hoạt sớm ngay khi monorepo SET-02 hoàn thành ở S0 để Claude soạn thảo SRS và hợp đồng API mà không phải chờ đóng sprint; S2 mở khi S1 chuẩn bị đóng để backend và frontend bắt tay vào luồng chơi đơn. Từ Sprint 3 trở đi, các sprint diễn ra tuần tự nghiêm ngặt mỗi tuần 1 sprint.

### Sprint 0 · Khởi động (17/10/2026 – 25/10/2026) — *Đã hoàn thành*
- **Mục tiêu:** Nộp đăng ký đề tài; thiết lập GitHub monorepo, CI và harness quy ước sẵn sàng cho AI agent; phát hành khảo sát người dùng; chốt nguồn dữ liệu nhạc.
- **Tiến độ Jira:** 14/14 tickets hoàn thành (14 Done, 0 In Progress, 0 To Do).

### Sprint 1 · Nền tảng (22/10/2026 – 01/11/2026) — *Đã hoàn thành*
- **Mục tiêu:** Hoàn thiện tài liệu SRS, mô hình use case, thiết kế CSDL ERD & migration; ban hành hợp đồng API v1 (`openapi.yaml`); xây dựng module identity (JWT khách), catalog kho nhạc (seed ≥ 1.000 bài), engine luật chơi domain thuần; đưa bản v0.1.0 lên production.
- **Tiến độ Jira:** 27/27 tickets hoàn thành (27 Done, 0 In Progress, 0 To Do).

### Sprint 2 · Chơi đơn (28/10/2026 – 08/11/2026) — *Đang diễn ra (Active)*
- **Mục tiêu:** Luồng chơi đơn hoàn chỉnh đầu-cuối trên production (v0.2.0); hạ tầng realtime WebSocket/STOMP; bộ sơ đồ thiết kế chi tiết (DOC-03, DOC-04); phân tích kết quả khảo sát người dùng (BIZ-05); tài liệu kế hoạch và quy trình (PM-09 / MEL-115).
- **Tiến độ Jira:** 29 tickets (20 Done, 3 In Progress, 6 To Do).

### Sprint 3 · Nhiều người chơi (09/11/2026 – 15/11/2026) — *Sắp diễn ra*
- **Mục tiêu:** Tính năng phòng riêng chơi thời gian thực 2–5 người (v0.3.0); đồng bộ trạng thái vòng chơi và đồng hồ server 45 giây; nộp hồ sơ thuyết minh chi tiết thiết kế (Mốc 2, DOC-05) trước 16/11; tổ chức beta kín (closed beta) với nhóm bạn bè.
- **Kế hoạch Jira:** 16 tickets (0 Done, 0 In Progress, 16 To Do).

### Sprint 4 · Beta công khai (16/11/2026 – 22/11/2026) — *Sắp diễn ra*
- **Mục tiêu:** Tích hợp đăng nhập Google OAuth2, lưu lịch sử ván đấu, bảng xếp hạng tuần; chế độ ghép trận ngẫu nhiên (Matchmaking); tính năng thử thách bài hát hàng ngày (Daily Challenge); bảng số liệu admin; phát hành bản Beta công khai (v0.4.0).
- **Kế hoạch Jira:** 16 tickets (0 Done, 0 In Progress, 16 To Do).

### Sprint 5 · Hoàn thiện & Kiểm định (23/11/2026 – 29/11/2026) — *Sắp diễn ra*
- **Mục tiêu:** Tích hợp Voice chat WebRTC trong phòng riêng; đăng nhập Facebook; hệ thống huy hiệu thành tích; hỗ trợ PWA cài đặt trên di động; rà quét bảo mật toàn diện OWASP; kiểm thử tải k6; nghiệm thu người dùng UAT; **đóng băng tính năng vào 23:59 ngày 29/11/2026**.
- **Kế hoạch Jira:** 18 tickets (0 Done, 0 In Progress, 18 To Do).

### Sprint 6 · Phát hành & Bảo vệ (30/11/2026 – 02/12/2026) — *Sắp diễn ra*
- **Mục tiêu:** Tuyệt đối không thêm tính năng mới; tập trung sửa lỗi tồn đọng sau UAT (BUG-00); hoàn thiện bản báo cáo cuối kỳ (DOC-08), slide thuyết trình và video demo (DEF-01); gắn tag phát hành Meloo v1.0.0; nộp đồ án đúng hạn 02/12.
- **Kế hoạch Jira:** 8 tickets (0 Done, 0 In Progress, 8 To Do).

### Tổng kết phân bổ theo Sprint
- **Tổng số ticket đã gán vào 7 Sprint:** 128 tickets (61 Done, 3 In Progress, 64 To Do).
- **Ticket trên Backlog chưa gán Sprint:** 8 tickets (0 Done, 0 In Progress, 8 To Do; thuộc nhóm Phase 2 Could Have và tối ưu hóa HAR-02).
- **Tổng cộng số ticket công việc:** **128 + 8 = 136 tickets** (khớp chính xác 100% với tổng số non-epic tasks trên Jira).

*Vì sao làm vậy:* Chu kỳ sprint giúp phát hiện sớm sai lệch; việc đóng băng tính năng ở cuối Sprint 5 dành trọn vẹn Sprint 6 cho tài liệu, kiểm thử và diễn tập bảo vệ, triệt tiêu rủi ro "vừa demo vừa sửa lỗi".

---

## 5. Cấu trúc phân rã công việc theo 14 Epic (WBS)

Hệ thống Meloo được phân rã thành 14 Epic quản lý danh mục trên Jira. Dưới đây là ma trận phân bổ toàn bộ **136 ticket công việc** (không tính 14 ticket loại Epic) được trích xuất từ Jira ngày 02/11/2026:

| Mã Epic | Tên Epic | Số ticket | Đã xong (Done) | Đang làm (InProg) | Chưa làm (ToDo) | Phân loại MoSCoW |
|---|---|---|---|---|---|---|
| **E01 (MEL-1)** | Nghiệp vụ & chứng minh nhu cầu | 9 | 5 | 1 | 3 | 6 Must, 3 Should |
| **E02 (MEL-2)** | Yêu cầu & thiết kế (UML) | 7 | 6 | 0 | 1 | 7 Must |
| **E03 (MEL-3)** | Nền tảng, hợp đồng API & quy trình | 27 | 18 | 2 | 7 | 24 Must, 3 Should |
| **E04 (MEL-4)** | Tài khoản & quyền riêng tư | 8 | 2 | 0 | 6 | 6 Must, 2 Should |
| **E05 (MEL-5)** | Kho nhạc | 9 | 7 | 0 | 2 | 7 Must, 2 Should |
| **E06 (MEL-6)** | Luật chơi, chơi đơn & thử thách ngày | 14 | 9 | 0 | 5 | 7 Must, 7 Should |
| **E07 (MEL-7)** | Nhiều người chơi & chat | 11 | 2 | 0 | 9 | 11 Must |
| **E08 (MEL-8)** | Voice chat | 4 | 0 | 0 | 4 | 4 Should |
| **E09 (MEL-9)** | Tiến trình & xếp hạng | 5 | 0 | 0 | 5 | 3 Must, 2 Should |
| **E10 (MEL-10)** | Quản trị & kiểm duyệt | 4 | 0 | 0 | 4 | 4 Must |
| **E11 (MEL-11)** | Hạ tầng & vận hành | 8 | 4 | 0 | 4 | 5 Must, 3 Should |
| **E12 (MEL-12)** | Kiểm thử & chất lượng | 7 | 1 | 0 | 6 | 6 Must, 1 Should |
| **E13 (MEL-13)** | Bàn giao cuối kỳ | 4 | 0 | 0 | 4 | 4 Must |
| **E14 (MEL-14)** | Phase 2 — Kinh doanh & mở rộng | 7 | 0 | 0 | 7 | 7 Could |
| *Tiểu kết 14 Epic* | *Các ticket gắn parent vào 14 Epic* | **124** | **54** | **3** | **67** | **90 Must, 27 Should, 7 Could** |
| *Ticket bổ sung* | *Các ticket kỹ thuật phát sinh (MEL-122..128, 146, 149..152)* | **12** | **7** | **0** | **5** | **9 Must, 1 Should, 1 Could, 1 khác** |
| **Tổng cộng (Tasks)** | **136 ticket công việc (cộng 14 Epic = 150 Jira issues)** | **136** | **61** | **3** | **72** | **99 Must, 28 Should, 8 Could, 1 khác** |

*Vì sao làm vậy:* Việc bóc tách rõ ràng giữa 14 ticket loại Epic (danh mục) và 136 ticket công việc thực tế loại bỏ hoàn toàn sự mâu thuẫn số liệu, giúp hội đồng và Tech Lead có được con số chính xác tuyệt đối khi đối chiếu với hệ thống Jira.

---

## 6. Ma trận phân tích rủi ro và giải pháp phòng ngừa

| Mã | Loại rủi ro | Mô tả rủi ro cụ thể | Xác suất | Tác động | Biện pháp phòng ngừa & Giảm thiểu |
|---|---|---|---|---|---|
| **R01** | Kỹ thuật / Nghiệp vụ | **Lộ đáp án bài hát qua Network hoặc DOM:** Người chơi mở DevTools xem payload REST/WebSocket để biết tên bài trước khi hết giờ. | Cao | Nghiêm trọng | - **Server là trọng tài tuyệt đối (NFR-07, ADR-008):** Trong suốt vòng chơi, server không bao giờ gửi id bài hát, tên bài hát hay ca sĩ xuống client.<br>- Chỉ cấp audio qua token mờ HMAC thời hạn ngắn (TTL ≤ 2 phút), endpoint `/api/v1/audio/{token}` theo đúng hợp đồng OpenAPI.<br>- Thông tin bài hát đáp án và nguồn nghe chỉ được công bố sau khi server đã chính thức đóng vòng chơi (BR-29). |
| **R02** | Kỹ thuật / Bản quyền | **Link preview nhạc bị hỏng hoặc nhà cung cấp gián đoạn:** Đoạn preview từ Deezer API bị lỗi hoặc không khả dụng. | Trung bình | Cao | - Thuật toán chọn bài kiểm tra tính hợp lệ của preview trước khi bắt đầu vòng (BR-27); nếu đoạn nhạc lỗi, server tự động tráo bài khác mà không ai bị trừ điểm.<br>- Thiết kế module Catalog qua interface `MusicProvider` đa nguồn (ADR-003, `catalog/provider/`), cho phép linh hoạt cấu hình nguồn phát mà không ảnh hưởng tới core game. |
| **R03** | Kỹ thuật / Xung đột | **Race condition trong phòng chơi nhiều người:** Nhiều người chơi cùng bấm đoán tại mili-giây thứ 44.999 gây sai lệch thứ hạng. | Cao | Cao | - Áp dụng **ADR-007**: Mỗi phòng chơi sở hữu một actor với single-thread executor riêng biệt (`RoomActor`), mọi lệnh thay đổi trạng thái phòng đều đi tuần tự qua executor này.<br>- Đồng hồ server là nguồn sự thật duy nhất (BR-07); mọi lệnh đoán gửi tới sau 45.000ms đều bị từ chối `TIMEOUT`. |
| **R04** | Quy trình / Agent | **Code AI bị ảo giác, phá vỡ kiến trúc module:** Agent tự ý gọi chéo CSDL hoặc sửa code ngoài module được giao. | Cao | Nghiêm trọng | - Chặn cứng qua công cụ: Script `check-scope.sh` và workflow `scope-guard.yml` kiểm tra danh sách file sửa trước khi commit và trên PR.<br>- Kiểm thử kiến trúc tự động bằng Spring Modulith (`ApplicationModules.verify()`) ngăn chặn phụ thuộc vòng hoặc vi phạm đóng gói.<br>- Quy tắc "Hợp đồng trước" (Contract-first): API đổi ở `contracts/` trước khi code backend/frontend. |
| **R05** | Quy trình / Git | **Xung đột mã nguồn khi nhiều agent cùng làm việc:** Hai agent cùng sửa cấu hình hoặc migration gây kẹt PR. | Trung bình | Cao | - 10 luật chống xung đột ([AGENTS.md](../../AGENTS.md)): Khóa module và khóa nhãn file nóng (`hot-*`).<br>- Mỗi ticket một worktree độc lập, tối đa 2 agent làm việc đồng thời (1 tác giả, 1 reviewer).<br>- Rebase bắt buộc trước khi mở PR. |
| **R06** | Tiến độ / Hạ tầng | **Cạn kiệt hạn mức GitHub Actions hoặc AWS EC2:** Hết 2.000 phút CI miễn phí khiến PR không thể merge. | Trung bình | Cao | - Lọc đường dẫn (Path filtering) trong CI: Sửa backend chỉ chạy maven, sửa docs không chạy build nặng.<br>- Docker Compose tối ưu hóa layer cache; server EC2 t4g.small tiết kiệm chi phí. |

---

## 7. Giải thích lý do thiết kế (Vì sao làm vậy)

1. **Vì sao chọn mô hình Scrum 1 tuần thay vì 2 tuần?**  
   *Trả lời:* Dự án chỉ kéo dài 7 tuần học kỳ với khối lượng công việc lớn. Chu kỳ sprint 1 tuần tạo áp lực phản hồi nhanh, giúp Thái phát hiện ngay các sai lệch về nghiệp vụ hoặc ảo giác của AI agent ngay trong tuần, thay vì để tồn đọng sang tuần thứ 2 gây vỡ kế hoạch.
2. **Vì sao thiết kế 14 Epic bao phủ cả Phase 2 (E14)?**  
   *Trả lời:* Đồ án không chỉ là bài tập lập trình mà còn hướng tới chuẩn đầu ra khởi nghiệp (BIZ-08). Việc phân định rõ E14 (Could Have) giúp chứng minh cho hội đồng thấy tính khả thi thương mại và tầm nhìn dài hạn của sản phẩm mà không làm rủi ro tiến độ MVP của đồ án môn học.
3. **Vì sao phải có cơ chế "Đóng băng tính năng" ở Sprint 5?**  
   *Trả lời:* Sai lầm phổ biến của sinh viên là thêm tính năng đến tận ngày nộp, dẫn đến hệ thống không ổn định khi bảo vệ. Đóng băng tính năng trước 3 ngày giúp toàn bộ nhóm tập trung vào kiểm thử tải, rà bảo mật và chuẩn bị kịch bản demo hoàn hảo.

---

## 8. Ngân hàng câu hỏi bảo vệ (Defense Preparation)

1. **Thầy/Cô hỏi:** *Làm thế nào bạn đảm bảo 150 ticket trên Jira được hoàn thành đúng hạn chỉ với một sinh viên thực hiện?*  
   **Trả lời:** Em đóng vai trò Product Owner và Tech Lead, chỉ đạo trực tiếp các AI Agent chuyên trách (Codex làm backend/infra, Claude làm frontend/contract/docs, Antigravity hỗ trợ linh hoạt). Toàn bộ quy trình được tự động hóa qua harness, kiểm soát bằng 5 tiêu chí Definition of Ready và CI chặn cứng, giúp em tập trung vào duyệt thiết kế, kiểm tra nghiệp vụ và nghiệm thu chất lượng thay vì mất thời gian gõ boilerplate.
2. **Thầy/Cô hỏi:** *Nếu nguồn phát nhạc Deezer gặp trục trặc về bản quyền hoặc đường truyền thì giải pháp dự phòng là gì?*  
   **Trả lời:** Nhóm đã thiết kế kiến trúc qua interface `MusicProvider` (ADR-003, `catalog/provider/`). Cơ chế proxy token mờ của hệ thống (ADR-008) độc lập với nguồn phát gốc, cho phép backend trỏ tới bất kỳ nguồn audio hợp pháp nào khác do Tech Lead cấu hình mà không làm thay đổi logic game-core hay giao diện người dùng.
3. **Thầy/Cô hỏi:** *Cơ sở nào để bạn phân chia tỷ lệ 72.8% Must Have và 20.6% Should Have trong backlog?*  
   **Trả lời:** Tỷ lệ này được xây dựng dựa trên yêu cầu học thuật của đồ án môn học kết hợp ma trận rủi ro: Nhóm ưu tiên tuyệt đối cho trải nghiệm chơi mượt mà không cần đăng ký, tính toàn vẹn của luật chơi và toàn bộ hồ sơ tài liệu bắt buộc theo đề cương môn học (Must Have). Các tính năng như Voice chat hay PWA (Should Have) là giá trị gia tăng, sẵn sàng cắt giảm nếu tiến độ bị đe dọa mà không làm mất đi tính hoàn chỉnh của đề tài.
