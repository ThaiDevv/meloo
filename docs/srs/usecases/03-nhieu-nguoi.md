# Đặc tả use case: Chơi nhiều người và giao tiếp

> Ticket: MEL-29 (DOC-02) · Tổng quan, tác nhân, danh mục và truy vết: `00-tong-quan.md` · Sơ đồ: `docs/uml/usecase/uc-03-nhieu-nguoi.png`

### UC-12: Tạo phòng
- Tác nhân chính / phụ: Chủ phòng (Khách hoặc Người chơi đã đăng nhập đang tạo phòng) / không có.
- Tiền điều kiện: Người chơi có phiên và tài khoản không bị khóa.
- Hậu điều kiện (thành công / thất bại): Thành công: phòng tự tạo ở trạng thái sảnh chờ, có mã phòng, người tạo là chủ phòng. Thất bại: không có phòng nào được tạo.
- Luồng chính:
  1. Người chơi chọn "Tạo phòng".
  2. Người chơi chọn loại phòng: riêng (vào bằng mã hoặc link) hoặc công khai (hiện trong danh sách phòng).
  3. Người chơi chọn bộ nhạc và số vòng 5, 10 hoặc 15 (mặc định 10).
  4. Hệ thống tạo phòng, sinh mã 6 ký tự gồm chữ in hoa và số, bỏ các ký tự 0, O, 1, I, L.
  5. Hệ thống đưa người tạo vào sảnh chờ với vai trò chủ phòng và hiển thị mã phòng cùng link mời.
- Luồng phụ và ngoại lệ:
  - 2a. Phòng công khai: hệ thống thêm phòng vào danh sách phòng công khai (UC-14).
  - 2b. Phòng riêng: voice chat có trong phòng (UC-23); phòng công khai không có voice.
  - 5a. Phòng trống quá 10 phút: hệ thống tự đóng phòng.
  - 5b. Chủ phòng rời phòng: quyền chủ phòng chuyển cho người vào phòng sớm nhất.
  - Ghi chú: việc kiểm tra bộ nhạc đủ bài làm ở UC-17.
- Quy tắc nghiệp vụ: BR-09, BR-11, BR-12, BR-15, BR-18, BR-23 (phòng tự tạo không tính xếp hạng), BR-28.
- Yêu cầu liên quan: FR-09.

