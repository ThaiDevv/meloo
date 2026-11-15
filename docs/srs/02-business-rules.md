# Quy tắc nghiệp vụ Meloo (BR-01 → BR-29)

> Ticket: MEL-28 (DOC-01), cập nhật MEL-149 · Trạng thái: **đã chốt ngày 22/10/2026, cập nhật luật xếp hạng ngày 01/11/2026, bỏ voice chat ngày 14/11/2026**
> Nguồn: tài liệu "Meloo — Kế hoạch dự án & Quy trình làm việc" (bản 17/10, mục "Luật chơi & quy tắc nghiệp vụ"). Ngày 22/10, Product Owner (Thái) giao Claude chốt các điểm còn mở theo tiêu chí: game hay, làm kịp trong MVP, trải nghiệm người chơi đặt lên đầu. BR có dấu ✎ là đã sửa hoặc bổ sung so với bản 17/10; lý do ghi ở mục 2. Cập nhật thêm cùng ngày theo review PR: BR-07, BR-10, BR-12, BR-13, BR-21; khi nghiệm thu: BR-25. Cập nhật theo 8 quyết định của PO khi đặc tả use case (MEL-29): BR-07, BR-13, BR-17, BR-20, BR-23, BR-25, BR-28. Cập nhật luật xếp hạng ngày 01/11/2026 theo quyết định của PO (MEL-149): BR-07, BR-13, BR-14, BR-20, BR-21, BR-23, BR-24. Ngày 14/11/2026 PO bỏ voice chat: BR-18, BR-19 hết hiệu lực, giữ mã để không đánh số lại (mục 2.5). Biên bản duyệt ở mục 8.
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
| Voice chỉ ở phòng riêng, mic tự tắt khi phát nhạc (BR-18, BR-19) | ~~**Giữ.** Mic tự bật lại khi nhạc dừng và có thông báo trên giao diện.~~ **Bỏ ngày 14/11/2026** (mục 2.5). | Không kiểm duyệt được giọng nói với người lạ. Mic tắt khi phát nhạc để không lọt đoạn dài hơn sang người đang ở mức thấp hơn. Tự bật lại vì người chơi nghe lại nhiều lần (BR-03), bắt họ bật tay mỗi lần sẽ rất phiền. |
| Job xóa khách sau 30 ngày (BR-20) | **Giữ ở BE-ID-03.** Nếu phải cắt BE-ID-03 thì chuyển riêng job này sang một ticket Must, không sửa chính sách. | Đây là cam kết quyền riêng tư trong LEG-01. Job chỉ là một tác vụ chạy định kỳ, nhỏ hơn nhiều so với việc sửa chính sách đã công bố. |

### 2.2 Những chỗ bản 17/10 chưa rõ

