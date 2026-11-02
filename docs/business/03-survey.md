# BIZ-05 · Thiết kế và phân tích khảo sát nhu cầu người dùng

> Ticket: MEL-53 · Trạng thái: **Khung phân tích sẵn sàng, chờ số liệu đóng khảo sát (08/11/2026)** · Tác giả: Trần Văn Thái (Antigravity thực hiện theo ủy quyền)  
> Kế thừa & đối chiếu: `docs/business/01-problem.md` (BIZ-02, mục 4), `docs/business/02-competitors.md` (BIZ-04), BIZ-03 (MEL-19)  
> **Nguồn khảo sát:** Biểu mẫu Google Form trực tuyến (phát hành từ 25/10/2026 đến hạn chốt Sprint S2 08/11/2026 theo kế hoạch đo lường tại BIZ-02 mục 7 / `01-problem.md:111`).

---

## 1. Mục tiêu, phương pháp và hạn chế của mẫu khảo sát

### 1.1. Mục tiêu
- Chuẩn bị khung phân tích định lượng và tiêu chí kiểm chứng thực nghiệm cho 4 giả thuyết bài toán (**H1, H2, H3, H4**) theo đúng các tiêu chí và ngưỡng xác nhận đã chốt tại BIZ-02 mục 4 (chốt ngày 19/10/2026, không chỉnh sửa sau khi có số liệu).
- Khảo sát sự sẵn sàng chi trả và hành vi chia sẻ để làm cơ sở cho kế hoạch khởi nghiệp (BIZ-08) và tinh chỉnh backlog Sprint S2–S4.
- Đảm bảo tính minh bạch và liêm chính học thuật theo quy ước `meloo-docs`: số liệu thực tế chỉ được ghi nhận khi có bản trích xuất (export) chính thức từ biểu mẫu sau khi kết thúc kỳ khảo sát.

### 1.2. Phương pháp thu thập và làm sạch dữ liệu
- **Công cụ:** Biểu mẫu Google Form trực tuyến gồm 12 câu hỏi (K1–K12) được thiết kế từ ticket BIZ-03 (MEL-19).
- **Thời gian triển khai:** Từ 25/10/2026 đến hết ngày 08/11/2026 (14 ngày, khớp mốc đo KPI khảo sát tại `01-problem.md:111`).
- **Kênh phân phối:** Các nhóm học tập sinh viên (Đại học Quốc gia, Bách Khoa, CNTT), nhóm cộng đồng nghe nhạc trên Facebook, mạng lưới bạn bè và đồng nghiệp qua Messenger/Discord.
- **Tiêu chuẩn làm sạch dữ liệu (Data Cleaning Protocol):**
  - Loại bỏ các phản hồi có thời gian hoàn thành dưới 35 giây và chọn cùng một đáp án đơn điệu xuyên suốt các câu hỏi.
  - Loại bỏ các phản hồi có câu trả lời văn bản tự do mang tính spam/vô nghĩa hoặc có dữ liệu mâu thuẫn nội tại (ví dụ: chọn chưa từng chơi game đoán nhạc nhưng lại đánh giá các tính năng không hài lòng ở game đã chơi).
- **Quy mô mẫu:** Mục tiêu tối thiểu **$N \ge 50$ phản hồi hợp lệ**. Số liệu chính thức sẽ được trích xuất và công bố vào ngày 08/11/2026: `[CẦN SỐ LIỆU: xuất dữ liệu Google Sheets vào 08/11/2026]`.

### 1.3. Hạn chế của mẫu khảo sát (Sample Limitations)
- **Phương pháp lấy mẫu thuận tiện (Convenience Sampling):** Khảo sát được phân phối chủ yếu qua các nhóm học tập sinh viên CNTT, mạng lưới bạn bè và các hội nhóm nghe nhạc trên mạng xã hội, do đó mẫu mang tính đại diện cao cho đối tượng người trẻ và sinh viên nhưng chưa bao quát toàn bộ các tầng lớp lứa tuổi khác ngoài xã hội.
- **Thiên lệch tự chọn (Self-selection Bias):** Những người có sẵn sự quan tâm đến âm nhạc hoặc thường xuyên chơi game nhóm có xu hướng mở biểu mẫu và hoàn thành khảo sát cao hơn người ít nghe nhạc.
- **Ý nghĩa đối với đồ án:** Với mô hình phát triển tinh gọn (Lean Startup) cho đồ án môn học, kết quả khảo sát đóng vai trò kiểm chứng định tính và định hướng ban đầu cho MVP. Các giả thuyết sẽ tiếp tục được đo lường và kiểm chứng chéo bằng hành vi thực tế của người chơi trong giai đoạn thử nghiệm Beta kín (BIZ-06) và Beta công khai (BIZ-07).

