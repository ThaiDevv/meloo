# Kịch Bản Demo Production, Video Dự Phòng & Tập Dượt Bấm Giờ (DEF-00 / MEL-116)

> **Mục đích:** Hướng dẫn chi tiết các bước Thái thực hiện trước và trong buổi báo cáo giữa kỳ: (1) xác nhận lịch với giảng viên, (2) kịch bản demo trực tiếp trên production đi trọn từ đầu đến màn Tổng kết trận / Bục vinh danh, (3) quay video dự phòng 1–2 phút, và (4) biên bản tập dượt bấm giờ.

---

## 1. Việc cần xác nhận với giảng viên trước buổi báo cáo (`[HỎI THÁI]`)
- [ ] Hỏi giảng viên hướng dẫn ngày báo cáo giữa kỳ chính thức và yêu cầu cụ thể (thời lượng trình bày + hỏi đáp, hình thức trực tiếp trên lớp hay trực tuyến).
- [ ] Cập nhật lại hạn (`Due date`) của ticket `MEL-116` trên Jira theo lịch thầy chốt (hoặc đóng ticket kèm lý do nếu lớp không tổ chức buổi báo cáo giữa kỳ).
- [ ] Chụp màn hình bảng Jira Active Sprint (S3) và chèn vào **Slide 7** trong [`slides.md`](slides.md).

---

## 2. Kịch bản Demo trực tiếp trên Production (3,5 – 4 phút)

### Chuẩn bị trước giờ lên bục (5 phút trước khi gọi tên)
1. Mở sẵn 2 cửa sổ trình duyệt (Cửa sổ 1: Chrome thường — đóng vai **Người chơi A / Chủ phòng**; Cửa sổ 2: Chrome Incognito đặt cạnh nhau 50/50 màn hình — đóng vai **Người chơi B**).
2. Mở sẵn tab **DevTools $\rightarrow$ Network** (lọc `Fetch/XHR` và `WS`) ở Cửa sổ 1 để chứng minh nguyên tắc chống gian lận (`NFR-07`, `ADR-008`).
3. Kiểm tra âm lượng loa máy tính / HDMI máy chiếu; mở sẵn tệp video dự phòng ở tab bên cạnh.

### Luồng Demo 1 · Chơi đơn đầu-cuối 5 vòng (`UC-01`, `UC-07`, `UC-08` — mục tiêu 105 giây)
1. **Vào chơi tức thì (`FR-01, NFR-10`):** Tại trang chủ Production, nhập biệt danh `ThaiDemo` $\rightarrow$ bấm vào chơi ngay không cần mật khẩu (chứng minh khách vào nghe đoạn nhạc đầu tiên trong $\le 30\text{s}$).
2. **Cấu hình trận 5 vòng (`FR-05, BR-09, BR-23`):** Tại màn thiết lập Chơi đơn (`/solo`), chọn số vòng **5 vòng (`roundCount = 5`)** và chọn một bộ đề nhạc Việt $\rightarrow$ bấm bắt đầu trận Luyện tập.
3. **Vòng 1 — Nghe mở dần, Gợi ý ca sĩ & Chống lộ đáp án (`BR-01, BR-06, ADR-008`):**
   - Bấm phát mức `2s`. Chỉ vào tab **Network** cho thầy thấy request `/api/v1/audio/{token}` chỉ trả về ~32 KB dữ liệu âm thanh và payload JSON tuyệt đối không có tên bài hát hay `trackId`.
   - Gõ và chọn một bài hát khác của **cùng ca sĩ chính** $\rightarrow$ giao diện hiện gợi ý vàng **"Đúng ca sĩ!"** (`BR-06`) và tự động mở sang mức `5s` (`BR-04`).
   - Gõ tên bài hát đáp án không dấu vào ô gợi ý (`BR-05`) $\rightarrow$ đoán đúng ở mức `5s` (800 điểm) $\rightarrow$ xem **màn kết quả Vòng 1** hiển thị tên bài, nghệ sĩ và link nghe trên Deezer (`BR-29`).
