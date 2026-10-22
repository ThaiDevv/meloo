# Đặc tả yêu cầu phần mềm (SRS) Meloo — v1

> Ticket: MEL-28 (DOC-01) · Trạng thái: **đã chốt ngày 22/10/2026**
> Nguồn: tài liệu "Meloo — Kế hoạch dự án & Quy trình làm việc" (mục Phạm vi sản phẩm, Kiến trúc & công nghệ, Phụ lục A) và `docs/business/01-problem.md` (BIZ-02). Các NFR-10 → NFR-15 được bổ sung ngày 22/10 theo tiêu chí trải nghiệm người chơi.
> Quy tắc nghiệp vụ nằm ở `02-business-rules.md` (mã BR-xx). Thuật ngữ ở `03-glossary.md`.

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này mô tả Meloo phải làm gì và làm tốt đến mức nào. Đây là đầu vào cho thiết kế (DES-01), lập trình (BE-GAME-01…), kiểm thử và báo cáo.

### 1.2 Sản phẩm
Meloo là web game đoán tên bài hát. Mỗi vòng, người chơi nghe đoạn đầu của bản preview 30 giây, mở dần 2 → 5 → 10 → 20 → 30 giây; đoán đúng càng sớm thì càng nhiều điểm. Người chơi có thể chơi một mình, làm thử thách hằng ngày, hoặc vào phòng 2–5 người theo thời gian thực, có chat và voice.