---

## 2. Bảng đối chiếu và kiểm chứng giả thuyết (H1–H4)

Toàn bộ các ngưỡng dưới đây được **giữ nguyên 100% như đã công bố tại BIZ-02 mục 4** trước khi phát hành khảo sát.

| Mã | Giả thuyết bài toán | Tiêu chí & Ngưỡng xác nhận (BIZ-02) | Kết quả thực tế (Đo vào 08/11/2026) | Trạng thái đánh giá |
|---|---|---|---|---|
| **H1** | Nhóm bạn thiếu hoạt động online **ngắn, vào ngay**; rào cản lớn nhất là phải cài app hoặc đăng ký. | **$\ge$ 50%** chơi cùng bạn bè từ 1 lần/tháng (K1) **VÀ** **$\ge$ 40%** chọn "phải cài app/đăng ký" hoặc "mất thời gian rủ/hướng dẫn" (K3). | `[CẦN SỐ LIỆU: tỷ lệ K1 và K3]` | **Chờ số liệu (08/11)** |
| **H2** | Người chơi muốn đoán **nhạc Việt** với **giao diện tiếng Việt**; game hiện có chưa đáp ứng. | **$\ge$ 60%** chọn V-pop trong 2 lựa chọn (K5) **VÀ** trong nhóm từng chơi, **$\ge$ 40%** chọn "ít nhạc Việt" hoặc "không có tiếng Việt" (K6). | `[CẦN SỐ LIỆU: tỷ lệ K5 và K6]` | **Chờ số liệu (08/11)** |
| **H3** | Người nghe nhạc thích **thử thách ngắn hằng ngày** và **so tài, khoe kết quả** (hiệu ứng Wordle/Heardle). | **$\ge$ 30%** trả lời "Có" ở K7 (chơi game hằng ngày) **VÀ** **$\ge$ 40%** trả lời "Có" ở K8 (từng chia sẻ kết quả). | - Nghĩa nghiêm ngặt (chơi mỗi ngày): `[CẦN SỐ LIỆU]`<br>- Nghĩa mở rộng ($\ge$ vài ngày/tuần): `[CẦN SỐ LIỆU]`<br>- Từng chia sẻ kết quả (K8): `[CẦN SỐ LIỆU]` | **Chờ số liệu (08/11)** |
| **H4** | Người tổ chức (lớp trưởng, giáo viên, HR, streamer) cần **hoạt động khởi động 10 phút** có bộ đề theo chủ đề. *(Cơ hội Phase 2)* | **$\ge$ 20%** từng làm người tổ chức (K9) **VÀ** trong nhóm đó có **$\ge$ 50%** quan tâm dùng game đoán nhạc (K10). | - Từng tổ chức / quản trò: `[CẦN SỐ LIỆU]`<br>- Nhóm tổ chức quan tâm dùng thử: `[CẦN SỐ LIỆU]` | **Chờ số liệu (08/11)** |

---

## 3. Cấu trúc câu hỏi K1–K12 và quy tắc thống kê

### 3.1. Đặc điểm nhân khẩu học (K12)
- **Độ tuổi:** `< 18`, `18–22 tuổi` (sinh viên), `23–26 tuổi` (mới đi làm), `> 26 tuổi`.
- **Nghề nghiệp:** Sinh viên / Học sinh, Nhân viên văn phòng, Khác.
- **Bảng dữ liệu:**

| Nhóm tuổi | Số lượng (người) | Tỷ lệ (%) |
|---|---|---|
| 18–22 tuổi (Sinh viên) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| 23–26 tuổi (Đi làm) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Khác (< 18 hoặc > 26) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| **Tổng cộng** | `[CẦN SỐ LIỆU]` | **100,0%** |

---

### 3.2. K1: Thói quen chơi game online cùng bạn bè
- **Câu hỏi:** *Trong 3 tháng qua, bạn chơi game online cùng bạn bè bao lâu một lần?* (Câu hỏi đơn)
- **Quy tắc xác nhận H1 vế 1:** Tính tổng tỷ lệ của 3 mức: "Hằng ngày", "Vài lần một tuần", "1–3 lần một tháng" ($\ge 50\%$).

