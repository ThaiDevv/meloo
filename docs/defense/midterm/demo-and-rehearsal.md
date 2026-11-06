# Kịch Bản Demo Production, Video Dự Phòng & Tập Dượt Bấm Giờ (DEF-00 / MEL-116)

> **Mục đích:** Hướng dẫn chi tiết các bước Thái thực hiện trước và trong buổi báo cáo giữa kỳ: (1) xác nhận lịch với giảng viên, (2) kịch bản demo trực tiếp trên production, (3) quay video dự phòng 1–2 phút, và (4) biên bản tập dượt bấm giờ.

---

## 1. Việc cần xác nhận với giảng viên trước buổi báo cáo (`[HỎI THÁI]`)
- [ ] Hỏi giảng viên hướng dẫn ngày báo cáo giữa kỳ chính thức và yêu cầu cụ thể (thời lượng trình bày + hỏi đáp, hình thức trực tiếp trên lớp hay trực tuyến).
- [ ] Cập nhật lại hạn (`Due date`) của ticket `MEL-116` trên Jira theo lịch thầy chốt (hoặc đóng ticket kèm lý do nếu lớp không tổ chức buổi báo cáo giữa kỳ).
- [ ] Chụp màn hình bảng Jira Active Sprint (S3) và chèn vào **Slide 7** trong [`slides.md`](slides.md).

---

## 2. Kịch bản Demo trực tiếp trên Production (3 – 4 phút)

### Chuẩn bị trước giờ lên bục (5 phút trước khi gọi tên)
1. Mở sẵn 2 cửa sổ trình duyệt (Cửa sổ 1: Chrome thường — đóng vai **Người chơi A / Chủ phòng**; Cửa sổ 2: Chrome Incognito đặt cạnh nhau 50/50 màn hình — đóng vai **Người chơi B**).
2. Mở sẵn tab **DevTools $\rightarrow$ Network** (lọc `Fetch/XHR` và `WS`) ở Cửa sổ 1 để chứng minh nguyên tắc chống gian lận (`NFR-07`, `ADR-008`).
3. Kiểm tra âm lượng loa máy tính / HDMI máy chiếu; mở sẵn tệp video dự phòng ở tab bên cạnh.

### Luồng Demo 1 · Chơi đơn đầu-cuối (`UC-01`, `UC-07`, `UC-08` — mục tiêu 90 giây)
1. **Vào chơi tức thì (`FR-01, NFR-10`):** Tại trang chủ Production, nhập biệt danh `ThaiDemo` $\rightarrow$ bấm vào chơi ngay không cần mật khẩu (chứng minh khách vào nghe đoạn nhạc đầu tiên trong $\le 30\text{s}$).
2. **Chọn bộ đề (`FR-05, BR-23`):** Chọn tab Bộ đề (ví dụ *V-pop*) $\rightarrow$ bắt đầu ván Chơi đơn (chế độ Luyện tập).
3. **Nghe mở dần & Chống lộ đáp án (`BR-01, ADR-008`):**
   - Bấm phát mức `2s`. Chỉ vào tab **Network** cho thầy thấy request `/api/v1/audio/{token}` chỉ trả về ~32 KB dữ liệu âm thanh và payload JSON tuyệt đối không có tên bài hát hay `trackId`.
   - Gõ thử tên một bài khác của cùng ca sĩ chính $\rightarrow$ giao diện hiện gợi ý vàng **"Đúng ca sĩ!"** (`BR-06`) và tự động mở sang mức `5s` (`BR-04`).
4. **Tìm bài không dấu & Chấm điểm (`BR-05, BR-02, BR-08`):** Gõ tên bài hát không dấu vào ô gợi ý $\rightarrow$ chọn đáp án đúng $\rightarrow$ hệ thống công bố điểm cơ bản + combo, tên bài, ca sĩ và liên kết nghe bản gốc trên Deezer (`BR-29`).

