# BIZ-06 · Báo cáo beta kín với bạn bè

> Ticket: MEL-63 · Phiên bản nháp ngày 05/11/2026
>
> **CẢNH BÁO — DỮ LIỆU MÔ PHỎNG:** Toàn bộ người tham gia, câu trả lời, số liệu,
> nhật ký trận và lỗi trong tài liệu này là kịch bản mô phỏng do Codex soạn để
> hoàn thiện biểu mẫu theo quyết định của Thái ngày 05/11/2026. Đây **không phải
> bằng chứng người dùng thật** và không được dùng để kết luận KPI hay giả thuyết
> sản phẩm. Trước khi nộp, Thái phải thay các mục có nhãn `MÔ PHỎNG` bằng dữ liệu
> thu trên production và xóa cảnh báo này sau khi kiểm tra nguồn.

## 1. Mục tiêu và điều kiện hoàn tất

Đợt beta kín dùng phòng riêng để kiểm tra ba câu hỏi trước khi mở beta công khai:

1. Người mới có vào phòng và bắt đầu chơi được chỉ từ một link mời không?
2. Luồng nghe, đoán, chat và bảng điểm có đủ ổn định trong một trận thật không?
3. Điều gì khiến người chơi muốn quay lại, và điểm ma sát nào cần sửa trước?

Beta chỉ được xem là hoàn tất khi có đủ bằng chứng thật sau:

- 10–15 người đã chơi phòng riêng trên môi trường production;
- 5 câu hỏi có phản hồi gốc, kèm ngày thu và số lượng câu trả lời hợp lệ;
- ghi chú quan sát trực tiếp ít nhất 1–2 trận;
- mỗi lỗi có một ticket Bug trên Jira và 5 góp ý chính đã được tổng hợp.

Trạng thái hiện tại: **chưa hoàn tất beta thật**. Nguồn dữ liệu thật:
`[CẦN SỐ LIỆU: link Google Form, ảnh/chụp màn hình dashboard và ngày beta]`.

### 1.1. Trạng thái nghiệm thu MEL-63

| Yêu cầu | Trạng thái | Điều kiện để đạt |
|---|---|---|
| 10–15 người chơi phòng riêng trên production | **Chưa đạt** | Thay danh sách mô phỏng bằng danh sách mã người tham gia và bằng chứng production |
| Form 5 câu; quan sát trực tiếp 1–2 trận | **Chưa đạt** | Liên kết form gốc, kết quả thật và ghi chú có mốc thời gian |
| Mỗi lỗi có Bug; tổng hợp 5 góp ý | **Chưa đạt** | Thái tạo Jira cho lỗi đã tái hiện; viết lại góp ý từ phản hồi thật |
| Báo cáo tại `docs/business/04-beta-feedback.md` | **Đạt** | File có cấu trúc để thay dữ liệu thật |

## 2. Kế hoạch và người tham gia

### 2.1. Điều kiện chạy thật

| Mục | Giá trị cần ghi sau buổi beta |
|---|---|
| Ngày, giờ | `[CẦN SỐ LIỆU]` |
| Môi trường | Production (`[CẦN SỐ LIỆU: URL/build hoặc commit]`) |
| Người điều phối | Thái |
| Kênh mời | `[CẦN SỐ LIỆU: Messenger/Zalo/Discord/khác]` |
| Số người được mời / tham gia / chơi hết trận | `[CẦN SỐ LIỆU]` / `[CẦN SỐ LIỆU]` / `[CẦN SỐ LIỆU]` |
| Thiết bị, trình duyệt | `[CẦN SỐ LIỆU]` |
| Bằng chứng | `[CẦN SỐ LIỆU: ảnh sảnh, bảng điểm và log không chứa dữ liệu nhạy cảm]` |

Chỉ lưu mã người tham gia (`P01`…`P15`), không đưa tên, email hoặc tài khoản mạng
xã hội vào repo. File phản hồi gốc phải giới hạn quyền truy cập nếu chứa dữ liệu
cá nhân.

### 2.2. Danh sách minh họa — `MÔ PHỎNG`

Kịch bản dùng 12 người để kiểm tra biểu mẫu; thay toàn bộ bảng này sau buổi beta.

