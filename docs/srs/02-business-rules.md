# Quy tắc nghiệp vụ Meloo (BR-01 → BR-29)

> Ticket: MEL-28 (DOC-01) · Trạng thái: **đã chốt ngày 22/10/2026**
> Nguồn: tài liệu "Meloo — Kế hoạch dự án & Quy trình làm việc" (bản 17/10, mục "Luật chơi & quy tắc nghiệp vụ"). Ngày 22/10, Product Owner (Thái) giao Claude chốt các điểm còn mở theo tiêu chí: game hay, làm kịp trong MVP, trải nghiệm người chơi đặt lên đầu. BR có dấu ✎ là đã sửa hoặc bổ sung so với bản 17/10; lý do ghi ở mục 2.
> Tài liệu liên quan: `01-srs.md` (yêu cầu), `03-glossary.md` (thuật ngữ).

## 1. Ba nguyên tắc dùng để chốt

1. **Không ai phải chờ vô ích.** Người chơi luôn có việc để làm: đoán, nghe thêm, bỏ qua, chơi tiếp, hoặc được gợi ý chế độ khác.
2. **Không thắng được bằng mẹo.** Mọi chỗ có thể lách (đoán bừa, cày điểm bằng tài khoản phụ, lấy cớ lỗi để bỏ bài khó) đều có quy tắc chặn, và server là trọng tài.
3. **Làm được trong MVP.** Không thêm hạ tầng mới (không bot, không chế độ khán giả, không Redis). Mỗi bổ sung chỉ là thêm trạng thái hoặc thêm một lệnh trong module sẵn có.

## 2. Các quyết định ngày 22/10

### 2.1 Các điểm đang dùng phương án mặc định

| Điểm | Quyết định | Vì sao |
|---|---|---|
| Đoán sai có mở mức kế tiếp như "Nghe thêm"? (BR-04) | **Có.** Thêm nút "Bỏ qua" ở mức 30 giây. | Đoán sai mà không mất gì thì người chơi đoán bừa liên tục. Heardle dùng đúng cơ chế này và đã được kiểm chứng là vui. "Bỏ qua" để người không biết bài không phải ngồi chờ hết giờ. |
| 5 mức 2 – 5 – 10 – 20 – 30 giây, điểm 1.000 → 200 (BR-01, BR-02) | **Giữ nguyên.** | 2 giây đủ khó cho người giỏi mà người chơi phổ thông vẫn có cơ hội với bài quen. Điểm giảm đều 200 mỗi mức nên dễ nhớ, dễ giải thích. |
| Phòng riêng không tính xếp hạng (BR-23) | **Giữ, mở rộng cho mọi phòng tự tạo**, nhưng vẫn lưu lịch sử, thống kê và tính huy hiệu. | Phòng tự tạo cho phép chọn bộ nhạc và người chơi, nên cày điểm bằng tài khoản phụ rất dễ. Nhóm bạn là người dùng chính, nên họ vẫn phải thấy tiến bộ của mình khi chơi phòng riêng. |
| Voice chỉ ở phòng riêng, mic tự tắt khi phát nhạc (BR-18, BR-19) | **Giữ.** Mic tự bật lại khi nhạc dừng và có thông báo trên giao diện. | Không kiểm duyệt được giọng nói với người lạ. Mic tắt khi phát nhạc để không lọt đoạn dài hơn sang người đang ở mức thấp hơn. Tự bật lại vì người chơi nghe lại nhiều lần (BR-03), bắt họ bật tay mỗi lần sẽ rất phiền. |
| Job xóa khách sau 30 ngày (BR-20) | **Giữ ở BE-ID-03.** Nếu phải cắt BE-ID-03 thì chuyển riêng job này sang một ticket Must, không sửa chính sách. | Đây là cam kết quyền riêng tư trong LEG-01. Job chỉ là một tác vụ chạy định kỳ, nhỏ hơn nhiều so với việc sửa chính sách đã công bố. |

### 2.2 Những chỗ bản 17/10 chưa rõ

