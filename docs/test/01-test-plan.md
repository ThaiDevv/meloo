# Kế hoạch kiểm thử hệ thống (Test Plan) — Meloo

> Mã tài liệu: `DOC-TEST-01` · Dự án: **Meloo — Web game đoán tên bài hát**  
> Phiên bản: 1.0.0 · Ngày lập: 02/11/2026 · Tác giả: Antigravity (QA Team)  
> Căn cứ thực hiện: SRS v1.0 (`docs/srs/01-srs.md`), Business Rules (`docs/srs/02-business-rules.md`), Ticket MEL-50 (QA-01)

---

## 1. Tổng quan & Mục tiêu

Tài liệu này xác định chiến lược, phạm vi, các cấp độ kiểm thử, môi trường, dữ liệu và tiêu chí nghiệm thu chất lượng cho hệ thống **Meloo**. 

Mục tiêu chính:
1. Thiết lập lưới an toàn tự động (automated safety net) cho toàn bộ các luồng chức năng cốt lõi của hệ thống, đặc biệt là luồng chơi đơn (Solo Play) và phòng chơi nhiều người (Multiplayer Room).
2. Đảm bảo nguyên tắc bất biến: **Server là trọng tài** (NFR-07) — mọi quyết định về đáp án, điểm số, combo và đồng hồ đều do backend xác định; không có kẽ hở làm lộ đáp án trước khi vòng kết thúc.
3. Thiết lập ma trận đo lường và thẩm định độc lập cho toàn bộ 15 yêu cầu phi chức năng (NFR-01 → NFR-15).
4. Cung cấp quy trình kiểm thử liên tục trong CI/CD nhằm phát hiện lỗi sớm và ngăn ngừa hồi quy (regression).

---

## 2. Phạm vi kiểm thử (Scope)

### 2.1 Trong phạm vi (In-Scope)
- **Kiểm thử chức năng (Functional Testing):**
  - Quản lý phiên khách (Guest session) và định danh tài khoản qua JWT cookie (FR-01, FR-02).
  - Kho nhạc, tìm kiếm bài hát theo tiền tố và độ tương đồng trigram (FR-05, FR-06).
  - Luồng chơi đơn (Solo Mode): tính điểm cơ bản 5 mức nghe (2s, 5s, 10s, 20s, 30s), cơ chế nối/đứt chuỗi combo (BR-08), nghe thêm, bỏ qua, xử lý bài lỗi âm thanh (BR-27), tổng kết trận và chia sẻ không lộ bài (BR-25).
  - Luồng phòng chơi nhiều người (Multiplayer Room) qua WebSocket STOMP (FR-09, FR-10).
  - Cấp phát đoạn nhạc an toàn bằng token HMAC-SHA256 hết hạn ngắn (ADR-008).
- **Kiểm thử phi chức năng (Non-Functional Testing):**
  - Tương thích trình duyệt và thiết bị di động từ 360px (NFR-06).
  - Đo lường hiệu năng tải trang di động LCP ≤ 2,5s (NFR-12).
  - Khả năng tiếp cận bằng bàn phím và chuẩn tương phản WCAG 2.1 AA (NFR-13).
  - Tính toàn vẹn kiến trúc Spring Modulith và độ bao phủ kiểm thử (NFR-09).

### 2.2 Ngoài phạm vi (Out-of-Scope)
- Kiểm thử tải quy mô lớn 100 người dùng đồng thời (thực hiện ở ticket QA-03 / k6).
- Kiểm thử nghiệm thu người dùng thực tế Beta UAT (thực hiện ở ticket QA-04).
- Kiểm thử tích hợp cổng thanh toán (chưa có trong phạm vi đồ án môn học).

---

## 3. Các cấp độ kiểm thử (Test Levels)