| Mã | Thiết bị | Trình duyệt | Hình thức tham gia | Hoàn tất |
|---|---|---|---|---|
| P01 | Laptop Windows | Chrome | Trận quan sát A | Có |
| P02 | Laptop Windows | Edge | Trận quan sát A | Có |
| P03 | iPhone | Safari | Trận quan sát A | Có |
| P04 | Android | Chrome | Trận quan sát A | Có |
| P05 | MacBook | Chrome | Trận quan sát A | Có |
| P06 | Laptop Windows | Chrome | Trận quan sát B | Có |
| P07 | Android | Chrome | Trận quan sát B | Có |
| P08 | iPhone | Safari | Trận quan sát B | Có |
| P09 | MacBook | Safari | Trận quan sát B | Có |
| P10 | Laptop Windows | Firefox | Trận quan sát B | Có |
| P11 | Android | Chrome | Chơi ở phiên khác | Có |
| P12 | Laptop Windows | Chrome | Chơi ở phiên khác | Có |

## 3. Form phản hồi 5 câu

### 3.1. Nguyên văn câu hỏi

1. **F1 — Vào phòng:** Bạn vào phòng từ link mời và sẵn sàng chơi dễ đến mức
   nào? *(Thang 1–5: 1 = rất khó, 5 = rất dễ.)*
2. **F2 — Ổn định:** Trong lúc chơi, đoạn nhạc, ô đoán, đồng hồ và bảng điểm
   hoạt động ổn định đến mức nào? *(Thang 1–5: 1 = rất không ổn định, 5 = rất
   ổn định.)*
3. **F3 — Giá trị chính:** Phần nào khiến bạn thích nhất? *(Chọn một: nghe mở
   dần; thi điểm với bạn; vào nhanh bằng link; chat trong phòng; khác.)*
4. **F4 — Điểm ma sát:** Bạn gặp lỗi hoặc bước nào khó hiểu? Hãy mô tả thiết bị,
   thao tác và điều bạn mong đợi. *(Câu trả lời mở.)*
5. **F5 — Ý định quay lại:** Bạn có muốn chơi Meloo với nhóm bạn lần nữa không?
   *(Thang 1–5: 1 = chắc chắn không, 5 = chắc chắn có.)*

Form thật: `[CẦN SỐ LIỆU: URL và mốc đóng form]`.

### 3.2. Kết quả minh họa — `MÔ PHỎNG`

| Câu | Kết quả mô phỏng (n = 12) | Cách tính |
|---|---|---|
| F1 | 3 điểm: 2; 4 điểm: 6; 5 điểm: 4; trung bình **4,17/5** | (2×3 + 6×4 + 4×5) / 12 |
| F2 | 2 điểm: 1; 3 điểm: 2; 4 điểm: 5; 5 điểm: 4; trung bình **4,00/5** | (1×2 + 2×3 + 5×4 + 4×5) / 12 |
| F3 | Nghe mở dần: 5; thi điểm: 4; chat: 2; vào bằng link: 1 | Một lựa chọn/người |
| F4 | 4 phản hồi về âm thanh; 3 về thao tác mobile; 2 về trạng thái sẵn sàng; 3 không gặp khó khăn | Mã hóa theo chủ đề chính |
| F5 | 2 điểm: 1; 3 điểm: 2; 4 điểm: 4; 5 điểm: 5; trung bình **4,08/5** | (1×2 + 2×3 + 4×4 + 5×5) / 12 |

Không dùng các giá trị trên để điền KPI CSAT trong `01-problem.md`. Khi có dữ
liệu thật, xuất bảng tổng hợp trực tiếp từ form, giữ cả số lượng từng lựa chọn và
cách tính để kết quả có thể kiểm tra lại.

## 4. Nhật ký quan sát 2 trận — `MÔ PHỎNG`

### Trận A

- Thành phần: P01–P05; phòng riêng 5 người; 10 vòng.
- Kịch bản: P01 tạo phòng và gửi link, bốn người còn lại tự vào, đặt biệt danh,
  bấm sẵn sàng, chơi hết trận và về sảnh.
- Mốc minh họa: người cuối vào sảnh sau 1 phút 18 giây; trận dài 11 phút 48
  giây; 5/5 người xem được bảng điểm cuối.
- Quan sát: P03 phải chạm nút phát lần thứ hai ở vòng đầu; P04 mất thời gian đóng
  bàn phím để thấy danh sách gợi ý; cả nhóm hiểu cơ chế mở thêm mức nghe sau vòng
  thứ hai.

### Trận B