| Chỗ chưa rõ | Quyết định | BR |
|---|---|---|
| Khách có chơi ghép trận nhanh không, khi khách không lên bảng xếp hạng? | Khách **chơi được**. Điểm của khách không vào bảng xếp hạng, người đã đăng nhập trong cùng trận vẫn được tính. Khi khách đăng nhập, điểm các trận xếp hạng được gộp và hiện lên bảng. | BR-20, BR-21 |
| Ghép trận nhanh "đủ 5 người" trong khi số người tối đa là cấu hình | Bắt đầu khi **đủ số người tối đa của phòng** (mặc định 5). Đếm ngược 30 giây bắt đầu khi có người thứ 2 và không đặt lại khi thêm người. | BR-13 |
| Nhiều người: mức nghe tính riêng hay chung? | **Mỗi người tự mở mức của mình**, chỉ đồng hồ 45 giây là chung. Vòng kết thúc sớm khi mọi người đã xong. | BR-07 |
| Mất kết nối giữa vòng thì trận có chờ không? | **Không chờ.** Vào lại kịp trong 30 giây thì chơi tiếp ở vòng hiện tại; vòng bị lỡ được 0 điểm. | BR-14 |
| Thử thách ngày "xếp theo số lần thử" trong khi mỗi người chỉ có một lượt | Xếp theo **mức đoán đúng**, rồi theo thời gian. | BR-25 |

### 2.3 Những chỗ bản 17/10 chưa có

| Bổ sung | Vì sao | BR |
|---|---|---|
| Công thức điểm một vòng, gồm cách tính thưởng tốc độ | Engine (BE-GAME-01) và test cần một công thức duy nhất. Khi bảo vệ cũng có ví dụ cụ thể để trình bày. | BR-07, BR-08, mục 3 |
| Combo áp dụng cả chơi đơn | Chuỗi đúng liên tiếp là thứ giữ người chơi đơn ở lại. Một công thức cho mọi chế độ thì dễ hiểu hơn và test ít hơn. | BR-08 |
| Đáp án đúng khi trùng tên bài sau chuẩn hóa | Người chơi nhận ra bài là đủ, không bắt chọn đúng bản thu. Luật này cũng xử lý được bản remix, live và cover. | BR-05 |
| Bảng tuần và toàn thời gian xếp theo tổng điểm; "bảng theo ngày" là bảng thử thách ngày | Bản 17/10 chưa nói xếp hạng theo chỉ số nào. Tổng điểm thưởng người chơi đều đặn, khớp với KPI quay lại ≥ 30%. | BR-24 |
| Mã phòng bỏ ký tự dễ nhầm | Người chơi hay đọc mã qua voice hoặc tin nhắn, nhầm 0/O hay 1/I/L thì vào sai phòng. | BR-11 |
| Chủ phòng mời người khác ra khỏi phòng; hết trận cả phòng về sảnh để chơi tiếp | Một người treo máy không bấm sẵn sàng thì cả phòng bị kẹt. "Chơi lại" ngay trong phòng giữ nhóm bạn ở lại lâu hơn. | BR-12 |
| Người vào phòng lúc trận đang chạy thì chờ ở sảnh | Bạn đến muộn bấm link mà bị báo lỗi là trải nghiệm tệ nhất của game nhóm. | BR-15 |
| Đợi ghép trận 60 giây vẫn một mình thì gợi ý chơi đơn hoặc mời bạn | Kế hoạch đã ghi rủi ro "ít người online". Không để người chơi chờ mù. | BR-13 |
| Đoạn nhạc lỗi giữa trận thì thay bằng bài khác | Lỗi kỹ thuật không được làm hỏng trận. Chỉ server xác nhận lỗi để không ai lấy cớ bỏ qua bài khó. | BR-27 |
| Kết quả thử thách ngày chia sẻ không lộ tên bài | Chia sẻ là kênh kéo người mới; lộ tên bài thì hỏng thử thách của người chưa chơi. | BR-25 |

## 3. Cách tính điểm

### 3.1 Điểm cơ bản (BR-02)

| Đoán đúng ở mức | Điểm cơ bản |
|---|---|
| 2 giây | 1.000 |
| 5 giây | 800 |
| 10 giây | 600 |
| 20 giây | 400 |
| 30 giây | 200 |
| Không đoán được, hoặc bỏ qua | 0 |

### 3.2 Điểm một vòng

```
Điểm vòng = Điểm cơ bản × (1 + hệ số combo) + Thưởng tốc độ

Hệ số combo   = 0 %  (chưa có chuỗi hoặc vòng đầu của chuỗi)
              = 10 % (vòng thứ 2 liên tiếp đúng ở mức ≤ 5 giây)
              = 20 % (từ vòng thứ 3 liên tiếp trở đi)
Thưởng tốc độ = ⌊200 × số giây còn lại ÷ 45⌋   (chỉ khi chơi nhiều người; chơi đơn bằng 0)
```

Thử thách ngày chỉ có một bài nên không có combo; thứ hạng theo BR-25.