### 1.3 Phạm vi
- **Trong phạm vi (MVP, môn học):** các yêu cầu Must và Should ở mục 3.
- **Ngoài phạm vi:** phát cả bài, app iOS, thanh toán thật trong thời gian môn học (Won't).
- **Phase 2 (Could, sau môn học):** Premium, quảng cáo, chế độ Host/Event, bài hát tài trợ, app di động, Redis cho trạng thái phòng, chế độ đội/sinh tồn/giải đấu.

Quy ước ưu tiên (MoSCoW): Must bắt buộc để bảo vệ; Should làm nếu kịp sau Must; Could để Phase 2. Khi trễ thì cắt từ dưới lên, không bao giờ cắt Must.

### 1.4 Tài liệu tham chiếu
- `docs/business/01-problem.md` (bài toán, personas, KPI), `docs/business/02-competitors.md`.
- `contracts/openapi.yaml`, `contracts/asyncapi.yaml` (hợp đồng API, chưa có ở thời điểm viết).

## 2. Mô tả tổng quan

### 2.1 Người dùng (actor)

| Actor | Họ là ai | Cần gì |
|---|---|---|
| Khách | Người chơi chưa đăng nhập | Vào chơi ngay bằng biệt danh, mọi chế độ |
| Người chơi đã đăng nhập | Nhóm bạn sinh viên, fan âm nhạc | Chơi cùng bạn bè, thử thách ngày, xếp hạng, huy hiệu |
| Chủ phòng | Người tạo phòng riêng hoặc phòng công khai (phòng ghép trận không có chủ phòng) | Chọn bộ nhạc, số vòng, bắt đầu trận, mời người treo máy ra khỏi phòng |
| Quản trị viên (ADMIN) | Đội vận hành Meloo | Kiểm soát nội dung, xem số liệu |

Người tổ chức (lớp trưởng, giáo viên, HR, streamer) dùng phòng riêng với bộ đề tuyển chọn ở MVP; chế độ Host thuộc Phase 2.

### 2.2 Ràng buộc
- Chỉ dùng đoạn preview 30 giây từ API âm nhạc hợp lệ; không phát cả bài. Sau mỗi vòng hiện link nghe đầy đủ trên nền tảng gốc (BR-29).
- Nguồn nhạc chính dự kiến là Deezer; iTunes là dự phòng kỹ thuật, tắt mặc định (đề xuất ở ADR-003, đang ở trạng thái Đề xuất; SPK-01 đo ngày 21/10/2026). Spotify bị loại vì đã cắt preview với app mới từ 27/11/2024.
- Beta công khai chỉ dùng preview của nhà cung cấp khi LEG-02 (MEL-120) xác nhận quyền sử dụng.
- Backend không lưu lâu dài URL preview của Deezer (URL có chữ ký, hết hạn sau khoảng 15 phút) và không lưu file audio.
- Server là trọng tài: đáp án, điểm và đồng hồ do server quyết, client chỉ hiển thị.
- Đoạn nhạc cấp qua token mờ, chỉ đúng mức đã mở.
- Trạng thái phòng giữ trong bộ nhớ của một instance (ADR-007), nên khởi động lại server thì các trận đang chạy bị hủy.
- Tài khoản ADMIN đầu tiên lấy từ biến môi trường `meloo.admin.emails`.

### 2.3 Giả định
- Một người làm khoảng 20 giờ mỗi tuần, 7 sprint một tuần, hạn nộp 03/12/2026.
- Hạ tầng trong free tier (1 EC2, RDS).

## 3. Yêu cầu chức năng

Mỗi dòng kiểm chứng được bằng Acceptance Criteria ở ticket Jira tương ứng. Cột BR trỏ tới quy tắc trong `02-business-rules.md`.

### 3.1 Tài khoản

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-01 | Hệ thống cho phép chơi ngay mọi chế độ mà không cần đăng ký (khách), bằng biệt danh. | Must | S1 | BR-20, BR-22 |
| FR-02 | Hệ thống cho phép đăng nhập Google và gộp lịch sử, thống kê, huy hiệu, điểm xếp hạng của khách vào tài khoản. | Must | S4 | BR-21 |
| FR-03 | Hệ thống cho phép đăng nhập Facebook và xóa tài khoản. | Should | S5 | BR-21 |
| FR-04 | Hệ thống có hồ sơ người chơi. | Must | S4 | — |

### 3.2 Kho nhạc

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-05 | Hệ thống nhập bài từ API nhạc, phân loại theo thể loại, quốc gia, ca sĩ, thập niên; vô hiệu hóa bài lỗi preview. | Must | S1 | BR-27 |
| FR-06 | Hệ thống tìm tên bài có gợi ý, không cần gõ dấu. | Must | S1 | BR-05 |

### 3.3 Chơi đơn

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-07 | Người chơi luyện tập theo bộ lọc: nghe đoạn nhạc mở dần 5 mức, nghe lại, nghe thêm hoặc bỏ qua, đoán bằng ô gợi ý, được tính điểm theo mức đoán đúng kèm combo; sau mỗi vòng thấy nguồn nhạc và link nghe đầy đủ. | Must | S2 | BR-01 → BR-09, BR-27, BR-29 |
| FR-08 | Người chơi làm thử thách hằng ngày (một bài chung mỗi ngày) và chia sẻ kết quả không lộ tên bài. | Should | S4 | BR-07, BR-25 |

### 3.4 Chơi nhiều người

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-09 | Người chơi tạo phòng riêng (vào bằng mã hoặc link mời) hoặc phòng công khai (hiện trong danh sách phòng), chờ ở sảnh, chơi trận thời gian thực, xem bảng điểm trực tiếp, xem công bố người thắng rồi chơi lại trong cùng phòng. Chủ phòng mời được người khác ra khỏi phòng. Người vào lúc trận đang chạy chờ ở sảnh. | Must | S3 | BR-07 → BR-12, BR-14, BR-15 |
| FR-10 | Người chơi, kể cả khách, vào phòng công khai từ danh sách, hoặc ghép trận nhanh vào phòng ghép trận, có đếm ngược và gợi ý khi chờ lâu. | Must | S4 | BR-11, BR-13, BR-20 |

### 3.5 Giao tiếp

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-11 | Chat chữ trong phòng: chặn tin lộ đáp án, lọc từ cấm, báo cáo vi phạm. | Must | S3 | BR-16, BR-17 |
| FR-12 | Voice chat trong phòng riêng, mic tự tắt khi đang phát nhạc và tự bật lại sau đó. | Should | S5 | BR-18, BR-19 |

### 3.6 Tiến trình

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-13 | Hệ thống lưu lịch sử trận, hồ sơ, thống kê cá nhân cho mọi trận, kể cả phòng tự tạo. | Must | S4 | BR-23 |
| FR-14 | Hệ thống có bảng xếp hạng tuần và toàn thời gian theo tổng điểm trận xếp hạng, và bảng theo ngày của thử thách ngày. | Must | S4 | BR-20, BR-21, BR-23 → BR-25 |
| FR-15 | Hệ thống có huy hiệu thành tích. | Should | S5 | BR-23, BR-26 |

### 3.7 Quản trị

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-16 | Admin xem dashboard số liệu (người dùng, trận, KPI), quản lý kho nhạc và bộ đề. | Must | S4–S5 | BR-27 |
| FR-17 | Admin quản lý người dùng, xử lý báo cáo vi phạm, quản lý từ cấm, xem nhật ký thao tác. | Must | S5 | BR-17, BR-28 |

### 3.8 Nền tảng

| Mã | Yêu cầu | Ưu tiên | Sprint | BR |
|---|---|---|---|---|
| FR-18 | Giao diện tiếng Việt và tiếng Anh; responsive từ màn hình 360px. | Must | S1–S6 | — |
| FR-19 | Cài đặt được như ứng dụng (PWA). | Should | S5 | — |

## 4. Yêu cầu phi chức năng

NFR-01 → NFR-09 lấy từ kế hoạch. NFR-10 → NFR-15 bổ sung ngày 22/10.

| Mã | Nhóm | Mục tiêu | Kiểm bằng |
|---|---|---|---|
| NFR-01 | Tốc độ API | p95 < 300 ms | Actuator, k6 |
| NFR-02 | Độ trễ sự kiện trận | < 200 ms | k6 WebSocket |
| NFR-03 | Tải | 20 phòng × 5 người cùng lúc trên 1 EC2 free tier. Vượt ngưỡng này thì không cam kết; mở rộng ở Phase 2 (P2-06). | QA-03 |
| NFR-04 | Phát nhạc | < 1 s sau khi vòng bắt đầu (tải trước) | E2E |
| NFR-05 | Bảo mật | OWASP Top 10; HTTPS/WSS; RDS không public; secret ngoài mã nguồn | SEC-01 |
| NFR-06 | Tương thích | Chrome, Edge, Safari, Firefox bản mới; màn hình từ 360px | Playwright |
| NFR-07 | Công bằng | Đáp án, điểm, đồng hồ do server quyết; không lộ đáp án qua API hoặc WebSocket | Review, test tích hợp |
| NFR-08 | Đồng thời | Trạng thái phòng chỉ đổi trong hàng đợi của phòng; không có race condition khi hết giờ hoặc hai người đoán cùng lúc; lượt đoán đến sát lúc hết giờ được phân xử theo lúc server nhận so với mốc 45.000 ms (BR-07) | Test tích hợp |
| NFR-09 | Chất lượng mã | Coverage domain ≥ 80%; Spring Modulith không báo vi phạm ranh giới module | CI |
| NFR-10 | Người mới vào chơi | Từ lúc mở web đến khi nghe đoạn nhạc đầu tiên ≤ 30 giây, không cần đăng ký | Thử với ≥ 5 người dùng mới trong beta |
| NFR-11 | Vào phòng bằng link | Từ lúc mở link mời đến khi thấy sảnh ≤ 3 s (p95, mạng 4G giả lập) | Playwright |
| NFR-12 | Tải trang | LCP trang chủ ≤ 2,5 s trên di động | Lighthouse |
| NFR-13 | Dễ tiếp cận | Thao tác chính (đoán, nghe thêm, bỏ qua, sẵn sàng) dùng được bằng bàn phím; tương phản chữ đạt WCAG 2.1 AA | axe trong Playwright |
| NFR-14 | Độ sẵn sàng | ≥ 99% trong đợt beta 16/11–29/11 | UptimeRobot |
| NFR-15 | Khôi phục | Khôi phục dịch vụ ≤ 1 giờ sau sự cố (RTO). Khi cơ sở dữ liệu hỏng và phải khôi phục từ bản sao lưu tự động hằng ngày của RDS, mất tối đa 24 giờ dữ liệu (RPO). Server khởi động lại mà cơ sở dữ liệu còn nguyên thì không mất dữ liệu đã lưu, chỉ hủy các trận đang chạy (mục 2.2). | Diễn tập khôi phục một lần trước beta |

## 5. Truy vết yêu cầu

| Yêu cầu | Phụ lục A của đề tài | Persona (BIZ-02) |
|---|---|---|
| FR-01 → FR-04 | Mục 1 (Tài khoản) | P1, P2 |
| FR-05, FR-06 | Mục 2 (Kho nhạc) | P1, P2 |
| FR-07, FR-08 | Mục 3, 4 (Chơi đơn, thử thách ngày) | P2 |
| FR-09, FR-10 | Mục 5 (Nhiều người) | P1 |
| FR-11, FR-12 | Mục 6 (Giao tiếp) | P1 |
| FR-13 → FR-15 | Mục 7 (Thành tích, xếp hạng) | P2 |
| FR-16, FR-17 | Mục 8 (Quản trị) | P4 |
| FR-18, FR-19 | Mục 9 (Giao diện, PWA) | P1, P2 |

Bảng truy vết use case ↔ FR (UC-xx ↔ FR-01 → FR-19) nằm ở [`usecases/00-tong-quan.md` mục 4](usecases/00-tong-quan.md#4-truy-vết-uc--fr).

## 6. Còn mở

- **Quyền dùng preview cho beta công khai:** chờ LEG-02 (MEL-120). Nguồn nhạc kỹ thuật được đề xuất ở ADR-003 (Deezer chính, iTunes dự phòng); ADR-003 chỉ chuyển sang Chấp nhận khi LEG-02 kết luận "Được phép" hoặc "Được phép có điều kiện"; nếu không được phép thì phải chọn nguồn audio khác.
- **Module sở hữu lịch sử trận** (Match, Round, Guess) và phục vụ `/me/matches`: chốt ở DES-01 vì là quyết định kiến trúc. Đề xuất: progression ghi lịch sử khi nhận sự kiện `MatchFinished`, game chỉ giữ engine. Ảnh hưởng FR-13.

## 7. Câu hỏi bảo vệ

1. **FR khác BR thế nào?** FR nói hệ thống làm được gì; BR là luật của bài toán mà chức năng phải tuân theo. Một FR có thể dựa trên nhiều BR, ví dụ FR-07 (chơi đơn) dựa trên BR-01 → BR-09, BR-27, BR-29.
2. **NFR-10 (người mới vào chơi ≤ 30 giây) đo thế nào?** Trong beta, cho ít nhất 5 người chưa từng dùng Meloo mở web và bấm giờ đến khi nghe đoạn nhạc đầu tiên. Khách chơi được ngay mà không cần đăng ký nên mục tiêu này khả thi.
3. **Vì sao chỉ cam kết 20 phòng × 5 người?** Hệ thống chạy trên một EC2 free tier 1 GB RAM và giữ trạng thái phòng trong bộ nhớ (ADR-007). QA-03 đo ngưỡng này bằng k6; muốn vượt ngưỡng cần Redis và nhiều instance (P2-06).
4. **Vì sao chọn Deezer mà không chọn iTunes, dù iTunes có preview 70/70 bài?** Điều khoản iTunes cấm dùng preview làm giá trị giải trí độc lập, mà đó chính là cách game đoán nhạc dùng preview. Deezer đủ bài V-pop, tốn khoảng nửa băng thông và có API nhập hàng loạt (đề xuất ở ADR-003). Quyền dùng cho beta công khai vẫn phải được xác nhận ở LEG-02.
5. **Server khởi động lại giữa trận thì sao?** Trận đang chạy bị hủy vì trạng thái phòng nằm trong bộ nhớ (ADR-007); lịch sử các trận đã xong nằm trong cơ sở dữ liệu nên không mất. Mốc mất tối đa 24 giờ dữ liệu (RPO, NFR-15) chỉ áp dụng khi chính cơ sở dữ liệu hỏng và phải khôi phục từ bản sao lưu hằng ngày.