- Thành phần: P06–P10; phòng riêng 5 người; 10 vòng.
- Kịch bản: tham gia bằng link trên bốn loại trình duyệt, dùng chat giữa các vòng,
  một người tải lại trang rồi quay lại phòng.
- Mốc minh họa: người cuối vào sảnh sau 56 giây; trận dài 12 phút 06 giây; 5/5
  người hoàn tất.
- Quan sát: một người sau khi tải lại trang thấy trạng thái sẵn sàng không rõ;
  hai người hỏi bảng điểm có tự cập nhật hay cần tải lại; tin nhắn thường xuất
  hiện cho cả phòng trong kịch bản.

Khi chạy thật, người quan sát phải ghi mốc giờ, thao tác trước lỗi, kết quả thực
tế, kết quả mong đợi và bằng chứng tương ứng. Không ghi tên bài khi vòng chưa kết
thúc và không chụp token audio hoặc thông tin cá nhân.

## 5. Lỗi phát hiện và draft Bug — `MÔ PHỎNG`

Agent không tạo hoặc sửa Jira. Ba mục dưới đây chỉ là draft để Thái đối chiếu với
buổi beta thật; chỉ tạo ticket cho lỗi thực sự tái hiện được.

| Mã tạm | Hiện tượng mô phỏng | Mức ảnh hưởng dự kiến | Trạng thái |
|---|---|---|---|
| BETA-01 | Safari iOS không phát preview ở lần chạm đầu tiên của vòng đầu | Major — người chơi không nghe được đoạn nhạc | Chờ tái hiện thật và Thái tạo Bug |
| BETA-02 | Bàn phím mobile che một phần danh sách ô gợi ý | Major — cản thao tác đoán | Chờ tái hiện thật và Thái tạo Bug |
| BETA-03 | Sau khi tải lại trang, trạng thái sẵn sàng không đủ rõ | Minor — người chơi không biết cần bấm lại | Chờ tái hiện thật và Thái tạo Bug |

Mỗi ticket Bug phải có: môi trường/build, trình duyệt và thiết bị, bước tái hiện,
kết quả thực tế, kết quả mong đợi, bằng chứng không chứa secret, mức độ ảnh hưởng
và một test tái hiện được viết trước khi sửa. Liên kết Jira thật:
`[CẦN SỐ LIỆU: MEL-… cho từng lỗi đã xác nhận]`.

## 6. Năm góp ý chính — `MÔ PHỎNG`

1. **Link mời giảm ma sát vào phòng.** Người chơi muốn thấy xác nhận rõ sau khi
   bấm link và nhập biệt danh.
2. **Nghe mở dần là điểm khác biệt dễ hiểu.** Hướng dẫn ngắn ở vòng đầu sẽ giúp
   người mới hiểu việc nghe thêm làm giảm điểm.
3. **Ô gợi ý trên mobile cần ưu tiên.** Bàn phím và vùng hiển thị kết quả không
   nên che nút hoặc đáp án đang chọn.
4. **Âm thanh phải có trạng thái lỗi rõ.** Nếu autoplay bị chặn hoặc preview tải
   chậm, giao diện cần hướng dẫn thao tác lại thay vì để người chơi chờ.
5. **Bảng điểm và trạng thái phòng cần phản hồi tức thời.** Người chơi muốn biết
   dữ liệu đang cập nhật realtime, đặc biệt sau tải lại hoặc mất kết nối ngắn.

Sau beta thật, xếp 5 góp ý theo số người nhắc đến và mức ảnh hưởng quan sát được;
không giữ thứ tự mô phỏng nếu dữ liệu không ủng hộ.

## 7. Kết luận và việc cần làm trước beta công khai

Chưa thể kết luận sản phẩm đã sẵn sàng từ dữ liệu mô phỏng. Thái cần:

1. chạy beta trên production với 10–15 người và lưu bằng chứng nguồn;
2. thay toàn bộ bảng `MÔ PHỎNG` bằng dữ liệu thật, kiểm tra lại phép tính;
3. tạo Bug cho từng lỗi đã tái hiện và liên kết mã Jira tại mục 5;
4. ghi rõ lỗi nào phải sửa trước beta công khai và lỗi nào được chấp nhận;
5. nhờ một người khác đối chiếu báo cáo với form gốc trước khi xóa cảnh báo.

Quyết định mở beta công khai: **`[CẦN SỐ LIỆU VÀ QUYẾT ĐỊNH CỦA THÁI]`**.