**Ví dụ (phòng nhiều người).** Người chơi đã đúng ở mức 2 giây ở vòng trước. Vòng này đoán đúng ở mức 5 giây khi đồng hồ còn 30 giây:
- Điểm cơ bản: 800
- Combo vòng thứ 2: 800 × 1,10 = 880
- Thưởng tốc độ: ⌊200 × 30 ÷ 45⌋ = ⌊133,3⌋ = 133
- **Điểm vòng: 880 + 133 = 1.013**

Lưu ý từ kế hoạch: bản preview 30 giây do nền tảng nhạc tự cắt và thường không phải đoạn mở đầu bài. "2 giây đầu" là 2 giây đầu của đoạn preview. Spike SPK-01 sẽ kiểm tra preview thường bắt đầu ở đâu trong bài.

## 4. Bảng quy tắc

| Mã | Nhóm | Quy tắc | Vì sao |
|---|---|---|---|
| BR-01 | Vòng chơi | 5 mức nghe: 2, 5, 10, 20, 30 giây, tính từ đầu đoạn preview. | Mức tăng dần tạo kịch tính; 30 giây là giới hạn của preview. |
| BR-02 | Vòng chơi | Điểm cơ bản theo mức đoán đúng: 1.000 / 800 / 600 / 400 / 200. | Thưởng người nhận ra bài sớm. |
| BR-03 | Vòng chơi | Nghe lại đoạn đang mở không giới hạn, không mất điểm. | Bù cho mạng chậm hoặc lỡ nghe. |
| BR-04 ✎ | Vòng chơi | Bấm "Nghe thêm" hoặc đoán sai đều mở mức kế tiếp. Ở mức 30 giây, nút "Nghe thêm" đổi thành "Bỏ qua"; đoán sai hoặc bỏ qua ở mức này thì vòng đó 0 điểm. | Chặn đoán bừa liên tục; "Bỏ qua" giúp người không biết bài khỏi phải ngồi chờ. |
| BR-05 ✎ | Vòng chơi | Đáp án chọn từ ô gợi ý tìm trên toàn bộ kho nhạc, không cần gõ dấu. Đoán đúng khi tên bài đã chọn trùng tên bài đáp án sau chuẩn hóa: bỏ dấu, viết thường, bỏ phần trong ngoặc và phần ghi phiên bản (Remix, Live, Acoustic, feat. …). Vì vậy mọi phiên bản và bản cover cùng tên đều tính là đúng. | Game đoán tên bài, nhận ra bài là đủ, không bắt chọn đúng bản thu. Ô gợi ý tìm trên cả kho nên không lộ đáp án. Hai bài khác nhau mà trùng tên rất hiếm, chấp nhận để luật đơn giản. |
| BR-06 | Vòng chơi | Chọn đúng ca sĩ chính nhưng sai bài: báo "Đúng ca sĩ!" làm gợi ý, vẫn tính là đoán sai. | Thêm hứng thú mà không cho điểm dễ. |
| BR-07 ✎ | Vòng chơi | Nhiều người: mỗi vòng tối đa 45 giây theo đồng hồ server; mỗi người tự mở mức nghe của mình. Vòng kết thúc khi hết giờ, hoặc sớm hơn khi mọi người đã đoán đúng hoặc hết lượt. Khi có người đoán đúng, cả phòng thấy "<tên> đã đoán đúng" nhưng không thấy đáp án. Đoán đúng được thưởng tốc độ = ⌊200 × số giây còn lại ÷ 45⌋. | Không ai phải chờ người khác nghe xong; thông báo tạo áp lực vui; thưởng tốc độ phân thắng thua giữa người cùng mức. |
| BR-08 ✎ | Vòng chơi | Combo (chơi đơn và nhiều người, trừ thử thách ngày): mỗi vòng đoán đúng ở mức 2 hoặc 5 giây thì nối chuỗi, vòng có kết quả khác thì đứt chuỗi. Vòng thứ 2 liên tiếp được +10%, từ vòng thứ 3 được +20%, tính trên điểm cơ bản. | Thưởng người giỏi thật, giữ người chơi ở lại. Một công thức điểm cho mọi chế độ nên dễ hiểu, dễ test. |
| BR-09 ✎ | Vòng chơi | Trận 5/10/15 vòng (mặc định 10), không lặp bài. Bộ nhạc không đủ bài hợp lệ cho số vòng đã chọn thì không cho bắt đầu và báo rõ còn thiếu bao nhiêu bài. | Trận ngắn 10–15 phút; báo rõ để chủ phòng biết đổi bộ nhạc hay giảm số vòng. |
| BR-10 | Vòng chơi | Thắng = tổng điểm cao nhất; hòa thì ai có tổng thời gian trả lời đúng ít hơn thắng. | Luôn có một người thắng. |
| BR-11 ✎ | Phòng | Phòng 2–5 người (số tối đa là cấu hình, mặc định 5). Mã phòng 6 ký tự gồm chữ in hoa và số, bỏ các ký tự dễ nhầm 0, O, 1, I, L. Phòng riêng chỉ vào bằng mã hoặc link; phòng công khai hiện trong danh sách. | Mở rộng số người sau mà không sửa code; đọc mã qua voice hay tin nhắn không bị nhầm. |
| BR-12 ✎ | Phòng | Chủ phòng chọn bộ nhạc và số vòng, bấm bắt đầu khi ≥ 2 người và tất cả đã sẵn sàng. Chủ phòng mời được người khác ra khỏi phòng; người bị mời ra không vào lại phòng đó được. Chủ rời phòng thì quyền chuyển cho người vào sớm nhất. Hết trận, cả phòng về sảnh để chơi tiếp, giữ nguyên mã phòng. | Phòng không bị kẹt khi chủ thoát hoặc có người treo máy; chơi lại liền mạch giữ nhóm bạn ở lại. |
| BR-13 ✎ | Phòng | Ghép trận nhanh: vào phòng công khai đang chờ và hợp bộ lọc, không có thì tạo phòng mới. Khi có người thứ 2, đếm ngược 30 giây (không đặt lại khi thêm người); trận tự bắt đầu khi hết đếm ngược hoặc khi đủ số người tối đa của phòng. Chờ 60 giây vẫn chỉ có một mình thì gợi ý chơi đơn hoặc gửi link mời bạn; người chơi vẫn chờ tiếp được. | Ít người online vẫn chơi được, và không ai phải chờ mà không biết bao giờ mới vào trận. |
| BR-14 ✎ | Phòng | Mất kết nối được giữ chỗ 30 giây; trận không dừng lại để chờ. Vào lại kịp thì chơi tiếp ở vòng hiện tại với mức nghe đã mở; vòng bị lỡ được 0 điểm. Quá 30 giây thì coi như rời, điểm đã có vẫn giữ. Nếu phòng chỉ còn 1 người, trận kết thúc sớm và tính theo điểm hiện tại. | Mạng di động hay chập chờn; những người còn lại không phải chờ. |
| BR-15 ✎ | Phòng | Không vào giữa trận đang diễn ra: ai vào bằng mã hoặc link lúc đó thì chờ ở sảnh (nếu phòng còn chỗ) và chơi từ trận sau. Phòng trống quá 10 phút tự đóng. | Công bằng cho người đang chơi; bạn đến muộn không bị đuổi về; giải phóng tài nguyên. |
| BR-16 ✎ | Giao tiếp | Chat tối đa 200 ký tự, 1 tin mỗi giây. Trong vòng, tin chứa tên bài đáp án (so khớp không dấu như BR-05) bị chặn, chỉ người gửi thấy cảnh báo; hết vòng thì nói tự do. | Chống spam và chống nhắc bài, nhưng vẫn cho bàn tán sau mỗi vòng. |
| BR-17 | Giao tiếp | Lọc từ cấm theo danh sách admin quản lý; người chơi báo cáo được tin vi phạm. | An toàn khi chơi với người lạ. |
| BR-18 | Giao tiếp | Voice chat chỉ có trong phòng riêng; mic mặc định tắt. | Không kiểm duyệt được giọng nói với người lạ. |
| BR-19 ✎ | Giao tiếp | Mic tự tắt khi đoạn nhạc của chính người đó đang phát, và tự bật lại khi nhạc dừng nếu trước đó đang bật; giao diện báo "Mic tạm tắt khi phát nhạc". | Không để lọt đoạn nhạc dài hơn cho người khác; người chơi không phải bật mic lại mỗi lần nghe. |
| BR-20 ✎ | Tài khoản | Khách chơi mọi chế độ, kể cả ghép trận nhanh, bằng biệt danh. Điểm của khách không vào bảng xếp hạng; người đã đăng nhập trong cùng trận vẫn được tính bình thường. Dữ liệu khách không hoạt động 30 ngày bị xóa. | Vào chơi không có rào cản; dữ liệu gọn, đúng quyền riêng tư. |
| BR-21 ✎ | Tài khoản | Đăng nhập Google/Facebook. Khi khách đăng nhập, lịch sử, thống kê, huy hiệu và điểm các trận xếp hạng của khách được gộp vào tài khoản và tính vào bảng xếp hạng của kỳ tương ứng. Nếu cả khách và tài khoản đều đã chơi thử thách ngày trong cùng ngày thì giữ lượt của tài khoản. | Không mất thành tích khi chuyển đổi; "đăng nhập để đưa điểm lên bảng xếp hạng" là lý do tự nhiên để tạo tài khoản. |
| BR-22 | Tài khoản | Biệt danh 3–20 ký tự, không chứa từ cấm. | Hiển thị gọn, văn minh. |
| BR-23 ✎ | Xếp hạng | Chỉ trận xếp hạng (chơi đơn bộ chuẩn, thử thách ngày, ghép trận nhanh) được tính vào bảng xếp hạng. Phòng tự tạo, dù riêng hay công khai, không tính xếp hạng nhưng vẫn lưu lịch sử, thống kê và tính huy hiệu. | Chống cày điểm bằng tài khoản phụ ở nơi tự chọn được bộ nhạc và người chơi, mà nhóm bạn vẫn thấy mình tiến bộ. |
| BR-24 ✎ | Xếp hạng | Bảng tuần và bảng toàn thời gian xếp theo tổng điểm các trận xếp hạng; bằng điểm thì ai đạt mức điểm đó trước xếp trên. Bảng tuần reset 00:00 thứ Hai giờ Việt Nam. Bảng theo ngày là bảng thử thách ngày (BR-25). | Tổng điểm thưởng người chơi đều đặn; reset hằng tuần cho người mới cơ hội lên top. |
| BR-25 ✎ | Xếp hạng | Thử thách ngày: một bài chung, mỗi người một lượt, đổi bài lúc 00:00 giờ Việt Nam. Xếp theo mức đoán đúng (2 giây cao nhất), rồi theo thời gian từ lúc bắt đầu tới khi đoán đúng; không đoán ra thì xếp cuối. Kết quả chia sẻ dạng ô màu theo từng mức, không lộ tên bài. | Tạo thói quen quay lại mỗi ngày; chia sẻ kéo người mới mà không làm hỏng bài cho người chưa chơi. |
| BR-26 | Xếp hạng | Huy hiệu mở khóa tự động khi đạt điều kiện, không bị thu hồi. | Khuyến khích chơi lâu dài. |
| BR-27 ✎ | Quản trị | Bài lỗi preview hoặc không phù hợp bị vô hiệu hóa, không xuất hiện ở trận mới. Nếu server xác nhận đoạn nhạc lỗi giữa trận, vòng đó được thay bằng bài khác, không ai bị trừ điểm. | Trận không hỏng vì lỗi kỹ thuật; chỉ server xác nhận để không ai lấy cớ "lỗi" bỏ qua bài khó. |
| BR-28 | Quản trị | Tài khoản bị khóa không đăng nhập, không tạo hay vào phòng được; mọi thao tác admin ghi nhật ký. | Truy vết, chống lạm quyền. |
| BR-29 | Quản trị | Sau mỗi vòng hiện nguồn nhạc và link nghe đầy đủ trên nền tảng gốc. | Tôn trọng bản quyền và điều khoản API. |