| Chỗ chưa rõ | Quyết định | BR |
|---|---|---|
| Khách có chơi ghép trận nhanh không, khi khách không lên bảng xếp hạng? | **Không.** Khách không vào được ghép trận nhanh; muốn ghép trận nhanh tính điểm rank phải đăng nhập. Khách vẫn chơi được chơi đơn và phòng tự tạo. Bỏ cơ chế gộp điểm trận xếp hạng của khách vì khách không chơi rank nên không còn gì để gộp (cập nhật 01/11). | BR-13, BR-20, BR-21 |
| Ghép trận nhanh "đủ 5 người" trong khi số người tối đa là cấu hình | Bắt đầu khi **đủ số người tối đa của phòng** (mặc định 5). Đếm ngược 30 giây bắt đầu khi có người thứ 2 và không đặt lại khi thêm người. | BR-13 |
| Nhiều người: mức nghe tính riêng hay chung? | **Mỗi người tự mở mức của mình**, chỉ đồng hồ 45 giây là chung. Vòng kết thúc sớm khi mọi người đã xong. | BR-07 |
| Mất kết nối giữa vòng thì trận có chờ không? | **Không chờ.** Vào lại kịp trong 30 giây thì chơi tiếp ở vòng hiện tại; vòng bị lỡ được 0 điểm. Rời trận hoặc mất kết nối quá 30 giây xếp chót khi tính điểm rank (cập nhật 01/11). | BR-14 |
| Thử thách ngày "xếp theo số lần thử" trong khi mỗi người chỉ có một lượt | Xếp theo **mức đoán đúng**, rồi theo thời gian. | BR-25 |
| Ghép trận nhanh có vào phòng công khai tự tạo không? (review PR) | **Không.** Chỉ vào phòng ghép trận do hệ thống tạo; phòng này không hiện trong danh sách phòng công khai và không có chủ phòng. Lý do: trận ghép nhanh tính xếp hạng (BR-23), nên bộ nhạc và người chơi phải do hệ thống chọn; phòng tự tạo do chủ phòng chỉnh và chủ phòng mời người ra được. | BR-13 |
| "Số giây còn lại" trong thưởng tốc độ là số nguyên hay mili giây? (review PR) | **Mili giây, theo đồng hồ server:** ⌊200 × số mili giây còn lại ÷ 45.000⌋. Lý do: tính theo giây nguyên làm điểm nhảy từng bậc khoảng 4 điểm và dễ hòa; mili giây công bằng hơn, khớp cách phân định hòa ở BR-10. | BR-07, BR-10 |
| Chơi đơn có giới hạn thời gian mỗi vòng không? (review PR) | **Chơi đơn: không; thử thách ngày: có trần 5 phút mỗi lượt (BR-25).** Chơi đơn không giới hạn giờ mỗi vòng, nên không có thưởng tốc độ. Lý do: chơi đơn là để luyện thư thả; thử thách ngày đã tính thời gian khi xếp hạng (BR-25). *Đã sửa ở dòng "(use case MEL-29)" bên dưới: thử thách ngày có trần 5 phút mỗi lượt.* | BR-07 |
| Gợi ý "mời bạn" sau 60 giây có cho bạn vào phòng ghép trận không? (review PR lần 2) | **Không.** Phòng ghép trận không vào được bằng mã hay link. Gợi ý đổi thành "Tạo phòng riêng và mời bạn": rời hàng chờ, tạo phòng riêng cùng bộ lọc rồi copy link. Lý do: cho bạn bè vào thẳng phòng xếp hạng là đúng kiểu cày điểm BR-23 muốn chặn; phòng riêng vẫn chơi được ngay, chỉ không tính xếp hạng. | BR-13, BR-23 |
| Lượt đoán đến sau khi hết giờ thì sao? (review PR lần 2) | **Bị từ chối:** lượt đoán server nhận từ mốc 45.000 ms trở đi không được chấm, vòng đã hết giờ với người đó. Công thức thưởng tốc độ vẫn ghi max(0, …) để không bao giờ ra số âm. Lý do: server là trọng tài; một lượt đoán trễ do mạng không được biến thành điểm âm hay điểm dương. | BR-07 |
| Lượt đoán và lúc hết giờ đến sát nhau thì tính thế nào? (review PR lần 3) | **Phân xử theo lúc server nhận lượt đoán:** trước mốc 45.000 ms thì được chấm, từ mốc 45.000 ms trở đi thì bị từ chối, bất kể lệnh hết giờ đã được xử lý hay chưa. Lý do: bộ hẹn giờ của server có thể chạy trễ vài mili giây; nếu dựa vào thứ tự xử lý thì cùng một lượt đoán lúc được chấm lúc không. So với một mốc cố định thì kết quả luôn như nhau và test được. | BR-07 |
| Phòng ghép trận có chủ phòng, nút sẵn sàng, "chơi lại giữ mã" không? (review PR lần 3) | **Không.** BR-12 chỉ áp dụng cho phòng tự tạo. Phòng ghép trận bắt đầu và chơi tiếp theo đếm ngược ở BR-13, không ai phải bấm sẵn sàng. Lý do: một người lạ treo máy không chặn được cả phòng; phòng không có mã nên không có gì để giữ. | BR-12, BR-13 |
| Viết "5s" hay "5 giây"? (review PR lần 3) | **Tùy chỗ dùng:** nhãn ngắn trên giao diện ghi 2s, 5s, 10s, 20s, 30s, giống nhau ở tiếng Việt và tiếng Anh; câu văn trong tài liệu và giao diện ghi "5 giây" / "5 seconds". Quy ước nằm ở `03-glossary.md` (Nhãn mức nghe). | BR-01 |
| Kết quả chia sẻ có ghi mức đoán đúng không? (nghiệm thu) | **Có.** Chuỗi ô màu kèm nhãn "Đúng ở 10s" (không đoán ra thì "Chưa đoán ra") và link. Lý do: người thấy bài chia sẻ trên mạng xã hội chưa chơi nên không biết ô thứ ba là mức 10 giây; nhãn chữ cho họ hiểu ngay và muốn thử vượt. Người khó phân biệt đỏ và xanh cũng đọc được kết quả, vì màu không phải cách duy nhất truyền thông tin (WCAG 2.1 AA, NFR-13). Nhãn không lộ tên bài. | BR-25 |
| Chơi đơn có tính xếp hạng không? (use case MEL-29, cập nhật 01/11) | **Không.** Chơi đơn bỏ hoàn toàn chế độ "Xếp hạng", chỉ còn chế độ "Luyện tập" (tự chọn bộ lọc / bộ đề); vẫn có điểm, combo, lịch sử, thống kê và huy hiệu nhưng không tính điểm rank và không ảnh hưởng xếp hạng. Xếp hạng chỉ đến từ ghép trận nhanh (trận xếp hạng duy nhất). | BR-23 |
| Khách có xem bảng xếp hạng và hồ sơ không? (use case MEL-29) | **Có, chỉ xem.** Khách xem bảng nhưng không có tên trên bảng; hồ sơ chỉ xem gồm lịch sử, thống kê, huy hiệu của phiên khách, kèm lời nhắc đăng nhập. Lý do: BR-21 đã gộp các dữ liệu này khi đăng nhập, nên khách đang tích lũy chúng; cho khách thấy là lý do tự nhiên để tạo tài khoản. | BR-20 |
| Chơi đơn bỏ dở giữa chừng thì sao? (use case MEL-29) | **Giữ trận 30 phút**, mở lại thì chơi tiếp đúng vòng và mức nghe đã mở, không đổi bài. Quá 30 phút thì trận bỏ dở: lưu các vòng đã xong, vòng đang dở 0 điểm, không tính xếp hạng. Lý do: người lỡ đóng tab không mất trận, nhưng không ai tải lại trang để đổi bài khó. | BR-23 |
| Thử thách ngày qua 00:00 lúc đang chơi thì tính ngày nào? (use case MEL-29) | **Ngày mà server cấp bài.** Mỗi lượt có trần 5 phút; hết trần chưa đúng là "Chưa đoán ra". Lý do: bảng ngày xếp theo thời gian, không có trần thì người chơi mở lượt rồi treo máy đi hỏi người khác. | BR-07, BR-25 |
| Ghép trận: người thứ 2 rời lúc đang đếm ngược? (use case MEL-29) | **Đếm ngược dừng**, có người thứ 2 thì đếm lại đủ 30 giây; hết đếm ngược server kiểm tra lại, dưới 2 người thì không bắt đầu. Lý do: không có trận một người; kiểm tra lại lúc bắt đầu xử lý luôn trường hợp rớt mạng. | BR-13 |
| Lọc từ cấm là chặn hay che? (use case MEL-29) | **Chặn cả tin**, chỉ người gửi thấy cảnh báo. Lý do: dùng chung cơ chế "tin bị chặn" với tin lộ đáp án (BR-16), ít code và ít test hơn; che bằng *** vẫn để lộ ý. | BR-17 |
| Xử lý báo cáo có cảnh cáo trước khi khóa? Tin của khách thì khóa gì? (use case MEL-29) | **Không có cảnh cáo trong MVP**; khóa phải nhập lý do, mở khóa lại được. Tin của khách: khóa theo id khách. Lý do: cảnh cáo cần thêm trạng thái và thông báo, trái nguyên tắc 3, để Phase 2; khách không có tài khoản nên chỉ khóa được id, chấp nhận giới hạn khách xóa dữ liệu trình duyệt thì có id mới. | BR-28 |

### 2.3 Những chỗ bản 17/10 chưa có