4. **Vòng 2 — Minh họa chuỗi Combo (`BR-08`):** Bấm sang Vòng 2, đoán đúng ngay ở mức `2s` (hoặc `5s`) để nối chuỗi combo 2 vòng liên tiếp ở mức $\le 5\text{s}$ và nhận hệ số thưởng **+10% điểm cơ bản** (`BR-08`).
5. **Vòng 3–5 $\rightarrow$ Màn Tổng kết trận (`MatchSummary`, `UC-07`):**
   - Hoàn tất nhanh Vòng 3–5 (đoán nhanh hoặc bấm `Nghe thêm` tới mức `30s` rồi bấm `Bỏ qua` theo `BR-04`, mất ~15 giây cho 3 vòng; *mẹo tiết kiệm giờ: cũng có thể chơi sẵn tới Vòng 4 trước giờ lên bục nhờ cơ chế giữ phiên 30 phút `BR-23`*).
   - Sau khi kết thúc Vòng 5, bấm nút xem kết quả toàn trận để mở màn **Tổng kết trận (`MatchSummary`)**: chỉ cho thầy thấy tổng điểm toàn trận, số vòng đoán đúng, chuỗi combo cao nhất và danh sách tổng hợp cả 5 bài hát đã chơi kèm link Deezer.

### Luồng Demo 2 · Phòng nhiều người đầu-cuối 5 vòng & Chat chặn lộ đáp án (`UC-12`, `UC-13`, `UC-18`, `UC-21` — mục tiêu 120 giây)
1. **Tạo phòng 5 vòng & Vào bằng link (`BR-11, NFR-11`):** Ở Cửa sổ 1 (`ThaiDemo`), bấm **Tạo phòng** (sinh mã 6 ký tự không có `0, O, 1, I, L`), chọn cài đặt **5 vòng (`roundCount = 5`)**, sao chép link mời và dán sang Cửa sổ 2 (`BanChoiB`).
2. **Sảnh chờ & Bắt đầu trận (`BR-12`):** Cửa sổ 2 bấm **Sẵn sàng** $\rightarrow$ Cửa sổ 1 (Chủ phòng) bấm **Bắt đầu trận**.
3. **Vòng 1 — Đồng bộ giờ server, Ẩn đáp án & Chặn chat lộ bài (`BR-07, BR-16, NFR-04`):**
   - Sau pha chuẩn bị nạp trước audio (`ROUND_PREPARE`), đồng hồ 45 giây đếm ngược đồng bộ trên cả 2 cửa sổ.
   - Ở Cửa sổ 1, đoán đúng ngay ở mức `2s`: chỉ cho thầy thấy trên Cửa sổ 2 hiện thông báo `"ThaiDemo đúng ở mức 2s"` kèm điểm mới (có thưởng tốc độ theo mili-giây còn lại), nhưng **Cửa sổ 2 vẫn đang chơi tiếp và không hề thấy tên bài đáp án**.
   - Khi Cửa sổ 2 chưa xong vòng, dùng Cửa sổ 1 gõ tên bài hát đáp án (có dấu hoặc không dấu) vào khung Chat phòng $\rightarrow$ server chặn tin nhắn ngay lập tức, chỉ hiện cảnh báo cho người gửi và không phát sang Cửa sổ 2 (`BR-16`).
   - Ở Cửa sổ 2, bấm `Nghe thêm` tới mức `30s` rồi bấm **Bỏ qua** (`BR-04`) $\rightarrow$ Vòng 1 kết thúc sớm ngay khi cả hai người đã xong lượt (`BR-07`), cả hai cửa sổ hiện **màn kết quả Vòng 1 (`reveal`)** và bảng điểm tạm thời.