## 5. Còn mở (không thuộc luật chơi)

- **Nhà cung cấp nhạc** (Deezer hay iTunes): chốt sau spike SPK-01, vì cần đo độ phủ nhạc Việt và độ ổn định của preview. Ảnh hưởng tới dữ liệu phiên bản bài (BR-05) và link nghe đầy đủ (BR-29), không đổi nội dung các quy tắc này.

## 6. Ticket cần cập nhật Acceptance Criteria theo bản này

Các BR có dấu ✎ làm thay đổi AC của ticket ở những module sau. Cần rà lại trước khi giao ticket cho agent:

- **game:** công thức điểm (mục 3), combo cả chơi đơn, nút "Bỏ qua", so khớp tên bài sau chuẩn hóa (BE-GAME-01).
- **room:** mời ra khỏi phòng, về sảnh sau trận, chờ ở sảnh khi trận đang chạy, đếm ngược ghép trận, gợi ý sau 60 giây, xử lý mất kết nối, kết thúc sớm khi còn 1 người.
- **progression, daily:** chỉ số xếp hạng (tổng điểm), gộp điểm khi khách đăng nhập, phòng tự tạo vẫn tính huy hiệu, xếp hạng thử thách ngày, kết quả chia sẻ.
- **chat, voice:** chặn đáp án so khớp không dấu, mic tự bật lại.
- **catalog, audio:** thay bài khi server xác nhận preview lỗi.