| Bổ sung | Vì sao | BR |
|---|---|---|
| Công thức điểm một vòng, gồm cách tính thưởng tốc độ | Engine (BE-GAME-01) và test cần một công thức duy nhất. Khi bảo vệ cũng có ví dụ cụ thể để trình bày. | BR-07, BR-08, mục 3 |
| Combo áp dụng cả chơi đơn | Chuỗi đúng liên tiếp là thứ giữ người chơi đơn ở lại. Một công thức cho mọi chế độ thì dễ hiểu hơn và test ít hơn. | BR-08 |
| Đáp án đúng khi trùng tên bài sau chuẩn hóa | Người chơi nhận ra bài là đủ, không bắt chọn đúng bản thu. Luật này cũng xử lý được bản remix, live và cover. | BR-05 |
| Bảng xếp hạng chính theo điểm rank toàn thời gian (cập nhật 01/11: bỏ bảng tuần); "bảng theo ngày" là bảng thử thách ngày | Bản 17/10 chưa nói xếp hạng theo chỉ số nào. Ngày 01/11, PO quyết định bảng xếp hạng chính xếp theo điểm rank (tích lũy từ ghép trận nhanh), bỏ bảng tuần để tập trung vào một thước đo trình độ duy nhất. Bảng theo ngày vẫn là thử thách ngày (BR-25). | BR-24 |
| Mã phòng bỏ ký tự dễ nhầm | Người chơi hay đọc mã cho nhau hoặc gửi qua tin nhắn, nhầm 0/O hay 1/I/L thì vào sai phòng. | BR-11 |
| Chủ phòng mời người khác ra khỏi phòng; hết trận cả phòng về sảnh để chơi tiếp | Một người treo máy không bấm sẵn sàng thì cả phòng bị kẹt. "Chơi lại" ngay trong phòng giữ nhóm bạn ở lại lâu hơn. | BR-12 |
| Người vào phòng lúc trận đang chạy thì chờ ở sảnh | Bạn đến muộn bấm link mà bị báo lỗi là trải nghiệm tệ nhất của game nhóm. | BR-15 |
| Đợi ghép trận 60 giây vẫn một mình thì gợi ý chơi đơn, hoặc tạo phòng riêng cùng bộ lọc để mời bạn | Kế hoạch đã ghi rủi ro "ít người online". Không để người chơi chờ mù. | BR-13 |
| Đoạn nhạc lỗi giữa trận thì thay bằng bài khác | Lỗi kỹ thuật không được làm hỏng trận. Chỉ server xác nhận lỗi để không ai lấy cớ bỏ qua bài khó. | BR-27 |
| Kết quả thử thách ngày chia sẻ không lộ tên bài | Chia sẻ là kênh kéo người mới; lộ tên bài thì hỏng thử thách của người chưa chơi. | BR-25 |
| Bộ nhạc không đủ bài thì báo rõ còn thiếu bao nhiêu bài | Chủ phòng biết nên đổi bộ nhạc hay giảm số vòng, thay vì gặp một lỗi chung chung. | BR-09 |
| Chặn chat lộ đáp án bằng cùng cách chuẩn hóa tên bài với BR-05; hết vòng thì nói tự do | Chấm điểm và chặn chat dùng chung một cách so khớp nên không lệch nhau; bàn tán sau mỗi vòng là phần vui của game nhóm. | BR-16 |
| Người dùng tự xóa được tài khoản; lịch sử trận được ẩn danh | Quyền riêng tư của người dùng và là yêu cầu bắt buộc của Facebook Login (FR-03); ẩn danh để bảng xếp hạng và lịch sử của người khác không bị sai. | BR-21 |

### 2.4 Quyết định của PO ngày 01/11 (Cập nhật luật xếp hạng - MEL-149)

| Điểm thay đổi | Quyết định của PO | Lý do | BR liên quan |
|---|---|---|---|
| Chế độ tính điểm xếp hạng (rank) | Xếp hạng **chỉ đến từ ghép trận nhanh** (2–5 người). Chơi đơn bỏ chế độ "Xếp hạng", chỉ còn "Luyện tập". Phòng tự tạo và thử thách ngày không tính điểm rank. | Xếp hạng phải phản ánh trình độ thi đấu đối kháng trực tiếp, công bằng và ngẫu nhiên. Chơi đơn và phòng riêng tự chọn được đối thủ hoặc bài nên dễ cày điểm/thao túng; ghép trận nhanh hệ thống tự ghép và chia bài nên công bằng tuyệt đối. | BR-13, BR-23, BR-24 |
| Điểm rank cộng/trừ cố định theo thứ hạng | Áp dụng bảng cố định theo số người trong trận:<br>• 2 người: +20 / −15<br>• 3 người: +25 / 0 / −15<br>• 4 người: +30 / +10 / −10 / −20<br>• 5 người: +30 / +15 / 0 / −10 / −20 | Luật đơn giản, dễ nhớ, minh bạch. Tổng điểm dương lớn hơn hoặc bằng điểm âm giúp người chơi có động lực leo rank, nhưng vẫn phạt thỏa đáng người thua. | BR-24 |
| Điểm sàn và điểm khởi đầu | Bắt đầu từ 0, **sàn 0** (không xuống dưới 0). Điểm hiển thị là số thay đổi thực tế sau khi áp sàn. | Không để điểm âm làm nản lòng người chơi mới hoặc người đang gặp chuỗi thua. Hiển thị số thay đổi thực tế giúp người chơi hiểu chính xác biến động điểm của mình. | BR-24 |
| Rời phòng giữa trận hoặc mất kết nối | Rời trận hoặc mất kết nối quá 30 giây (BR-14) **tính là hạng chót** khi tính điểm rank. Phòng chỉ còn 1 người thì trận kết thúc sớm, người còn lại xếp trên người đã rời. | Chặn hành vi thoát game (rage-quit) để trốn trừ điểm rank khi gặp đối thủ mạnh hoặc thế trận bất lợi; bảo vệ quyền lợi người ở lại thi đấu nghiêm túc. | BR-14, BR-24 |
| Khách trong ghép trận nhanh | Khách **không được vào ghép trận nhanh**; muốn chơi rank phải đăng nhập. Bỏ cơ chế gộp điểm trận xếp hạng của khách. | Ngăn chặn clone tài khoản khách vào phá game, treo máy làm ảnh hưởng đến rank của người khác. Yêu cầu danh tính rõ ràng cho chế độ xếp hạng mang tính cạnh tranh. | BR-13, BR-20, BR-21 |
| Bảng xếp hạng chính | **Chỉ có bảng xếp hạng chính theo điểm rank (toàn thời gian), bỏ bảng tuần.** Bảng theo ngày giữ nguyên cho thử thách ngày (BR-25). | Tập trung toàn bộ cộng đồng vào một thước đo trình độ duy nhất, bền vững và dài hạn (như hệ thống rank tiêu chuẩn trong game); tránh phân mảnh và không cần reset tuần gây mất động lực. | BR-24 |
| Thông báo đoán đúng trong vòng | Khi có người đoán đúng, cả phòng thấy "<tên> đúng ở mức 5s" **và điểm mới của người đó**, nhưng không thấy đáp án. | Giúp người chơi trong phòng nắm bắt trực tiếp biến động điểm số và cục diện trận đấu ngay trong vòng, tăng kịch tính mà vẫn giấu đáp án. | BR-07 |

### 2.5 Quyết định của PO ngày 14/11: bỏ voice chat