4. **Vòng 2–5 $\rightarrow$ Bục vinh danh (`Podium` ở phase `finished`) & Chơi lại (`BR-10, BR-12`):**
   - Ở Vòng 2–5, cả hai cửa sổ đoán nhanh hoặc bấm `Bỏ qua` (vì vòng tự kết thúc sớm ngay khi cả 2 người hoàn tất lượt theo `BR-07`, không phải đợi hết 45s mỗi vòng nên chỉ mất ~30 giây cho 4 vòng).
   - Kết thúc Vòng 5, phòng chuyển từ `reveal` sang phase **`finished`**, hiển thị **Bục vinh danh (`Podium`)** công bố thứ hạng chung cuộc (`BR-10`) và nút **Chơi lại (`Rematch`)** đưa cả nhóm về sảnh chờ giữ nguyên mã phòng (`BR-12`).

---

## 3. Hướng dẫn quay Video dự phòng (1 – 2 phút)
- **Thời lượng chuẩn:** 90 – 120 giây, độ phân giải 1080p (MP4), có thu âm thanh hệ thống (System Audio).
- **Bố cục cảnh quay (đảm bảo có đủ cả kết quả từng vòng lẫn màn Tổng kết trận / Bục vinh danh):**
  - `00:00 – 00:50` (50 giây — Chơi đơn 5 vòng đầu-cuối): Vào bằng khách $\rightarrow$ chọn **5 vòng** & bộ đề $\rightarrow$ ở Vòng 1 soi Network `/api/v1/audio/{token}`, thử gợi ý **"Đúng ca sĩ!"** (`BR-06`), gõ không dấu đoán đúng xem kết quả Vòng 1 $\rightarrow$ **tua nhanh `4x` qua Vòng 2–4** $\rightarrow$ hoàn tất Vòng 5 và dừng 5 giây tại màn **Tổng kết trận (`MatchSummary`)**.
  - `00:50 – 01:50` (60 giây — Phòng nhiều người 5 vòng đầu-cuối): Hai cửa sổ cạnh nhau tạo phòng **5 vòng** $\rightarrow$ vào bằng link $\rightarrow$ ở Vòng 1 Cửa sổ 1 đoán đúng ở `2s` (Cửa sổ 2 thấy thông báo ẩn đáp án), thử chat tên bài bị chặn (`BR-16`), Cửa sổ 2 bỏ qua để xem màn `reveal` Vòng 1 $\rightarrow$ **tua nhanh `4x` qua Vòng 2–4** $\rightarrow$ hoàn tất Vòng 5 và dừng 5 giây tại màn **Bục vinh danh (`Podium`)** + nút **Chơi lại**.
- **Vị trí lưu / liên kết video sau khi quay:** `[CẦN SỐ LIỆU: Thái điền tên file hoặc link Google Drive/YouTube Unlisted sau khi quay]`

---

## 4. Biên bản Tập dượt có bấm giờ (Rehearsal Timer Log)

> Yêu cầu ticket: **Tập dượt 1 lần có bấm giờ.** Thái chạy thử từ Slide 1 đến hết phần Demo, ghi mốc thời gian thực tế vào bảng dưới đây trước buổi báo cáo:

| Phần trình bày | Nội dung | Thời lượng mục tiêu | Thời lượng bấm giờ thực tế | Ghi chú điều chỉnh |
|---|---|---|---|---|
| **Phần 1 · Mở đầu & Bài toán** | Slide 1 (Bìa) + Slide 2 (Bài toán & Đối thủ) + Slide 3 (Khảo sát H1–H4) | 02 phút 15 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 2 · Phạm vi & Thiết kế** | Slide 4 (MoSCoW & BR) + Slide 5 (Kiến trúc C4) + Slide 6 (Use Case & Class/ERD) | 03 phút 00 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 3 · Tiến độ & Demo** | Slide 7 (Tiến độ S0–S3) + Slide 8 (Demo trực tiếp 2 luồng 5 vòng tới Tổng kết & Podium) | 04 phút 15 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 4 · Rủi ro & Kế hoạch** | Slide 9 (Ma trận rủi ro) + Slide 10 (Kế hoạch S4–S6) | 01 phút 30 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Tổng cộng** | **Toàn bộ bài báo cáo giữa kỳ (10 slide + Demo đầu-cuối)** | **~11 phút 00 giây** | **`[CẦN SỐ LIỆU: tổng thời gian]`** | **Ngày tập dượt:** `[CẦN SỐ LIỆU]` |