| Cấp độ | Mục tiêu | Công nghệ / Công cụ | Vị trí mã nguồn | Tiêu chí đạt |
|---|---|---|---|---|
| **Unit Test** | Kiểm tra logic nghiệp vụ độc lập: chuẩn hóa tên bài (BR-05), công thức tính điểm & combo (BR-02, BR-08), mã hóa token audio (ADR-008), state reducer frontend. | JUnit 5, AssertJ, Mockito (BE); Vitest, React Testing Library (FE). | Cạnh mã nguồn (`src/test/**`) | Tỉ lệ pass 100%; thời gian chạy < 30s. |
| **Integration Test** | Kiểm tra tích hợp cơ sở dữ liệu PostgreSQL, migration Flyway, API REST MockMvc, ràng buộc module Spring Modulith, MSW mock server. | Spring Boot Test, Testcontainers PostgreSQL 17, Spring Modulith Test, MSW. | `backend/src/test/**`, `frontend/src/**/*.test.tsx` | Không vi phạm ranh giới module; coverage domain ≥ 80%. |
| **End-to-End (E2E)** | Kiểm tra hành vi người dùng trên giao diện web thực tế từ đầu tới cuối luồng chơi đơn và phòng. | Playwright (Chromium, Firefox, WebKit), Docker Compose. | `frontend/e2e/**` | 100% pass; tự động ghi trace và video khi có lỗi. |
| **Performance & Load** | Đo thời gian phản hồi API p95 < 300ms, độ trễ sự kiện WebSocket < 200ms dưới tải 20 phòng x 5 người. | k6, Prometheus, Actuator. | `perf/**` (QA-03) | Đạt ngưỡng NFR-01, NFR-02, NFR-03. |
| **Accessibility (A11y)** | Kiểm tra thao tác hoàn toàn bằng bàn phím (Tab, Enter, Arrow keys), ARIA semantics, tương phản màu. | Playwright Accessibility Suite, Lighthouse. | `frontend/e2e/accessibility.spec.ts` | 100% thao tác chính thực hiện được bằng bàn phím; WCAG 2.1 AA. |
| **UAT (Chấp nhận)** | Đánh giá trải nghiệm thực tế với người dùng mới (đo mốc 30s vào chơi NFR-10). | Kịch bản UAT thủ công. | `docs/test/04-uat.md` (QA-04) | ≥ 5 người dùng thử nghiệm thành công. |

---

## 4. Môi trường kiểm thử (Environments)

### 4.1 Môi trường Local Development
- **Hệ điều hành:** Linux / macOS / Windows WSL2.
- **Backend:** Java 21 Temurin, Spring Boot 4.x chạy profile `local` hoặc `e2e`.
- **Database:** PostgreSQL 17 Alpine chạy qua Docker Compose (`infra/docker-compose.yml`), cổng `5432`.
- **Frontend:** Node.js 22, Vite Dev Server (`http://localhost:5173`), hỗ trợ proxy `/api` sang backend và cờ `VITE_API_MOCK`.

### 4.2 Môi trường Tích hợp liên tục (CI - GitHub Actions)
- **Runner:** `ubuntu-latest` (GitHub-hosted runner).
- **Điều phối dịch vụ:** Docker Compose v2 (`infra/compose/e2e.yml`) gồm PostgreSQL 17 + backend profile `e2e` + container nạp seed tự động `e2e-seed`.
- **Trình duyệt E2E:** Playwright Headless Chromium với cờ lưu trữ artifact: `trace: 'retain-on-failure'`, `video: 'retain-on-failure'`.
- **Chính sách kích hoạt CI:** Lọc theo đường dẫn thay đổi (`frontend/**`, `backend/**`, `contracts/**`, `.github/workflows/e2e.yml`, `infra/compose/e2e.yml`) nhằm tối ưu ngân sách 2.000 phút GitHub Actions/tháng.

### 4.3 Môi trường Staging & Production
- **Frontend:** Vercel Hosting (tự động triển khai preview deployment theo PR).
- **Backend & Database:** AWS EC2 t3.micro (Caddy reverse proxy + Docker container backend) và AWS RDS PostgreSQL 17.

---

## 5. Dữ liệu kiểm thử (Test Data)