| Điểm thay đổi | Quyết định của PO | Lý do | BR liên quan |
|---|---|---|---|
| Voice chat trong phòng riêng | **Bỏ khỏi phạm vi đồ án** (Won't). FR-12 và UC-23 hết hiệu lực; epic E08 (MEL-8) và các ticket SPK-02 (MEL-80), INF-03 (MEL-81), BE-RTC-01 (MEL-82), FE-RTC-01 (MEL-83) bị hủy. | Voice cần WebRTC mesh, TURN server có credential tạm thời, signaling qua STOMP và tự tắt mic khi phát nhạc: nhiều phần mới, khó test tự động, rủi ro cao trong khi S5 chỉ còn hai tuần. Chat chữ (BR-16, BR-17) đã đủ cho nhóm bạn trao đổi; ai cần nói chuyện vẫn dùng Discord hay Zalo song song. | BR-18, BR-19 |
| Mã BR-18, BR-19 | Giữ mã, đánh dấu "Đã bỏ" ở mục 4; không đánh số lại BR-20 → BR-29. | Nhiều tài liệu, ticket và test đang tham chiếu theo số BR. | — |

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
Điểm vòng = Điểm cơ bản × (1 + hệ số combo) + Thưởng tốc độ   (chỉ khi đoán đúng; sai hết lượt, bỏ qua hoặc hết giờ thì Điểm vòng = 0)

Hệ số combo   = 0 %  (chưa có chuỗi hoặc vòng đầu của chuỗi)
              = 10 % (vòng thứ 2 liên tiếp đúng ở mức ≤ 5 giây)
              = 20 % (từ vòng thứ 3 liên tiếp trở đi)
Thưởng tốc độ = max(0, ⌊200 × số mili giây còn lại ÷ 45.000⌋)   (chỉ khi chơi nhiều người; chơi đơn và thử thách ngày bằng 0)
Số mili giây còn lại = 45.000 − (lúc server nhận lượt đoán − lúc server bắt đầu vòng)
```

Thử thách ngày chỉ có một bài nên không có combo; thứ hạng theo BR-25.

**Ví dụ (phòng nhiều người).** Người chơi đã đúng ở mức 2 giây ở vòng trước. Vòng này đoán đúng ở mức 5 giây khi đồng hồ còn đúng 30 giây (30.000 ms):
- Điểm cơ bản: 800
- Combo vòng thứ 2: 800 × 1,10 = 880
- Thưởng tốc độ: ⌊200 × 30.000 ÷ 45.000⌋ = ⌊133,3⌋ = 133
- **Điểm vòng: 880 + 133 = 1.013**

Lưu ý: bản preview khoảng 30 giây do nền tảng nhạc tự cắt. "2 giây đầu" là 2 giây đầu của đoạn preview. SPK-01 (đo ngày 21/10/2026) xác nhận cả Deezer và iTunes đều không công bố đoạn preview nằm ở đâu trong bài, nên độ khó của mức 2 giây giữa các bài có thể chênh nhau và cần đánh giá bằng playtest.

### 3.3 Điểm rank ghép trận nhanh (BR-24)

Điểm rank là thước đo trình độ thi đấu đối kháng nhiều người, chỉ được tính trong chế độ **Ghép trận nhanh** (2–5 người, chỉ dành cho người chơi đã đăng nhập).

Bảng cộng/trừ điểm rank cố định theo thứ hạng và số người trong trận:

| Số người trong trận | Hạng 1 | Hạng 2 | Hạng 3 | Hạng 4 | Hạng 5 |
|---|---|---|---|---|---|
| **2 người** | +20 | −15 | — | — | — |
| **3 người** | +25 | 0 | −15 | — | — |
| **4 người** | +30 | +10 | −10 | −20 | — |
| **5 người** | +30 | +15 | 0 | −10 | −20 |

**Quy tắc áp dụng:**
1. **Khởi đầu và sàn điểm:** Mỗi người chơi bắt đầu với **0 điểm rank**. Điểm rank có **sàn là 0** (không bao giờ xuống dưới 0).
2. **Điểm hiển thị:** Số điểm thay đổi hiển thị cho người chơi trên giao diện kết quả trận là số thay đổi thực tế sau khi áp sàn 0.
   - *Ví dụ 1:* Người chơi đang có 10 điểm rank, về hạng 4 trong trận 4 người (mức phạt bảng là −20): sau khi áp sàn 0, điểm mới là 0, và số thay đổi hiển thị là **−10** (thay vì −20).
   - *Ví dụ 2:* Người chơi đang có 0 điểm rank và về chót, điểm mới vẫn là 0 và hiển thị thay đổi là **0**.
   - *Ví dụ 3:* Người chơi đang có 50 điểm rank, về hạng 1 trong trận 4 người (+30 điểm): điểm mới là 80, hiển thị thay đổi là **+30**.
3. **Phân định thứ hạng trong trận:** Thứ hạng dựa trên tổng điểm trận sau tất cả các vòng (tính theo BR-02, BR-07, BR-08). Trường hợp bằng điểm nhau, phân định bằng tổng thời gian trả lời đúng ít hơn theo mili giây (BR-10).
4. **Xử lý rời trận / mất kết nối (BR-14):**
   - Rời phòng giữa trận hoặc mất kết nối quá 30 giây tính là **hạng chót** của trận khi tính điểm rank.
   - Nếu phòng chỉ còn 1 người, trận đấu kết thúc sớm; người còn lại xếp trên người đã rời và nhận điểm rank tương ứng thứ hạng của mình.

## 4. Bảng quy tắc

| Mã | Nhóm | Quy tắc | Vì sao |
|---|---|---|---|
| BR-01 | Vòng chơi | 5 mức nghe: 2, 5, 10, 20, 30 giây, tính từ đầu đoạn preview. | Mức tăng dần tạo kịch tính; 30 giây là giới hạn của preview. |
| BR-02 | Vòng chơi | Điểm cơ bản theo mức đoán đúng: 1.000 / 800 / 600 / 400 / 200. | Thưởng người nhận ra bài sớm. |
| BR-03 | Vòng chơi | Nghe lại đoạn đang mở không giới hạn, không mất điểm. | Bù cho mạng chậm hoặc lỡ nghe. |
| BR-04 ✎ | Vòng chơi | Bấm "Nghe thêm" hoặc đoán sai đều mở mức kế tiếp. Ở mức 30 giây, nút "Nghe thêm" đổi thành "Bỏ qua"; đoán sai hoặc bỏ qua ở mức này thì vòng đó 0 điểm. | Chặn đoán bừa liên tục; "Bỏ qua" giúp người không biết bài khỏi phải ngồi chờ. |
| BR-05 ✎ | Vòng chơi | Đáp án chọn từ ô gợi ý tìm trên toàn bộ kho nhạc, không cần gõ dấu. Đoán đúng khi tên bài đã chọn trùng tên bài đáp án sau chuẩn hóa: bỏ dấu, viết thường, bỏ phần trong ngoặc và phần ghi phiên bản (Remix, Live, Acoustic, feat. …). Vì vậy mọi phiên bản và bản cover cùng tên đều tính là đúng. | Game đoán tên bài, nhận ra bài là đủ, không bắt chọn đúng bản thu. Ô gợi ý tìm trên cả kho nên không lộ đáp án. Hai bài khác nhau mà trùng tên rất hiếm, chấp nhận để luật đơn giản. |
| BR-06 | Vòng chơi | Chọn đúng ca sĩ chính nhưng sai bài: báo "Đúng ca sĩ!" làm gợi ý, vẫn tính là đoán sai. | Thêm hứng thú mà không cho điểm dễ. |
| BR-07 ✎ | Vòng chơi | Nhiều người: mỗi vòng tối đa 45 giây theo đồng hồ server; mỗi người tự mở mức nghe của mình. Vòng kết thúc khi hết giờ, hoặc sớm hơn khi mọi người đã đoán đúng hoặc hết lượt. Khi có người đoán đúng, cả phòng thấy "<tên> đúng ở mức 5s" và điểm mới của người đó, nhưng không thấy đáp án. Đoán đúng được thưởng tốc độ = max(0, ⌊200 × số mili giây còn lại ÷ 45.000⌋), thời gian tính theo lúc server nhận lượt đoán; lượt đoán server nhận từ mốc 45.000 ms trở đi bị từ chối. Lượt đoán và lúc hết giờ đến sát nhau thì phân xử theo lúc server nhận lượt đoán so với mốc 45.000 ms, không theo thứ tự xử lý trong hàng đợi phòng. Chơi đơn không giới hạn thời gian mỗi vòng; thử thách ngày có trần 5 phút mỗi lượt (BR-25). Cả hai không có thưởng tốc độ. | Không ai phải chờ người khác nghe xong; thông báo tạo áp lực vui; thưởng tốc độ phân thắng thua giữa người cùng mức; chơi một mình thì không cần áp lực giờ. |
| BR-08 ✎ | Vòng chơi | Combo (chơi đơn và nhiều người, trừ thử thách ngày): mỗi vòng đoán đúng ở mức 2 hoặc 5 giây thì nối chuỗi, vòng có kết quả khác thì đứt chuỗi. Vòng thứ 2 liên tiếp được +10%, từ vòng thứ 3 được +20%, tính trên điểm cơ bản. | Thưởng người giỏi thật, giữ người chơi ở lại. Một công thức điểm cho mọi chế độ nên dễ hiểu, dễ test. |
| BR-09 ✎ | Vòng chơi | Trận 5/10/15 vòng (mặc định 10), không lặp bài. Bộ nhạc không đủ bài hợp lệ cho số vòng đã chọn thì không cho bắt đầu và báo rõ còn thiếu bao nhiêu bài. | Trận ngắn 10–15 phút; báo rõ để chủ phòng biết đổi bộ nhạc hay giảm số vòng. |
| BR-10 ✎ | Vòng chơi | Thắng = tổng điểm cao nhất; hòa thì ai có tổng thời gian trả lời đúng ít hơn thắng (tính bằng mili giây theo đồng hồ server). | Luôn có một người thắng. |
| BR-11 ✎ | Phòng | Phòng 2–5 người (số tối đa là cấu hình, mặc định 5). Mã phòng 6 ký tự gồm chữ in hoa và số, bỏ các ký tự dễ nhầm 0, O, 1, I, L. Phòng riêng chỉ vào bằng mã hoặc link; phòng công khai hiện trong danh sách. | Mở rộng số người sau mà không sửa code; đọc mã cho nhau hay gửi qua tin nhắn không bị nhầm. |
| BR-12 ✎ | Phòng | Áp dụng cho phòng tự tạo (riêng hoặc công khai). Chủ phòng chọn bộ nhạc và số vòng, bấm bắt đầu khi ≥ 2 người và tất cả đã sẵn sàng. Chủ phòng mời được người khác ra khỏi phòng; người bị mời ra không vào lại phòng đó được. Chủ rời phòng thì quyền chuyển cho người vào sớm nhất. Hết trận, cả phòng về sảnh để chơi tiếp, giữ nguyên mã phòng. Phòng ghép trận không có chủ phòng và không có nút sẵn sàng; trận bắt đầu và chơi tiếp theo đếm ngược ở BR-13. | Phòng không bị kẹt khi chủ thoát hoặc có người treo máy; chơi lại liền mạch giữ nhóm bạn ở lại; ở phòng ghép trận, một người lạ treo máy không chặn được cả phòng. |
| BR-13 ✎ | Phòng | Ghép trận nhanh là trận xếp hạng duy nhất, chỉ dành cho người chơi đã đăng nhập (khách không vào được), 2–5 người (số tối đa là cấu hình, mặc định 5). Hệ thống tự tìm phòng ghép trận đang chờ và hợp bộ lọc, không có thì tạo phòng mới; không vào phòng công khai tự tạo. Phòng ghép trận không hiện trong danh sách phòng công khai, không vào được bằng mã hay link, và không có chủ phòng (không ai chỉnh cài đặt hay mời người ra). Khi có người thứ 2, đếm ngược 30 giây (không đặt lại khi thêm người); trận tự bắt đầu khi hết đếm ngược hoặc khi đủ số người tối đa của phòng. Đang đếm ngược mà số người còn kết nối giảm xuống dưới 2 thì đếm ngược dừng; khi lại có người thứ 2 thì đếm lại đủ 30 giây. Lúc hết đếm ngược, server kiểm tra lại số người đang kết nối; dưới 2 thì không bắt đầu trận và quay về trạng thái chờ. Chờ 60 giây vẫn chỉ có một mình thì gợi ý chơi đơn, hoặc tạo phòng riêng cùng bộ lọc để mời bạn (không tính xếp hạng); người chơi vẫn chờ tiếp được. Hết trận, ai ở lại sảnh thì đếm ngược lại như trên. | Ít người online vẫn chơi được, và không ai phải chờ mà không biết bao giờ mới vào trận; trận xếp hạng luôn do hệ thống chọn bộ nhạc và người chơi (BR-23), yêu cầu đăng nhập để đảm bảo danh tính thi đấu. |
| BR-14 ✎ | Phòng | Mất kết nối được giữ chỗ 30 giây; trận không dừng lại để chờ. Vào lại kịp thì chơi tiếp ở vòng hiện tại với mức nghe đã mở; vòng bị lỡ được 0 điểm. Quá 30 giây thì coi như rời phòng, tính là hạng chót khi tính điểm rank trong ghép trận nhanh; điểm trận đã có vẫn giữ. Nếu phòng chỉ còn 1 người, trận kết thúc sớm, người còn lại xếp trên người đã rời và tính điểm rank theo thứ hạng. | Mạng di động hay chập chờn; những người còn lại không phải chờ; chống rage-quit trốn phạt rank. |
| BR-15 ✎ | Phòng | Không vào giữa trận đang diễn ra: ai vào bằng mã hoặc link lúc đó thì chờ ở sảnh (nếu phòng còn chỗ) và chơi từ trận sau. Phòng trống quá 10 phút tự đóng. | Công bằng cho người đang chơi; bạn đến muộn không bị đuổi về; giải phóng tài nguyên. |
| BR-16 ✎ | Giao tiếp | Chat tối đa 200 ký tự, 1 tin mỗi giây. Trong vòng, tin chứa tên bài đáp án (so khớp không dấu như BR-05) bị chặn, chỉ người gửi thấy cảnh báo; hết vòng thì nói tự do. | Chống spam và chống nhắc bài, nhưng vẫn cho bàn tán sau mỗi vòng. |
| BR-17 ✎ | Giao tiếp | Lọc từ cấm theo danh sách admin quản lý: tin chứa từ cấm bị chặn cả tin (không che bằng ***), chỉ người gửi thấy cảnh báo, dùng chung cơ chế chặn tin với BR-16. Biệt danh chứa từ cấm bị từ chối ngay khi nhập (BR-22). Người chơi báo cáo được tin vi phạm. | An toàn khi chơi với người lạ; một cơ chế chặn tin cho cả lộ đáp án và từ cấm nên ít code, ít test. |
| BR-18 ✎ | Giao tiếp | **Đã bỏ ngày 14/11/2026** (mục 2.5). ~~Voice chat chỉ có trong phòng riêng; mic mặc định tắt.~~ | Meloo không có voice chat. |
| BR-19 ✎ | Giao tiếp | **Đã bỏ ngày 14/11/2026** (mục 2.5). ~~Mic tự tắt khi đoạn nhạc của chính người đó đang phát, và tự bật lại khi nhạc dừng nếu trước đó đang bật; giao diện báo "Mic tạm tắt khi phát nhạc".~~ | Meloo không có voice chat. |
| BR-20 ✎ | Tài khoản | Khách không vào được ghép trận nhanh; muốn chơi rank phải đăng nhập. Khách chơi chơi đơn và phòng tự tạo (riêng hoặc công khai) bằng biệt danh. Điểm của khách không vào bảng xếp hạng. Dữ liệu khách không hoạt động 30 ngày bị xóa. Khách xem được bảng xếp hạng nhưng không có tên trên bảng, và có hồ sơ chỉ xem (lịch sử, thống kê, huy hiệu của phiên khách) kèm lời nhắc đăng nhập để đưa điểm lên bảng. Xóa tài khoản chỉ dành cho người đã đăng nhập. | Vào chơi không có rào cản ở chơi đơn và phòng nhóm; bảo vệ tính công bằng của ghép trận xếp hạng; dữ liệu gọn, đúng quyền riêng tư; khách thấy thành tích mình đang tích lũy nên có lý do tạo tài khoản. |
| BR-21 ✎ | Tài khoản | Đăng nhập Google/Facebook. Khi khách đăng nhập, lịch sử, thống kê và huy hiệu của khách được gộp vào tài khoản (bỏ gộp điểm xếp hạng vì khách không chơi ghép trận rank). Nếu cả khách và tài khoản đều đã chơi thử thách ngày trong cùng ngày thì giữ lượt của tài khoản. Người dùng tự xóa được tài khoản: dữ liệu cá nhân bị xóa, lịch sử trận được ẩn danh. | Không mất thành tích khi chuyển đổi; "đăng nhập để chơi rank và đưa điểm lên bảng xếp hạng" là lý do tự nhiên để tạo tài khoản; xóa tài khoản là quyền riêng tư và là yêu cầu của Facebook Login. |
| BR-22 | Tài khoản | Biệt danh 3–20 ký tự, không chứa từ cấm. | Hiển thị gọn, văn minh. |
| BR-23 ✎ | Xếp hạng | Xếp hạng chỉ đến từ ghép trận nhanh (trận xếp hạng duy nhất, 2–5 người, BR-13). Chơi đơn không còn chế độ "Xếp hạng", chỉ còn chế độ "Luyện tập" (tự chọn bộ lọc hoặc bộ bài); vẫn có điểm, combo, lưu lịch sử, thống kê và tính huy hiệu nhưng không tính điểm rank và không lên bảng xếp hạng. Phòng tự tạo, dù riêng hay công khai, không tính xếp hạng nhưng vẫn lưu lịch sử, thống kê và tính huy hiệu. Thử thách ngày có bảng theo ngày riêng (BR-25), không cộng điểm rank. Chơi đơn bỏ dở: server giữ trận 30 phút; mở lại trong thời gian đó thì chơi tiếp đúng vòng và mức nghe đã mở, không đổi bài. Quá 30 phút trận chuyển sang bỏ dở (ABANDONED): lưu các vòng đã xong, vòng đang dở 0 điểm. | Chống cày điểm bằng tài khoản phụ ở nơi tự chọn được bộ nhạc và người chơi; phân định rõ ranh giới giữa luyện tập cá nhân và thi đấu xếp hạng. Giữ trận 30 phút để người lỡ đóng tab không mất trận, nhưng không ai tải lại trang để đổi bài khó. |
| BR-24 ✎ | Xếp hạng | Bảng xếp hạng chính là bảng xếp hạng theo điểm rank (toàn thời gian), bỏ bảng tuần (PO duyệt 01/11). Điểm rank bắt đầu từ 0 và có sàn 0 (không xuống dưới 0). Thứ hạng trong trận theo tổng điểm trận (hòa theo BR-10). Điểm rank cộng/trừ theo bảng cố định ở mục 3.3 (+20/−15 cho 2 người; +25/0/−15 cho 3 người; +30/+10/−10/−20 cho 4 người; +30/+15/0/−10/−20 cho 5 người). Điểm hiển thị cho người chơi là số thay đổi thực tế sau khi áp sàn. Rời phòng giữa trận hoặc mất kết nối quá 30 giây (BR-14) tính là hạng chót. Bằng điểm rank thì ai đạt mốc điểm trước xếp trên. Bảng theo ngày là bảng thử thách ngày (BR-25), không cộng điểm rank. | Tập trung cộng đồng vào một thang đo trình độ duy nhất, bền vững; bảng điểm cố định minh bạch; sàn 0 tránh gây nản cho người mới. |
| BR-25 ✎ | Xếp hạng | Thử thách ngày: một bài chung, mỗi người một lượt, đổi bài lúc 00:00 giờ Việt Nam. Lượt thuộc về ngày mà server cấp bài (lúc bắt đầu lượt), kể cả khi đoán xong sau 00:00. Mỗi lượt có trần 5 phút; hết trần mà chưa đoán đúng thì kết quả là "Chưa đoán ra". Xếp theo mức đoán đúng (2 giây cao nhất), rồi theo thời gian từ lúc bắt đầu tới khi đoán đúng; không đoán ra thì xếp cuối. Kết quả chia sẻ gồm chuỗi ô màu theo từng mức (mức đoán đúng, mức đã nghe mà chưa đúng, mức chưa mở), nhãn mức đoán đúng (vd. "Đúng ở 10s"; không đoán ra thì "Chưa đoán ra") và link; không lộ tên bài. | Tạo thói quen quay lại mỗi ngày; chia sẻ kéo người mới mà không làm hỏng bài cho người chưa chơi; nhãn chữ giúp người chưa chơi hiểu ngay kết quả và không phụ thuộc vào màu (NFR-13); trần 5 phút để không ai mở lượt rồi treo máy đi hỏi người khác. |
| BR-26 | Xếp hạng | Huy hiệu mở khóa tự động khi đạt điều kiện, không bị thu hồi. | Khuyến khích chơi lâu dài. |
| BR-27 ✎ | Quản trị | Bài lỗi preview hoặc không phù hợp bị vô hiệu hóa, không xuất hiện ở trận mới. Nếu server xác nhận đoạn nhạc lỗi giữa trận, vòng đó được thay bằng bài khác, không ai bị trừ điểm. | Trận không hỏng vì lỗi kỹ thuật; chỉ server xác nhận để không ai lấy cớ "lỗi" bỏ qua bài khó. |
| BR-28 ✎ | Quản trị | Tài khoản bị khóa không đăng nhập, không tạo hay vào phòng được. Tin bị báo do khách gửi thì khóa theo id khách: id đó không tạo hay vào phòng, không chat được; khách xóa dữ liệu trình duyệt thì có id mới (giới hạn đã biết). MVP không có mức cảnh cáo: khi xử lý báo cáo vi phạm, quản trị viên chọn bỏ qua, khóa hoặc thêm từ cấm. Khóa phải nhập lý do; quản trị viên mở khóa lại được. Mọi thao tác admin, kèm lý do khóa, ghi nhật ký. | Truy vết, chống lạm quyền; khóa được cả người lạ không có tài khoản. Cảnh cáo cần thêm trạng thái và thông báo nên để Phase 2 (nguyên tắc 3). |
| BR-29 | Quản trị | Sau mỗi vòng hiện nguồn nhạc và link nghe đầy đủ trên nền tảng gốc. | Tôn trọng bản quyền và điều khoản API. |

## 5. Nguồn nhạc

- **Đề xuất: Deezer là nguồn chính; iTunes là dự phòng kỹ thuật, tắt mặc định** (ADR-003, đang ở trạng thái Đề xuất; số liệu SPK-01 đo ngày 21/10/2026). ADR-003 chỉ chuyển sang Chấp nhận khi LEG-02 kết luận "Được phép" hoặc "Được phép có điều kiện"; nếu không được phép thì ADR-003 phải viết lại với nguồn audio khác. **Ngoại lệ 2026-10-28:** Thái (PO) quyết định vẫn dùng Deezer cho beta và tự chấp nhận rủi ro; LEG-02 kết luận "Chưa được phép", Deezer chưa xác nhận cách dùng của Meloo (xem [`docs/legal/music-preview-licensing.md`](../legal/music-preview-licensing.md) Mục 6.1). Lý do: điều khoản iTunes Search API cấm dùng preview làm giá trị giải trí độc lập, mà game đoán nhạc dùng preview đúng theo cách đó. Deezer có preview cho 100% bài V-pop và US-UK trong mẫu đo, dùng khoảng nửa băng thông (MP3 128 kbps so với AAC khoảng 268 kbps) và có API chart, playlist, ca sĩ để nhập kho nhạc hàng loạt.
- **Cổng phát hành:** beta công khai chỉ dùng preview Deezer khi LEG-02 (MEL-120) có kết luận "Được phép" hoặc "Được phép có điều kiện". **Ngoại lệ 2026-10-28:** Thái (PO) quyết định vẫn dùng Deezer cho beta và tự chấp nhận rủi ro; LEG-02 kết luận "Chưa được phép" (xem [`docs/legal/music-preview-licensing.md`](../legal/music-preview-licensing.md) Mục 6.1).
- **Hệ quả cho luật chơi:** BR-29 hiện nguồn Deezer và link nghe đầy đủ trên Deezer. Bài không có trên Deezer (4/20 bài K-pop trong mẫu đo) bị loại khỏi bộ đề, không thay bằng bản cover. BR-05 chỉ chấp nhận bản cover khi chấm câu trả lời của người chơi; bài dùng làm đáp án luôn là bản gốc.

## 6. Ticket đã cập nhật Acceptance Criteria theo bản này

Ngày 22/10/2026 đã sửa AC trên Jira cho 29 ticket; cuối mô tả mỗi ticket có dòng "Cập nhật 22/10".

- **Game và chơi đơn:** MEL-37 BE-GAME-01, MEL-45 BE-SOLO-01, MEL-31 CT-01, MEL-48 FE-GAME-01, MEL-49 FE-SOLO-02, MEL-50 QA-01.
- **Phòng:** MEL-43 CT-02, MEL-55 BE-ROOM-01, MEL-56 BE-ROOM-02, MEL-70 BE-ROOM-03, MEL-58 FE-ROOM-01, MEL-59 FE-ROOM-02, MEL-76 FE-ROOM-03, MEL-61 QA-02.
- **Tài khoản, xếp hạng, thử thách ngày, huy hiệu:** MEL-30 DES-01, MEL-65 CT-03, MEL-67 BE-ID-02, MEL-68 BE-PROG-01, MEL-69 BE-DAILY-01 (thêm phụ thuộc BE-ID-02 để gộp lượt và chuỗi ngày của khách), MEL-74 FE-LB-01, MEL-75 FE-DAILY-01 (thêm khi nghiệm thu: nhãn mức trong kết quả chia sẻ), MEL-84 BE-ID-03, MEL-85 BE-PROG-02.
- **Chat, voice, audio, nguồn nhạc:** MEL-57 BE-CHAT-01, MEL-83 FE-RTC-01 (đã hủy 14/11/2026), MEL-44 BE-AUD-01, MEL-34 BE-CAT-01.
- **Vận hành và đo NFR:** MEL-91 INF-04, MEL-94 QA-04.

## 7. Câu hỏi bảo vệ

1. **Vì sao đoán sai cũng bị xuống mức?** Nếu đoán sai không mất gì, người chơi sẽ chọn bừa trong ô gợi ý đến khi trúng. Mỗi lần sai làm mất 200 điểm tiềm năng nên người chơi phải cân nhắc. Heardle dùng cùng cơ chế này.
2. **Vì sao phòng riêng không tính xếp hạng nhưng vẫn tính huy hiệu?** Ở phòng tự tạo, người chơi chọn được bộ nhạc và người cùng chơi, nên dễ cày điểm bằng tài khoản phụ; bảng xếp hạng cần công bằng. Huy hiệu là thành tích cá nhân, không so với ai, nên vẫn tính để nhóm bạn thấy mình tiến bộ.
3. **Vì sao combo chỉ tính khi đúng ở mức 5 giây trở xuống và chỉ nhân điểm cơ bản?** Combo thưởng người nhận ra bài rất nhanh. Chỉ nhân điểm cơ bản để thưởng tốc độ không bị nhân thêm, tránh chênh lệch điểm quá lớn. Ví dụ ở mục 3.2 ra 1.013 điểm.
4. **Mất kết nối thì sao trận không chờ?** Một người rớt mạng không được bắt bốn người còn lại chờ. Giữ chỗ 30 giây đủ cho mạng di động chập chờn; vòng bị lỡ được 0 điểm, điểm đã có vẫn giữ.
5. **Khách không chơi ghép trận nhanh và không lên bảng xếp hạng thì có thiệt không?** Khách vẫn trải nghiệm trọn vẹn gameplay cốt lõi qua chơi đơn và phòng tự tạo cùng bạn bè (vẫn có combo, lịch sử, thống kê, huy hiệu). Ghép trận nhanh là đấu trường xếp hạng nghiêm túc nên yêu cầu đăng nhập để tránh clone/troll phá trận. Khi đăng nhập, toàn bộ thành tích đã tích lũy (lịch sử, thống kê, huy hiệu) được gộp vào tài khoản. Đây là lý do tự nhiên để người chơi tạo tài khoản, phục vụ KPI ≥ 30 tài khoản trong beta.

## 8. Biên bản duyệt

Product Owner: Trần Văn Thái. Ngày 22/10/2026, Thái giao Claude chốt các điểm còn mở theo ba nguyên tắc ở mục 1; agent MEL-28 đưa bản chốt vào repo và hỏi lại qua review PR. Bảng dưới ghi lại từng vòng để truy vết quyết định.

| Vòng | Ngày | Nội dung | Người quyết | Ghi ở đâu |
|---|---|---|---|---|
| Chốt BR | 22/10/2026 | Các điểm còn mở của bản 17/10; 19 BR có dấu ✎ ở vòng này; MEL-29 thêm BR-17 và BR-28 (BR-04, 05, 07, 08, 09, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 27) | Claude, theo ủy quyền của Thái | Mục 2.1, 2.3, 4 |
| Review PR lần 1 | 22/10/2026 | 6 câu hỏi của agent: trạng thái ADR-003, câu cũ về SPK-01, câu bảo vệ 5, ghép trận nhanh (BR-13, BR-23), các điểm nhỏ (BR-07, BR-09, BR-16, BR-21), mã use case ở AC1 | Claude, theo ủy quyền của Thái | Dòng "(review PR)" ở mục 2.2 |
| Review PR lần 2 | 22/10/2026 | 5 điểm Minor: link mời ở BR-13, thưởng tốc độ âm, FR-07 và FR-08 trỏ BR-07, điều kiện chuyển ADR-003 sang Chấp nhận, FR-09 thiếu phòng công khai | Claude, theo ủy quyền của Thái | Dòng "(review PR lần 2)" ở mục 2.2 |
| Review PR lần 3 | 22/10/2026 | 4 điểm tùy chọn của QA: phạm vi BR-12, lượt đoán sát giờ, công thức "chỉ khi đoán đúng", cách viết "5s" và "5 giây" | Claude, theo ủy quyền của Thái | Dòng "(review PR lần 3)" ở mục 2.2 |
| Nghiệm thu | 22/10/2026 | Nhãn mức đoán đúng trong kết quả chia sẻ (BR-25); thêm biên bản này | Claude, theo ủy quyền của Thái | Dòng "(nghiệm thu)" ở mục 2.2 |
| Đặc tả use case (MEL-29) | 22/10/2026 | 8 điểm còn mở khi đặc tả use case: luyện tập và trận bỏ dở (BR-23), khách xem bảng và hồ sơ (BR-20), ngày của lượt và trần 5 phút (BR-25, BR-07), đếm ngược khi người rời (BR-13), chặn tin từ cấm (BR-17), cảnh cáo và khóa khách (BR-28) | Thái | Dòng "(use case MEL-29)" ở mục 2.2; `usecases/00-tong-quan.md` mục 5 |
| Quyết định LEG-02 (MEL-133) | 28/10/2026 | Ghi ngoại lệ: LEG-02 kết luận "Chưa được phép" nhưng vẫn dùng Deezer cho beta và tự chấp nhận rủi ro; giữ nguyên quy tắc gốc, không đổi BR nào | Thái (PO) quyết định | Mục 5: "Đề xuất: Deezer là nguồn chính", "Cổng phát hành"; "BR-27 và mục 5" ở danh sách BR cần theo dõi |
| Quyết định luật xếp hạng mới (MEL-149) | 01/11/2026 | PO duyệt 01/11: luật xếp hạng mới — rank chỉ từ ghép trận nhanh 2–5 người (chỉ người đã đăng nhập), tính điểm rank theo thứ hạng bảng cố định, sàn 0, điểm hiển thị sau áp sàn; chơi đơn bỏ xếp hạng; bỏ bảng tuần; BR-07 hiện điểm mới khi đoán đúng; BR-14 rời phòng xếp chót | Thái (PO) quyết định | Mục 2.2, 2.4, 3.3, 4 (BR-07, 13, 14, 20, 21, 23, 24); biên bản mục 8 |

**BR cần theo dõi sau khi duyệt** (đánh dấu để hỏi lại, theo AC của MEL-28):

- **BR-01:** preview không công bố vị trí đoạn trích trong bài (SPK-01), nên độ khó mức 2 giây có thể chênh giữa các bài. Xem lại sau playtest.
- **BR-27 và mục 5:** chỉ dùng preview Deezer cho beta công khai khi LEG-02 (MEL-120) kết luận "Được phép" hoặc "Được phép có điều kiện". **Ngoại lệ 2026-10-28:** Thái (PO) quyết định vẫn dùng Deezer cho beta và tự chấp nhận rủi ro; LEG-02 kết luận "Chưa được phép" (xem [`docs/legal/music-preview-licensing.md`](../legal/music-preview-licensing.md) Mục 6.1).

**Duyệt cuối của Product Owner**

- [x] Thái đã đọc mục 4 (BR-01 → BR-29) và duyệt bản này. Ngày: 22/10/2026 · Bằng chứng: comment duyệt của Thái trên PR: https://github.com/ThaiDevv/meloo/pull/17#issuecomment-6334499587
- [x] Thái duyệt luật xếp hạng mới (MEL-149). Ngày: 01/11/2026 · Bằng chứng: Quyết định của PO ngày 01/11/2026 ghi trong MEL-149.
- [x] Thái quyết định bỏ voice chat (BR-18, BR-19, FR-12). Ngày: 14/11/2026 · Bằng chứng: yêu cầu của Thái trong phiên làm việc ngày 14/11/2026 (mục 2.5).