### UC-13: Vào phòng bằng mã hoặc link
- Tác nhân chính / phụ: Khách (gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Người chơi biết mã hoặc có link mời. Nếu chưa có phiên thì làm UC-01 trước rồi quay lại bước 1.
- Hậu điều kiện (thành công / thất bại): Thành công: người chơi ở trong sảnh chờ của phòng. Thất bại: người chơi không vào được phòng và thấy lý do.
- Luồng chính:
  1. Người chơi nhập mã phòng hoặc mở link mời.
  2. Hệ thống tìm phòng theo mã.
  3. Hệ thống kiểm tra phòng còn chỗ (tối đa 2–5 người, mặc định 5) và người chơi chưa bị chủ phòng mời ra khỏi phòng này.
  4. Hệ thống đưa người chơi vào sảnh chờ, hiển thị danh sách người trong phòng và chủ phòng.
  5. Hệ thống báo cho những người trong phòng có người mới vào.
- Luồng phụ và ngoại lệ:
  - 2a. Mã sai hoặc phòng đã đóng: hệ thống báo không tìm thấy phòng.
  - 2b. Mã hoặc link của phòng ghép trận: phòng ghép trận không vào được bằng mã hay link nên xử lý như 2a.
  - 3a. Phòng đầy: hệ thống báo phòng đã đủ người.
  - 3b. Người chơi đã bị mời ra khỏi phòng này: hệ thống từ chối, không cho vào lại.
  - 3c. Trận đang chạy: người chơi vào sảnh chờ (nếu còn chỗ) và chơi từ trận sau, không bị báo lỗi.
- Quy tắc nghiệp vụ: BR-11, BR-12, BR-13, BR-15.
- Yêu cầu liên quan: FR-09, NFR-11 (từ lúc mở link đến khi thấy sảnh ≤ 3 giây, p95).

### UC-15: Ghép trận nhanh
- Tác nhân chính / phụ: Khách (gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Người chơi có phiên (khách cũng chơi được).
- Hậu điều kiện (thành công / thất bại): Thành công: người chơi ở trong phòng ghép trận và trận tự bắt đầu (UC-18). Thất bại: người chơi rời hàng chờ trước khi trận bắt đầu và không bị tính trận.
- Luồng chính:
  1. Người chơi chọn "Ghép trận nhanh" và bộ lọc.
  2. Hệ thống tìm phòng ghép trận đang chờ và hợp bộ lọc; không có thì tạo phòng ghép trận mới. Hệ thống không đưa người chơi vào phòng công khai tự tạo.
  3. Người chơi vào sảnh chờ của phòng ghép trận.
  4. Khi có người thứ 2, hệ thống bắt đầu đếm ngược 30 giây; thêm người không đặt lại đếm ngược.
  5. Hết đếm ngược, hoặc khi đủ số người tối đa của phòng, trận tự bắt đầu (UC-18).
- Luồng phụ và ngoại lệ:
  - 4a. Chờ 60 giây vẫn chỉ có một mình: hệ thống gợi ý chơi đơn (UC-07) hoặc "Tạo phòng riêng và mời bạn" (người chơi rời hàng chờ, UC-12 cùng bộ lọc, trận không tính xếp hạng). Người chơi vẫn chờ tiếp được.
  - 4b. Người thứ 2 rời đi khi đang đếm ngược: `[HỎI THÁI]` đếm ngược dừng hay chạy tiếp (mục 5 của `00-tong-quan.md`, điểm 5).
  - 5a. Hết trận, ai ở lại sảnh thì đếm ngược lại như bước 4 (UC-20).
  - Ghi chú: phòng ghép trận không có chủ phòng, không có nút sẵn sàng, không mời người ra, không hiện trong danh sách phòng công khai và không vào được bằng mã hay link.
  - Ghi chú: khách chơi được nhưng điểm không vào bảng xếp hạng; người đã đăng nhập trong cùng trận vẫn được tính.
- Quy tắc nghiệp vụ: BR-12, BR-13, BR-20, BR-23.
- Yêu cầu liên quan: FR-10, NFR-02.

### UC-17: Bắt đầu trận
- Tác nhân chính / phụ: Chủ phòng / không có.
- Tiền điều kiện: Phòng tự tạo đang ở sảnh chờ, có ít nhất 2 người và tất cả đã báo sẵn sàng (UC-16).
- Hậu điều kiện (thành công / thất bại): Thành công: phòng chuyển sang trạng thái đang chơi, vòng 1 bắt đầu (UC-18). Thất bại: phòng vẫn ở sảnh chờ.
- Luồng chính:
  1. Chủ phòng bấm "Bắt đầu".
  2. Hệ thống kiểm tra phòng có ít nhất 2 người, tất cả đã sẵn sàng và bộ nhạc đủ bài hợp lệ cho số vòng.
  3. Hệ thống chọn các bài của trận, không lặp.
  4. Hệ thống chuyển phòng sang đang chơi; người vào phòng từ lúc này chờ ở sảnh.
  5. Hệ thống bắt đầu vòng 1 (UC-18).
- Luồng phụ và ngoại lệ:
  - 2a. Chưa đủ 2 người hoặc còn người chưa sẵn sàng: hệ thống từ chối và báo rõ lý do.
  - 2b. Bộ nhạc không đủ bài hợp lệ cho số vòng: hệ thống không cho bắt đầu và báo còn thiếu bao nhiêu bài; chủ phòng đổi bộ nhạc hoặc giảm số vòng.
  - 1a. Người bấm không phải chủ phòng: hệ thống từ chối.
  - Ghi chú: phòng ghép trận không có UC này; trận bắt đầu theo đếm ngược (UC-15).
- Quy tắc nghiệp vụ: BR-09, BR-12, BR-15.
- Yêu cầu liên quan: FR-09, NFR-08.

### UC-18: Chơi trận nhiều người
- Tác nhân chính / phụ: Khách (người chơi trong phòng, gồm Người chơi đã đăng nhập) / Hệ thống API nhạc.
- Tiền điều kiện: Trận đã bắt đầu từ UC-17 (phòng tự tạo) hoặc UC-15 (phòng ghép trận).
- Hậu điều kiện (thành công / thất bại): Thành công: trận kết thúc, người thắng được công bố, trận được lưu vào lịch sử và thống kê của từng người (kể cả phòng tự tạo); phòng về sảnh. Thất bại: trận kết thúc sớm tính theo điểm hiện tại (mục 2c).
- Luồng chính:
  1. Hệ thống bắt đầu vòng và chạy đồng hồ 45 giây theo giờ server.
  2. Mỗi người chơi đoán bài hát (UC-08, include) và tự mở mức nghe của mình; chỉ đồng hồ là chung.
  3. Khi có người đoán đúng, cả phòng thấy "<tên> đúng ở mức 5s" nhưng không thấy đáp án; bảng điểm cập nhật trực tiếp.
  4. Vòng kết thúc khi hết 45 giây, hoặc sớm hơn khi mọi người đã đoán đúng hoặc hết lượt.
  5. Hệ thống hiển thị đáp án, điểm từng người, bảng điểm, nguồn nhạc và link nghe đầy đủ trên nền tảng gốc.
  6. Lặp bước 1–5 đến hết số vòng.
  7. Hệ thống công bố người thắng: tổng điểm cao nhất; hòa thì ai có tổng thời gian trả lời đúng ít hơn (mili giây, theo đồng hồ server).
  8. Hệ thống lưu trận vào lịch sử và đưa phòng về sảnh để chơi tiếp (UC-20). Trận xếp hạng của người đã đăng nhập vào bảng xếp hạng (UC-24).
- Luồng phụ và ngoại lệ:
  - 2a. Người chơi mất kết nối: hệ thống giữ chỗ 30 giây và trận không dừng. Vào lại kịp thì chơi tiếp ở vòng hiện tại với mức nghe đã mở; vòng bị lỡ được 0 điểm.
  - 2b. Quá 30 giây không vào lại: coi như rời phòng, điểm đã có vẫn giữ.
  - 2c. Phòng chỉ còn 1 người: trận kết thúc sớm và tính theo điểm hiện tại.
  - 2d. Đoạn nhạc lỗi (do server xác nhận): vòng được thay bằng bài khác, không ai bị trừ điểm.
  - 2e. Lượt đoán đến sát lúc hết giờ: phân xử theo lúc server nhận so với mốc 45.000 ms, không theo thứ tự xử lý trong hàng đợi phòng.
  - 3a. Trong vòng, người chơi nhắn tin (UC-21): tin chứa tên bài đáp án bị chặn.
- Quy tắc nghiệp vụ: BR-01 → BR-10, BR-14, BR-16, BR-23, BR-27, BR-29.
- Yêu cầu liên quan: FR-09, FR-13, NFR-02, NFR-07, NFR-08.

### UC-21: Nhắn tin trong phòng
- Tác nhân chính / phụ: Khách (người chơi trong phòng, gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Người chơi đang ở trong một phòng (sảnh chờ hoặc trong trận).
- Hậu điều kiện (thành công / thất bại): Thành công: tin nhắn hiện cho mọi người trong phòng. Thất bại: tin không được gửi; người gửi thấy lý do.
- Luồng chính:
  1. Người chơi gõ tin nhắn và gửi.
  2. Hệ thống kiểm tra tin dài tối đa 200 ký tự và người gửi không vượt 1 tin mỗi giây.
  3. Hệ thống lọc từ cấm theo danh sách do quản trị viên quản lý.
  4. Nếu đang trong vòng, hệ thống kiểm tra tin có chứa tên bài đáp án không (chuẩn hóa như BR-05).
  5. Hệ thống phát tin cho mọi người trong phòng.
- Luồng phụ và ngoại lệ:
  - 2a. Tin quá dài hoặc gửi quá nhanh: hệ thống từ chối và báo cho người gửi.
  - 3a. Tin chứa từ cấm: hệ thống lọc. `[HỎI THÁI]` chặn tin hay che từ (mục 5 của `00-tong-quan.md`, điểm 6).
  - 4a. Tin chứa tên bài đáp án: hệ thống chặn, chỉ người gửi thấy cảnh báo. Hết vòng thì nói tự do.
  - 5a. Người nhận thấy tin vi phạm: báo cáo tin (UC-22, extend).
- Quy tắc nghiệp vụ: BR-05, BR-16, BR-17.
- Yêu cầu liên quan: FR-11, NFR-07.
