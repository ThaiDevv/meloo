# Đặc tả use case: Chơi đơn

> Ticket: MEL-29 (DOC-02) · Tổng quan, tác nhân, danh mục và truy vết: `00-tong-quan.md` · Sơ đồ: `docs/uml/usecase/uc-02-choi-don.png`

### UC-07: Chơi luyện tập
- Tác nhân chính / phụ: Khách (gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Người chơi có phiên (khách hoặc đã đăng nhập). Kho nhạc có đủ bài hợp lệ cho bộ chọn.
- Hậu điều kiện (thành công / thất bại): Thành công: trận đơn kết thúc, người chơi thấy tổng điểm; trận được lưu vào lịch sử và thống kê. Nếu là trận xếp hạng và người chơi đã đăng nhập thì điểm vào bảng xếp hạng. Thất bại: trận không bắt đầu, người chơi ở màn hình chọn bộ.
- Luồng chính:
  1. Người chơi chọn "Chơi đơn".
  2. Người chơi chọn bộ chuẩn hoặc đặt bộ lọc (thể loại, quốc gia, ca sĩ, thập niên) và số vòng 5, 10 hoặc 15 (mặc định 10).
  3. Hệ thống kiểm tra kho nhạc đủ bài hợp lệ và chọn các bài, không lặp.
  4. Với mỗi vòng, người chơi đoán bài hát (UC-08, include). Chơi đơn không giới hạn thời gian mỗi vòng nên không có thưởng tốc độ.
  5. Sau mỗi vòng, hệ thống hiển thị đáp án, điểm vòng, combo hiện tại, nguồn nhạc và link nghe đầy đủ trên nền tảng gốc.
  6. Hết số vòng, hệ thống hiển thị tổng điểm.
  7. Hệ thống lưu trận vào lịch sử và thống kê cá nhân.
- Luồng phụ và ngoại lệ:
  - 3a. Bộ chọn không đủ bài cho số vòng đã chọn: hệ thống không cho bắt đầu và báo còn thiếu bao nhiêu bài; người chơi đổi bộ lọc hoặc giảm số vòng.
  - 4a. Đoạn nhạc lỗi giữa trận (do server xác nhận): vòng được thay bằng bài khác, không ai bị trừ điểm.
  - 4b. Người chơi thoát giữa trận: `[HỎI THÁI]` trận có được lưu và tính điểm không (mục 5 của `00-tong-quan.md`, điểm 3).
  - 7a. Người chơi là khách: điểm không vào bảng xếp hạng. `[HỎI THÁI]` bộ lọc tự chọn có tính xếp hạng không; đang suy ra là chỉ bộ chuẩn tính (điểm 1).
- Quy tắc nghiệp vụ: BR-01 → BR-09, BR-20, BR-23, BR-27, BR-29.
- Yêu cầu liên quan: FR-07, FR-13, NFR-04.

### UC-08: Đoán bài hát trong vòng
- Tác nhân chính / phụ: Khách (người chơi đang ở trong vòng) / Hệ thống API nhạc.
- Tiền điều kiện: Một vòng đã bắt đầu, từ chơi đơn (UC-07), thử thách ngày (UC-10) hoặc trận nhiều người (UC-18). Người chơi đang ở mức nghe đầu tiên (2 giây).
- Hậu điều kiện (thành công / thất bại): Thành công: vòng có kết quả "đúng ở mức X" và điểm vòng được tính. Thất bại: vòng kết thúc với 0 điểm (đoán sai ở mức 30 giây, bỏ qua, hoặc hết giờ ở trận nhiều người).
- Luồng chính:
  1. Hệ thống lấy đoạn preview của bài từ Hệ thống API nhạc và cấp đoạn nghe của mức hiện tại cho người chơi qua token mờ (URL không chứa id hay tên bài).
  2. Người chơi nghe đoạn nhạc; nghe lại không giới hạn và không mất điểm.
  3. Người chơi gõ tên bài vào ô gợi ý (UC-06, include), kết quả tìm trên toàn kho nhạc và không cần gõ dấu.
  4. Người chơi chọn một bài và gửi lượt đoán.
  5. Server chuẩn hóa tên bài đã chọn và tên đáp án (bỏ dấu, viết thường, bỏ phần trong ngoặc và phần ghi phiên bản) rồi so khớp.
  6. Trùng khớp: hệ thống tính điểm vòng (điểm cơ bản theo mức, combo, thưởng tốc độ nếu là trận nhiều người) và kết thúc vòng cho người chơi.
  7. Hệ thống hiển thị đáp án chỉ sau khi vòng kết thúc.
- Luồng phụ và ngoại lệ:
  - 2a. Người chơi bấm "Nghe thêm" (UC-09, extend): hệ thống mở mức kế tiếp (2 → 5 → 10 → 20 → 30 giây) và cấp token cho mức đó; quay lại bước 2.
  - 2b. Ở mức 30 giây nút "Nghe thêm" đổi thành "Bỏ qua": người chơi bấm thì vòng 0 điểm.
  - 2c. Token hết hạn khi nghe lại: hệ thống cấp token mới cho đúng mức đã mở.
  - 5a. Đoán sai: hệ thống mở mức kế tiếp như "Nghe thêm"; ở mức 30 giây thì vòng 0 điểm.
  - 5b. Đoán sai nhưng đúng ca sĩ chính: hệ thống báo "Đúng ca sĩ!" làm gợi ý, vẫn tính là đoán sai.
  - 5c. Trận nhiều người: lượt đoán server nhận từ mốc 45.000 ms trở đi bị từ chối, vòng đã hết giờ với người đó. Lượt đoán sát giờ được phân xử theo lúc server nhận so với mốc 45.000 ms.
  - 1a. Đoạn nhạc lỗi (do server xác nhận): thay bằng bài khác, không ai bị trừ điểm.
  - 1b. Hệ thống API nhạc không trả được preview: xử lý như 1a.
- Quy tắc nghiệp vụ: BR-01 → BR-08, BR-27; BR-07 cho trận nhiều người.
- Yêu cầu liên quan: FR-06, FR-07, NFR-04 (nghe < 1 giây sau khi vòng bắt đầu), NFR-07 (không lộ đáp án trước khi vòng kết thúc).

### UC-10: Làm thử thách hằng ngày
- Tác nhân chính / phụ: Khách (gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Người chơi có phiên và chưa dùng lượt của ngày hôm nay (giờ Việt Nam).
- Hậu điều kiện (thành công / thất bại): Thành công: lượt hôm nay đã dùng, kết quả được lưu; nếu đã đăng nhập thì kết quả vào bảng theo ngày. Thất bại: lượt chưa dùng, người chơi quay về màn hình chọn chế độ.
- Luồng chính:
  1. Người chơi chọn "Thử thách hằng ngày".
  2. Hệ thống kiểm tra người chơi còn lượt hôm nay.
  3. Hệ thống mở một vòng với bài chung của ngày hôm nay.
  4. Người chơi đoán bài hát (UC-08, include) không giới hạn thời gian, không có combo.
  5. Hệ thống hiển thị kết quả: mức đoán đúng (hoặc "Chưa đoán ra"), thời gian từ lúc bắt đầu, vị trí trên bảng theo ngày.
  6. Người chơi chọn chia sẻ kết quả (UC-11, extend): hệ thống tạo chuỗi ô màu theo từng mức, nhãn "Đúng ở 10s" (không đoán ra thì "Chưa đoán ra") và link; không lộ tên bài.
- Luồng phụ và ngoại lệ:
  - 2a. Hết lượt hôm nay: hệ thống hiển thị kết quả đã có và bảng theo ngày, không cho chơi lại. Bài đổi lúc 00:00 giờ Việt Nam.
  - 4a. Qua 00:00 khi đang chơi: `[HỎI THÁI]` tính vào ngày nào (mục 5 của `00-tong-quan.md`, điểm 4).
  - 5a. Người chơi là khách: kết quả không vào bảng theo ngày; hệ thống gợi ý đăng nhập để đưa kết quả lên bảng.
- Quy tắc nghiệp vụ: BR-07, BR-08, BR-20, BR-21, BR-25.
- Yêu cầu liên quan: FR-08, FR-13, FR-14.