### Luồng Demo 2 · Phòng nhiều người realtime & Chat chặn lộ đáp án (`UC-12`, `UC-13`, `UC-18`, `UC-21` — mục tiêu 120 giây)
1. **Tạo phòng & Vào bằng link (`BR-11, NFR-11`):** Ở Cửa sổ 1 (`ThaiDemo`), bấm **Tạo phòng** (sinh mã 6 ký tự không có `0, O, 1, I, L`), bấm **Sao chép link mời** và dán sang Cửa sổ 2 (`BanChoiB`).
2. **Sảnh chờ & Bắt đầu trận (`BR-12`):** Cửa sổ 2 bấm **Sẵn sàng** $\rightarrow$ Cửa sổ 1 (Chủ phòng) bấm **Bắt đầu trận**.
3. **Thi đấu đồng bộ giờ server (`BR-07, NFR-04`):**
   - Sau pha chuẩn bị nạp trước audio (`ROUND_PREPARE`), đồng hồ 45 giây đếm ngược đồng bộ trên cả 2 cửa sổ.
   - Ở Cửa sổ 1, đoán đúng ngay ở mức `2s`: chỉ cho thầy thấy trên Cửa sổ 2 hiện thông báo `"ThaiDemo đúng ở mức 2s"` kèm điểm mới (có thưởng tốc độ theo mili-giây còn lại), nhưng **Cửa sổ 2 vẫn đang chơi tiếp và không hề thấy tên bài đáp án**.
4. **Chặn chat lộ đáp án (`BR-16`):** Khi Cửa sổ 2 chưa đoán xong, thử dùng Cửa sổ 1 gõ tên bài hát đáp án (có dấu hoặc không dấu) vào khung Chat phòng $\rightarrow$ server chặn tin nhắn ngay lập tức, hiển thị cảnh báo riêng cho người gửi và không phát sang Cửa sổ 2.
5. **Kết thúc vòng & Bục vinh danh:** Cửa sổ 2 bấm **Bỏ qua** ở mức `30s` (`BR-04`) $\rightarrow$ vòng kết thúc sớm, cả hai cửa sổ cùng hiện đáp án và cập nhật bảng điểm trực tiếp.

---

## 3. Hướng dẫn quay Video dự phòng (1 – 2 phút)
- **Thời lượng chuẩn:** 90 – 120 giây, độ phân giải 1080p (MP4), có thu âm thanh hệ thống (System Audio).
- **Bố cục cảnh quay:**
  - `00:00 – 00:45` (45 giây): Vào bằng khách $\rightarrow$ chọn bộ đề $\rightarrow$ nghe mức 2s, soi nhanh Network `/api/v1/audio/{token}` $\rightarrow$ gõ không dấu đoán đúng 1 vòng chơi đơn.
  - `00:45 – 01:45` (60 giây): Hai cửa sổ cạnh nhau tạo phòng $\rightarrow$ vào bằng link $\rightarrow$ đoán đúng ở Cửa sổ 1 (Cửa sổ 2 thấy thông báo ẩn đáp án) $\rightarrow$ thử chat tên bài bị chặn (`BR-16`) $\rightarrow$ bảng điểm tổng kết và bục vinh danh.
- **Vị trí lưu / liên kết video sau khi quay:** `[CẦN SỐ LIỆU: Thái điền tên file hoặc link Google Drive/YouTube Unlisted sau khi quay]`

---

## 4. Biên bản Tập dượt có bấm giờ (Rehearsal Timer Log)

> Yêu cầu ticket: **Tập dượt 1 lần có bấm giờ.** Thái chạy thử từ Slide 1 đến hết phần Demo, ghi mốc thời gian thực tế vào bảng dưới đây trước buổi báo cáo:

| Phần trình bày | Nội dung | Thời lượng mục tiêu | Thời lượng bấm giờ thực tế | Ghi chú điều chỉnh |
|---|---|---|---|---|
| **Phần 1 · Mở đầu & Bài toán** | Slide 1 (Bìa) + Slide 2 (Bài toán & Đối thủ) + Slide 3 (Khảo sát H1–H4) | 02 phút 15 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 2 · Phạm vi & Thiết kế** | Slide 4 (MoSCoW & BR) + Slide 5 (Kiến trúc C4) + Slide 6 (Use Case & Class/ERD) | 03 phút 00 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 3 · Tiến độ & Demo** | Slide 7 (Tiến độ S0–S3) + Slide 8 (Trình chiếu Demo trực tiếp 2 luồng) | 04 phút 00 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Phần 4 · Rủi ro & Kế hoạch** | Slide 9 (Ma trận rủi ro) + Slide 10 (Kế hoạch S4–S6) | 01 phút 30 giây | `[CẦN SỐ LIỆU: ... phút ... giây]` | `[CẦN SỐ LIỆU]` |
| **Tổng cộng** | **Toàn bộ bài báo cáo giữa kỳ (10 slide + Demo)** | **~10 phút 45 giây** | **`[CẦN SỐ LIỆU: tổng thời gian]`** | **Ngày tập dượt:** `[CẦN SỐ LIỆU]` |
