# Đặc tả use case: Xếp hạng và quản trị

> Ticket: MEL-29 (DOC-02) · Tổng quan, tác nhân, danh mục và truy vết: `00-tong-quan.md` · Sơ đồ: `docs/uml/usecase/uc-04-xep-hang.png`, `docs/uml/usecase/uc-05-quan-tri.png`

### UC-24: Xem bảng xếp hạng
- Tác nhân chính / phụ: Khách (gồm Người chơi đã đăng nhập) / không có.
- Tiền điều kiện: Không có. `[HỎI THÁI]` đang suy ra khách xem được bảng xếp hạng (mục 5 của `00-tong-quan.md`, điểm 2).
- Hậu điều kiện (thành công / thất bại): Thành công: người chơi thấy bảng đã chọn. Thất bại: người chơi thấy thông báo lỗi tải bảng và thử lại được.
- Luồng chính:
  1. Người chơi mở "Xếp hạng".
  2. Người chơi chọn bảng: tuần, toàn thời gian hoặc theo ngày.
  3. Hệ thống hiển thị danh sách xếp theo quy tắc của bảng và vị trí của người chơi nếu có.
- Luồng phụ và ngoại lệ:
  - 3a. Bảng tuần và toàn thời gian xếp theo tổng điểm các trận xếp hạng; bằng điểm thì ai đạt mức điểm đó trước xếp trên.
  - 3b. Bảng tuần reset lúc 00:00 thứ Hai giờ Việt Nam.
  - 3c. Bảng theo ngày là bảng thử thách ngày: xếp theo mức đoán đúng (2 giây cao nhất), rồi theo thời gian; không đoán ra thì xếp cuối.
  - 3d. Người chơi là khách: không có vị trí trên bảng; hệ thống gợi ý đăng nhập để điểm được tính. Điểm của khách không vào bảng.
  - 3e. Bảng chưa có ai: hệ thống hiển thị bảng trống.
  - Ghi chú: chỉ trận xếp hạng (chơi đơn bộ chuẩn, thử thách ngày, ghép trận nhanh) được tính; phòng tự tạo thì không.
- Quy tắc nghiệp vụ: BR-20, BR-21, BR-23, BR-24, BR-25.
- Yêu cầu liên quan: FR-14.

### UC-28: Quản lý kho nhạc và bộ đề
- Tác nhân chính / phụ: Quản trị viên / Hệ thống API nhạc.
- Tiền điều kiện: Người dùng đăng nhập với vai trò Quản trị viên.
- Hậu điều kiện (thành công / thất bại): Thành công: kho nhạc hoặc bộ đề được cập nhật và thao tác ghi vào nhật ký. Thất bại: dữ liệu giữ nguyên.
- Luồng chính:
  1. Quản trị viên mở "Kho nhạc".
  2. Hệ thống hiển thị danh sách bài, lọc được theo thể loại, quốc gia, ca sĩ, thập niên và trạng thái.
  3. Quản trị viên chọn một thao tác: nhập bài từ API nhạc (UC-27, extend), vô hiệu hóa bài, hoặc tạo/sửa bộ đề.
  4. Hệ thống lưu thay đổi và ghi nhật ký thao tác.
- Luồng phụ và ngoại lệ:
  - 3a. Nhập bài (UC-27): hệ thống gọi Hệ thống API nhạc nhập bài và phân loại theo thể loại, quốc gia, ca sĩ, thập niên; bài lỗi preview bị vô hiệu hóa ngay khi nhập. API nhạc lỗi hoặc hạn chế: hệ thống báo lỗi, không đổi kho nhạc.
  - 3b. Vô hiệu hóa bài lỗi preview hoặc không phù hợp: bài không xuất hiện ở trận mới.
  - 3c. Bộ đề sau khi sửa không đủ bài hợp lệ cho các số vòng 5, 10, 15: hệ thống cảnh báo còn thiếu bao nhiêu bài.
  - 1a. Người dùng không phải Quản trị viên: hệ thống từ chối.
  - Ghi chú: bài không có trên nguồn nhạc chính bị loại khỏi bộ đề, không thay bằng bản cover.
- Quy tắc nghiệp vụ: BR-09, BR-27, BR-28.
- Yêu cầu liên quan: FR-05, FR-16.

### UC-30: Xử lý báo cáo vi phạm
- Tác nhân chính / phụ: Quản trị viên / không có.
- Tiền điều kiện: Người dùng là Quản trị viên và có báo cáo chưa xử lý (từ UC-22).
- Hậu điều kiện (thành công / thất bại): Thành công: báo cáo chuyển sang đã xử lý, thao tác ghi vào nhật ký. Thất bại: báo cáo vẫn chưa xử lý.
- Luồng chính:
  1. Quản trị viên mở danh sách báo cáo chưa xử lý.
  2. Hệ thống hiển thị tin bị báo (nội dung, người gửi, người báo cáo, thời điểm).
  3. Quản trị viên quyết định: bỏ qua báo cáo, hoặc xử lý.
  4. Hệ thống đánh dấu báo cáo đã xử lý và ghi nhật ký.
- Luồng phụ và ngoại lệ:
  - 3a. Xử lý bằng khóa tài khoản người gửi (UC-29, extend): tài khoản đó không đăng nhập, không tạo hay vào phòng được. `[HỎI THÁI]` có mức cảnh cáo trước khi khóa không (mục 5 của `00-tong-quan.md`, điểm 7).
  - 3b. Xử lý bằng thêm từ vào danh sách từ cấm (UC-31, extend).
  - 3c. Người gửi tin bị báo là khách (không có tài khoản để khóa): `[HỎI THÁI]` xử lý thế nào (mục 5 của `00-tong-quan.md`, điểm 8).
  - 1a. Người dùng không phải Quản trị viên: hệ thống từ chối.
- Quy tắc nghiệp vụ: BR-17, BR-20, BR-28.
- Yêu cầu liên quan: FR-17.