| Tần suất | Số lượng (người) | Tỷ lệ (%) | Tỷ lệ tích lũy (%) |
|---|---|---|---|
| Hằng ngày | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Vài lần một tuần | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| 1–3 lần một tháng | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Hiếm khi (< 1 lần/tháng) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Không bao giờ | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` | 100,0% |

---

### 3.3. K2: Kênh liên lạc khi chơi cùng nhau
- **Câu hỏi:** *Khi chơi game cùng bạn bè, bạn thường dùng gì để nói chuyện / trao đổi?* (Nhiều lựa chọn)
- **Mục đích:** Khảo sát nhu cầu sử dụng kênh thoại (voice) và tỷ lệ người chơi phải dùng ứng dụng ngoài (Discord, Messenger/Zalo).

| Kênh liên lạc | Số người chọn | Tỷ lệ trên tổng mẫu (%) |
|---|---|---|
| Discord (bật kênh voice) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Messenger / Zalo (gọi nhóm hoặc chat chữ) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Voice chat tích hợp sẵn trong game | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Chỉ chat chữ trong game / không nói chuyện | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Kênh khác | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.4. K3: Rào cản khi rủ bạn bè chơi game mới
- **Câu hỏi:** *Điều gì khiến bạn ngại hoặc từ chối thử một game mới cùng bạn bè?* (Nhiều lựa chọn, chọn tối đa 3)
- **Quy tắc xác nhận H1 vế 2:** Tỷ lệ người chọn ít nhất một trong hai rào cản: "phải cài app/đăng ký" HOẶC "mất thời gian rủ/hướng dẫn" ($\ge 40\%$).

| Rào cản chính | Số người chọn | Tỷ lệ (%) |
|---|---|---|
| Phải tải và cài đặt ứng dụng (nặng máy, tốn dung lượng) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Bắt buộc tạo tài khoản / đăng ký / xác thực rườm rà | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Mất thời gian rủ rê, chờ đợi tải và hướng dẫn luật chơi | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Thiết bị không tương thích (máy yếu, khác hệ điều hành) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Game bắt trả phí mua bản quyền | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Khác | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.5. K4 & K5: Kinh nghiệm và thể loại nhạc mong muốn
- **K4:** *Bạn đã từng chơi game đoán bài hát / thử tài âm nhạc nào chưa?* (Câu hỏi đơn)
- **K5:** *Nếu chơi game đoán bài hát, bạn muốn đoán thể loại nhạc nào nhất?* (Chọn tối đa 2 thể loại)
- **Quy tắc xác nhận H2 vế 1:** Tỷ lệ người chọn ít nhất một lựa chọn V-pop (V-pop hiện đại hoặc V-pop xưa) trên tổng số người tham gia ($\ge 60\%$).

| Thể loại âm nhạc (K5) | Số người chọn | Tỷ lệ (%) |
|---|---|---|
| Nhạc trẻ Việt Nam (V-pop 2010s–2020s) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Nhạc Âu Mỹ (US-UK Pop, Rock, Hip-hop) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Nhạc V-pop xưa / hoài niệm (1990s–2000s) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Nhạc Hàn Quốc (K-pop) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Nhạc phim / Anime / Game OST | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Khác | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.6. K6: Điểm còn thiếu ở các game đoán nhạc đã từng chơi
- **Câu hỏi:** *Những game đoán nhạc bạn từng chơi còn thiếu điều gì hoặc khiến bạn chưa hài lòng?* (Dành cho nhóm đã từng chơi ở K4, nhiều lựa chọn)
- **Quy tắc xác nhận H2 vế 2:** Trong nhóm từng chơi, tỷ lệ chọn "ít nhạc Việt" hoặc "không có tiếng Việt" ($\ge 40\%$).

| Hạn chế / Điểm chưa hài lòng | Số người chọn | Tỷ lệ trên nhóm từng chơi (%) |
|---|---|---|
| Ít bài hát Việt Nam / bài không hợp gu giới trẻ | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Giao diện không có tiếng Việt / gõ tìm bài tiếng Việt bị lỗi dấu | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Bắt buộc phải tải app / đăng ký tài khoản rườm rà | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Chỉ chơi được một mình, thiếu chế độ phòng cùng bạn bè | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Cách đoán trắc nghiệm 4 đáp án dễ đoán mò, thiếu kịch tính | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Thiếu âm thanh thoại (voice) tích hợp khi chơi nhóm | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Khác | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.7. K7 & K8: Thói quen chơi mini-game và chia sẻ kết quả
- **K7:** *Bạn có thói quen chơi các mini-game ngắn mỗi ngày (như Wordle, Heardle, giải đố...) không?* (Câu hỏi đơn)
  - Quy tắc đếm: Trình bày cả 2 cách hiểu: (1) Nghiêm ngặt (chỉ tính "chơi thường xuyên mỗi ngày", ngưỡng $\ge 30\%$); (2) Mở rộng (tính cả "thỉnh thoảng vài ngày/tuần").
- **K8:** *Bạn có từng chụp ảnh / chia sẻ kết quả game hoặc thành tích của mình lên MXH hoặc nhóm chat không?* (Câu hỏi đơn, ngưỡng $\ge 40\%$).

| Tần suất chơi mini-game hằng ngày (K7) | Số lượng | Tỷ lệ (%) |
|---|---|---|
| Chơi thường xuyên mỗi ngày | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Thỉnh thoảng chơi vài ngày một tuần | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Hiếm khi hoặc không bao giờ | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

| Mức độ chia sẻ kết quả (K8) | Số lượng | Tỷ lệ (%) |
|---|---|---|
| Thường xuyên chia sẻ | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Thỉnh thoảng khi đạt điểm cao / kỷ lục | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Chưa bao giờ chia sẻ | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.8. K9 & K10: Nhu cầu của người tổ chức hoạt động
- **K9:** *Bạn có từng làm người tổ chức hoạt động, quản trò, host cho lớp học, CLB, nhóm bạn hoặc công ty không?* (Câu hỏi đơn, ngưỡng $\ge 20\%$).
- **K10:** *Nếu có một web game đoán nhạc mở nhanh bằng link làm phần khởi động 10 phút, bạn có muốn sử dụng không?* (Hỏi nhóm từng tổ chức ở K9, ngưỡng $\ge 50\%$).
- **Quy tắc mã hóa:** Nhóm "quan tâm" được tính từ 2 mức tích cực ("Rất quan tâm và chắc chắn muốn dùng" + "Quan tâm, có thể dùng thử"), đối lập với mức phủ định duy nhất ("Không quan tâm / chưa thấy cần thiết").

| Mức độ quan tâm (K10) | Số người | Tỷ lệ (%) |
|---|---|---|
| Rất quan tâm và chắc chắn muốn dùng thử | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Quan tâm, có thể dùng nếu thao tác nhanh gọn | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Không quan tâm / chưa thấy cần thiết | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

### 3.9. K11: Ý kiến về tính năng trả phí & Mức giá sẵn sàng chi trả
(Phục vụ nghiên cứu mô hình kinh doanh cho BIZ-08)

| Khoảng giá đề xuất hằng tháng | Số lượng (người) | Tỷ lệ (%) |
|---|---|---|
| 0 đ (Chỉ dùng bản miễn phí hoàn toàn) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| 19.000 – 29.000 đ/tháng (Mức sinh viên) | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| 49.000 – 69.000 đ/tháng | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |
| Trên 99.000 đ/tháng | `[CẦN SỐ LIỆU]` | `[CẦN SỐ LIỆU]` |

---

## 4. Bốn hướng phân tích trọng tâm (Key Hypotheses & Insights to watch)

1. **Trải nghiệm "Zero-Friction" (Không ma sát):**
   Đo lường chính xác tỷ lệ người dùng gặp trở ngại vì phải tải app và đăng ký tài khoản (K3) để khẳng định giá trị cốt lõi của tính năng chơi ngay qua link với tư cách Khách (Guest).

2. **Thị hiếu V-pop & Công cụ tìm kiếm bài hát không dấu:**
   Xác định mức độ vượt trội của nhu cầu nhạc Việt (K5) và mức độ bức xúc của người chơi với các game nước ngoài thiếu nhạc Việt (K6), từ đó định vị bộ đề nhạc Việt là trọng tâm hàng đầu của sản phẩm.

3. **Cơ chế lan tỏa qua chia sẻ kết quả (Viral Loop):**
   Kiểm chứng tỷ lệ người có thói quen chia sẻ kết quả (K8) để thiết kế nút chia sẻ dạng ký tự emoji cho tính năng Thử thách hằng ngày (Daily Challenge).

4. **Nhu cầu Voice đàm thoại và tiềm năng mở rộng Host Mode (Phase 2):**
   Khảo sát thói quen sử dụng Discord (K2) và mức độ hứng thú của nhóm người làm quản trò/tổ chức (K9, K10) nhằm chuẩn bị cơ sở cho các gói tính năng mở rộng trong tương lai.

---

## 5. Đề xuất điều chỉnh Backlog và lộ trình phát triển

### 5.1. Các tính năng đã có trong thiết kế / hợp đồng (Khảo sát củng cố tính cấp thiết)
1. **Chia sẻ kết quả Thử thách hằng ngày (BR-25, FE-DAILY-01 / MEL-75):**
   - Đã được quy định trong quy tắc nghiệp vụ BR-25 ("Đúng ở 10s"). Thiết kế giao diện cần bổ sung nút sao chép kết quả trực quan dạng ô màu emoji (🟩 🟧 🟥).
2. **Vào phòng nhanh không cần tài khoản (BR-11, FE-ROOM-01 / MEL-58):**
   - Đã thiết kế cơ chế mã phòng 6 ký tự và link mời một chạm để triệt tiêu hoàn toàn rào cản cài app / đăng ký.
3. **Tìm kiếm bài hát tiếng Việt không dấu (BE-CAT-03 / MEL-36, đã merge):**
   - Đã được triển khai ở backend; đáp ứng đúng nhu cầu gõ tìm bài tiếng Việt nhanh chóng trên điện thoại.

### 5.2. Đề xuất tinh chỉnh mới chờ số liệu xác nhận
1. **Thứ tự bộ đề mặc định tại trang chủ (FE-SOLO-01 / MEL-47, đã merge):**
   - Chờ số liệu K5: nếu V-pop hiện đại và V-pop hoài niệm chiếm đa số, đề xuất tạo ticket tinh chỉnh nhỏ để hiển thị "V-pop 2010s–2020s" lên vị trí nổi bật đầu tiên, tiếp theo là "V-pop 1990s–2000s" và "US-UK".
2. **Lộ trình Phase 2 (Sau đồ án):**
   - **Chế độ Host & Màn hình chiếu (B2B):** Tiếp tục nghiên cứu trong BIZ-08 dành cho đối tượng người tổ chức sự kiện; không đưa vào MVP để bảo vệ tiến độ đồ án.
   - **Tự tạo bộ đề nhạc cá nhân:** Xem xét phát triển trong Phase 2 sau khi hoàn thiện cơ chế kiểm duyệt bản quyền.

---

## 6. Câu hỏi bảo vệ (Defense Q&A)

1. **"Quy mô mẫu khảo sát của đề tài được xác định như thế nào để đảm bảo tính đại diện?"**
   - *Trả lời:* Với một đồ án môn học xây dựng sản phẩm MVP theo phương pháp Lean Startup, mục tiêu tối thiểu là $N \ge 50$ phản hồi hợp lệ. Khảo sát nhằm kiểm chứng định hướng cho các giả thuyết bài toán then chốt (vào nhanh không cài app, nhu cầu nhạc Việt). Kết quả khảo sát còn được kiểm chứng chéo lần hai bằng dữ liệu hành vi thực tế trong đợt thử nghiệm Beta kín và Beta công khai (BIZ-06, BIZ-07).

2. **"Nếu người dùng chủ yếu thích chơi miễn phí (K11), mô hình kinh doanh của Meloo sẽ như thế nào?"**
   - *Trả lời:* Đây là đặc thù điển hình của nhóm người dùng sinh viên. Do đó, mô hình của Meloo là Freemium: miễn phí 100% tính năng thi đấu cốt lõi để thu hút quy mô người dùng (tận dụng hiệu ứng mạng lưới). Doanh thu sẽ đến từ 2 nguồn: (1) Gói cá nhân giá rẻ (~29.000 đ/tháng) cho nhóm người dùng có nhu cầu tùy biến giao diện và kho nhạc; (2) Gói B2B cho người tổ chức sự kiện, trường học, doanh nghiệp cần công cụ ice-breaker chuyên nghiệp (K10).

3. **"Nếu số liệu câu K7 về thói quen chơi hằng ngày không đạt ngưỡng 30%, nhóm sẽ xử lý thế nào?"**
   - *Trả lời:* Nếu số liệu thực tế cho thấy thói quen chơi đơn lẻ mỗi ngày không cao, nhóm sẽ nhìn nhận trung thực và đề xuất để Thái cân nhắc: Thử thách hằng ngày không thể chỉ là một tính năng chơi đơn độc lập, mà cần được gắn chặt với cơ chế so tài trong nhóm bạn (Group Challenge) và bảng xếp hạng theo chuỗi ngày (streak) để tạo áp lực xã hội tích cực kéo người chơi quay lại.

4. **"Tại sao không đưa tính năng tự tạo bộ đề vào ngay từ đầu khi người dùng có nhu cầu?"**
   - *Trả lời:* Tự tạo bộ đề đòi hỏi giải quyết các bài toán kỹ thuật phức tạp như kiểm duyệt bản quyền nhạc, xử lý preview audio tự động và kiểm duyệt từ khóa nhạy cảm. Để đảm bảo tính khả thi và chất lượng cao nhất cho đồ án môn học, nhóm tập trung toàn lực vào cơ chế chơi realtime ổn định, an toàn và chống gian lận trong MVP, đưa tính năng tự tạo bộ đề vào lộ trình Phase 2.

---

## 7. Phụ lục: Bộ câu hỏi khảo sát Google Form chi tiết

*(Trích xuất từ bản thiết kế khảo sát BIZ-03)*

1. **K1:** Trong 3 tháng qua, bạn chơi game online cùng bạn bè bao lâu một lần? *(Hằng ngày / Vài lần một tuần / 1–3 lần một tháng / Hiếm khi / Không bao giờ)*
2. **K2:** Khi chơi game cùng bạn bè, bạn thường dùng gì để nói chuyện / trao đổi? *(Discord voice / Messenger, Zalo / Voice trong game / Chỉ chat chữ / Khác)*
3. **K3:** Điều gì khiến bạn ngại hoặc từ chối thử một game mới cùng bạn bè? *(Phải tải và cài đặt ứng dụng / Bắt buộc tạo tài khoản, đăng ký / Mất thời gian rủ rê, hướng dẫn / Thiết bị không tương thích, máy yếu / Game bắt trả phí / Khác)*
4. **K4:** Bạn đã từng chơi game đoán bài hát / thử tài âm nhạc nào chưa? *(Đã từng / Chưa từng)*
5. **K5:** Nếu chơi game đoán bài hát, bạn muốn đoán loại nhạc nào nhất? *(Chọn tối đa 2 thể loại: Nhạc trẻ Việt Nam V-pop 2010s–2020s / Nhạc Âu Mỹ US-UK / Nhạc V-pop xưa hoài niệm 1990s–2000s / Nhạc Hàn Quốc K-pop / Nhạc phim, Anime, Game OST / Khác)*
6. **K6:** Những game đoán nhạc bạn từng chơi còn thiếu điều gì hoặc khiến bạn chưa hài lòng? *(Ít bài hát Việt Nam / Giao diện không có tiếng Việt, lỗi gõ dấu / Bắt buộc tải app, đăng ký / Chỉ chơi 1 mình / Trắc nghiệm dễ đoán mò / Thiếu voice chat / Khác)*
7. **K7:** Bạn có thói quen chơi các mini-game ngắn mỗi ngày (Wordle, Heardle, đố vui...) không? *(Chơi thường xuyên mỗi ngày / Thỉnh thoảng chơi vài ngày một tuần / Hiếm khi hoặc không bao giờ)*
8. **K8:** Bạn có từng chụp ảnh / chia sẻ kết quả game hoặc thành tích của mình lên MXH hoặc nhóm chat không? *(Thường xuyên / Thỉnh thoảng khi điểm cao / Chưa bao giờ)*
9. **K9:** Bạn có từng làm người tổ chức hoạt động, quản trò, host cho lớp học, CLB, nhóm bạn hoặc công ty không? *(Đã từng / Chưa từng)*
10. **K10:** Nếu có web game đoán nhạc mở nhanh bằng link làm phần khởi động 10 phút, bạn có muốn sử dụng không? *(Rất quan tâm và chắc chắn muốn dùng thử / Quan tâm, có thể dùng nếu thao tác nhanh gọn / Không quan tâm, chưa thấy cần thiết)*
11. **K11:** Nếu Meloo có tính năng nâng cao, bạn sẵn sàng trả phí cho tính năng nào và mức giá nào? *(Kho nhạc độc quyền / Tự tạo bộ đề / Host mode không giới hạn / Bỏ quảng cáo & avatar / Không muốn trả phí. Mức giá: 0đ / 19k–29k / 49k–69k / >99k)*
12. **K12:** Thông tin nhân khẩu: Bạn bao nhiêu tuổi và nghề nghiệp hiện tại là gì? *(Nhóm tuổi: <18, 18–22, 23–26, >26. Nghề nghiệp: Sinh viên / Học sinh, Nhân viên văn phòng, Khác)*