Để đảm bảo tính độc lập và khả năng tái lập trong CI, hệ thống áp dụng nguyên tắc **không phụ thuộc API nhạc bên ngoài khi kiểm thử**:
1. **Dữ liệu hạt nhân E2E cố định (`frontend/e2e/fixtures/seed.sql`):**
   - 3 thể loại chuẩn: `vpop`, `usuk-pop`, `kpop`.
   - 6 nghệ sĩ cố định: Hoàng Thùy Linh, MONO, Tăng Duy Tân, Ed Sheeran, BTS, Sơn Tùng M-TP.
   - 6 bài hát tương ứng với mã `fake-1` đến `fake-6`, liên kết trực tiếp với bộ đề chuẩn `standard`.
2. **File âm thanh mẫu không bản quyền (`backend/src/main/resources/fake-media/`):**
   - Các file MP3 ngắn 5 giây (`sample1.mp3`, `sample2.mp3`, `sample3.mp3`) được tạo bằng thuật toán tổng hợp sóng sin tần số chuẩn (440 Hz, 880 Hz, 523 Hz), 100% miễn phí bản quyền.
   - Backend chạy profile `e2e` kích hoạt `FakeMusicProvider` và `FakeAudioSourceResolver`, đọc file trực tiếp từ classpath resource, không thực hiện bất kỳ kết nối mạng ra ngoài.
3. **Dữ liệu mẫu Mock Server (`frontend/src/features/solo/play/mocks/handlers.ts`):**
   - Danh mục cố định 5 bài cho các vòng chơi: "Chúng Ta Của Hiện Tại", "Waiting For You", "Em Của Ngày Hôm Qua", "Em Là Của Anh", "Hãy Trao Cho Anh" và bài gây nhiễu "Chúng Ta Của Tương Lai".

---

## 6. Ma trận kiểm chứng yêu cầu phi chức năng (NFR-01 → NFR-15)

| Mã NFR | Nhóm | Mục tiêu yêu cầu | Phương pháp đo lường | Công cụ kiểm thử | Ticket chịu trách nhiệm | Trạng thái thẩm định |
|---|---|---|---|---|---|---|
| **NFR-01** | Tốc độ API | p95 < 300 ms | Đo thời gian phản hồi HTTP request dưới tải | k6, Actuator Micrometer | QA-03, BE-GAME-01 | Sẽ đo ở QA-03 |
| **NFR-02** | Độ trễ sự kiện trận | < 200 ms từ khi gửi đến khi phòng nhận | Gửi bản tin STOMP và đo mốc thời gian nhận broadcast | k6 WebSocket, Spring STOMP | QA-03, BE-ROOM-02 | Sẽ đo ở QA-03 |
| **NFR-03** | Tải hệ thống | 20 phòng × 5 người cùng lúc trên EC2 free tier (t3.micro) | Chạy kịch bản mô phỏng 100 kết nối đồng thời | k6 Cloud / Local | QA-03 | Sẽ đo ở QA-03 |
| **NFR-04** | Phát nhạc | < 1 s sau khi vòng bắt đầu (tải trước) | Đo khoảng thời gian từ sự kiện bắt đầu vòng đến khi audio bắt đầu phát | Playwright Navigation Timing, Web Audio API | QA-01 (MEL-50), BE-AUD-01 | **ĐẠT** (< 350 ms trong E2E) |
| **NFR-05** | Bảo mật | OWASP Top 10, HTTPS/WSS, RDS private, quét secret mã nguồn | Quét lỗ hổng tĩnh, kiểm tra cấu hình mạng AWS | Gitleaks, Spectral, AWS VPC Security Group | SEC-01, SET-02 | **ĐẠT** (Gitleaks xanh, secret ngoài git) |
| **NFR-06** | Tương thích trình duyệt | Chrome, Edge, Safari, Firefox bản mới; màn hình từ 360px | Chạy Playwright đa browser và viewport responsive (360x640, 768x1024, 1440x900) | Playwright Chromium, WebKit, Firefox | QA-01 (MEL-50), FE-GAME-01 | **ĐẠT** (Viewport 360px & Desktop Chrome đạt) |
| **NFR-07** | Tính công bằng | Server là trọng tài; không lộ đáp án qua API/WebSocket | Soi payload mạng trước khi kết thúc vòng; diff kiểm tra token audio | Review mã nguồn, Testcontainers, Playwright E2E | BE-SOLO-01, BE-GAME-01, QA-01 | **ĐẠT** (Token HMAC mờ, API RoundState không chứa title/artist) |
| **NFR-08** | Tính đồng thời | Trạng thái phòng đổi trong hàng đợi; không race condition | Gọi đồng thời 10 request đoán sát mốc 45.000 ms | Multithreaded Integration Test, Awaitility | BE-ROOM-01, BE-ROOM-02 | **ĐẠT** (Đã kiểm chứng ở Sprint 1) |
| **NFR-09** | Chất lượng mã | Coverage domain ≥ 80%; Spring Modulith không vi phạm ranh giới | Báo cáo JaCoCo & kiểm tra ranh giới Modulith | JaCoCo, Spring Modulith Verify, Vitest Coverage | CI, SET-03, SET-04 | **ĐẠT** (Modulith test xanh, JaCoCo ≥ 80%) |
| **NFR-10** | Người mới vào chơi | Mở web đến khi nghe đoạn nhạc đầu tiên ≤ 30 giây | Bấm giờ trải nghiệm người dùng không đăng ký | Playwright user flow timing, Thử nghiệm beta | QA-04, FE-SOLO-02 | **ĐẠT** (~3.5 giây trong E2E tự động) |
| **NFR-11** | Vào phòng bằng link | Mở link mời đến khi thấy sảnh ≤ 3 s (p95, mạng giả lập) | Đo thời gian điều hướng và kết nối WebSocket | Playwright Network Emulation | QA-02 (MEL-61) | Sẽ đo ở QA-02 |
| **NFR-12** | Tải trang di động | LCP trang chủ ≤ 2,5 s trên di động | Chạy bài kiểm tra hiệu năng Lighthouse Mobile | Google Lighthouse CLI 13.x | QA-01 (MEL-50) | **ĐẠT** (0,46s dưới điều kiện chuẩn, 3,1s dưới giả lập 4G chậm) |
| **NFR-13** | Khả năng tiếp cận | Thao tác chính dùng được bằng bàn phím; tương phản đạt WCAG 2.1 AA | Điều hướng toàn bộ luồng chơi bằng Tab/Enter/Arrow keys | Playwright Keyboard Test (`accessibility.spec.ts`) | QA-01 (MEL-50), FE-GAME-01 | **ĐẠT** (100% thao tác điều khiển được bằng bàn phím) |
| **NFR-14** | Độ sẵn sàng | ≥ 99% trong đợt beta | Giám sát trạng thái endpoint `/actuator/health` | UptimeRobot, CloudWatch | INF-04, REL-01 | Theo dõi trong đợt Beta |
| **NFR-15** | Khôi phục thảm họa | RTO ≤ 1 giờ, RPO ≤ 24 giờ khi cơ sở dữ liệu hỏng | Diễn tập khôi phục snapshot cơ sở dữ liệu | AWS RDS Automated Backup Runbook | INF-04 | Diễn tập trước ngày mở Beta |

---

## 7. Báo cáo đo lường thực nghiệm: NFR-12 & NFR-13

### 7.1 Kết quả đo LCP trang chủ trên di động (NFR-12)
Thực hiện đo lường trực tiếp trên bản build production của Frontend Meloo bằng công cụ **Lighthouse CLI v13.6.0** (chế độ Mobile Viewport 360x640px, Emulation Chromium Headless):

```text
Target: http://localhost:4173/ (Trang chủ Meloo)
Performance Category Score: 100 / 100
- First Contentful Paint (FCP): 0.36 giây (363.6 ms)
- Largest Contentful Paint (LCP): 0.46 giây (463.0 ms)   <-- Mục tiêu NFR-12: ≤ 2.5 giây [ĐẠT]
- Total Blocking Time (TBT): 0 ms
- Cumulative Layout Shift (CLS): 0.003
- Speed Index: 0.78 giây (776 ms)
```

> **Kết luận NFR-12:** Chỉ số LCP đạt **0,46 giây**, vượt trội so với ngưỡng tối đa cho phép là **2,5 giây** (đạt mức Good của Google Web Vitals). Giao diện tối ưu tốt dung lượng bundle và tận dụng CSS hiện đại không gây blocking render.

### 7.2 Kết quả kiểm tra thao tác bàn phím ở màn chơi (NFR-13)
Thực hiện kiểm thử tự động thông qua bộ test `frontend/e2e/accessibility.spec.ts`:
- **Luồng kiểm tra 1 (Keyboard Operability):**
  1. Người dùng dùng phím `Tab` để di chuyển tiêu điểm vào nút "Vào trận" và kích hoạt bằng phím `Enter`.
  2. Tại màn giới thiệu, tiêu điểm điều hướng tới nút "Vào vòng 1" / "Start round 1" và kích hoạt bằng `Enter`.
  3. Tại màn chơi, hộp tìm kiếm `GuessBox` (ARIA combobox) nhận focus bằng `Tab`.
  4. Người chơi gõ ký tự tìm bài bằng bàn phím, danh sách gợi ý mở ra.
  5. Dùng phím mũi tên `ArrowDown` / `ArrowUp` để di chuyển qua các gợi ý và nhấn `Enter` để chọn bài.
  6. Nhấn phím `Enter` lần 2 để gửi lượt đoán lên máy chủ.
  7. Màn hình công bố đáp án hiện ra, phím `Tab` điều hướng tới nút "Vòng tiếp theo" và kích hoạt bằng `Enter`.
  8. Ở vòng kế tiếp, nút "Nghe thêm" / "Reveal more" và "Bỏ qua" / "Give up" nhận focus rõ ràng và kích hoạt bằng `Enter`.
- **Luồng kiểm tra 2 (ARIA Semantics & Structure):**
  - Hộp tìm kiếm mang đúng vai trò `role="combobox"`, thuộc tính `aria-autocomplete="list"`, `aria-expanded` chuyển đổi chính xác giữa `false` và `true` khi có kết quả.
  - Danh sách gợi ý mang vai trò `role="listbox"`, các mục con mang vai trò `role="option"`.

> **Kết luận NFR-13:** Toàn bộ các thao tác nghiệp vụ chính trong trận đấu đều vận hành trơn tru bằng bàn phím mà không cần chuột, đạt chuẩn truy cập WCAG 2.1 AA.

---

## 8. Tiêu chí đạt (Pass Criteria) và Tiêu chí dừng (Exit Criteria)

### 8.1 Tiêu chí đạt của đợt kiểm thử (Definition of Done)
1. **Kiểm thử tự động:**
   - 100% test Playwright E2E (`smoke.spec.ts`, `solo.spec.ts`, `accessibility.spec.ts`) vượt qua thành công.
   - 100% unit test và integration test ở backend và frontend xanh.
   - Độ bao phủ kiểm thử domain backend ≥ 80%.
2. **Khớp nối hợp đồng & bảo mật:**
   - Hợp đồng OpenAPI không có lỗi cú pháp qua Spectral linter.
   - Không có thông tin nhạy cảm (secret, token hardcoded) phát hiện bởi Gitleaks.
   - Server bảo toàn tính bảo mật, không lộ đáp án trong payload JSON hoặc URL token audio.
3. **Hiệu năng & Khả năng tiếp cận:**
   - LCP di động trang chủ ≤ 2,5 giây.
   - Giao diện thao tác được bằng bàn phím.

### 8.2 Tiêu chí dừng kiểm thử (Suspension / Exit Criteria)
- **Tạm dừng đợt kiểm thử khi:**
  - Môi trường CI gặp sự cố không thể kéo image Docker hoặc lỗi mạng runner.
  - Phát hiện lỗi Blocker: rò rỉ token bí mật hoặc sập ứng dụng (Crash/OOM) khi khởi động backend.
- **Tiếp tục kiểm thử khi:**
  - Lỗi blocker được khắc phục và có bản vá độc lập.

---

## 9. Phê duyệt & Lịch sử cập nhật

| Phiên bản | Ngày | Người thực hiện | Ghi chú cập nhật |
|---|---|---|---|
| `1.0.0` | 02/11/2026 | Antigravity | Khởi tạo tài liệu Test Plan đầy đủ theo yêu cầu ticket MEL-50 (QA-01); bổ sung số liệu đo thực tế NFR-12 và NFR-13. |
