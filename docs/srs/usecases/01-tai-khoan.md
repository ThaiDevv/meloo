# Đặc tả use case: Tài khoản

> Ticket: MEL-29 (DOC-02) · Tổng quan, tác nhân, danh mục và truy vết: `00-tong-quan.md` · Sơ đồ: `docs/uml/usecase/uc-01-tai-khoan.png`

### UC-01: Vào chơi bằng biệt danh
- Tác nhân chính / phụ: Khách / không có.
- Tiền điều kiện: Người dùng đã mở web Meloo, chưa đăng nhập.
- Hậu điều kiện (thành công / thất bại): Thành công: có phiên khách với biệt danh hợp lệ và chọn được mọi chế độ chơi. Thất bại: chưa có phiên, người dùng ở lại màn hình nhập biệt danh.
- Luồng chính:
  1. Khách mở Meloo.
  2. Hệ thống hiển thị ô nhập biệt danh và các chế độ chơi.
  3. Khách nhập biệt danh.
  4. Hệ thống kiểm tra biệt danh dài 3–20 ký tự và không chứa từ cấm.
  5. Hệ thống tạo phiên khách và ghi nhận biệt danh.
  6. Khách chọn một chế độ chơi (UC-07, UC-10, UC-13, UC-15…).
- Luồng phụ và ngoại lệ:
  - 2a. Khách đã có phiên khách từ lần trước và dữ liệu chưa bị xóa (khách không hoạt động 30 ngày thì bị xóa): hệ thống dùng lại phiên và biệt danh cũ, bỏ qua bước 3–5.
  - 4a. Biệt danh ngắn hơn 3 hoặc dài hơn 20 ký tự, hoặc chứa từ cấm: hệ thống báo lỗi, quay lại bước 3.
  - 6a. Khách chọn đăng nhập thay vì chơi bằng biệt danh: chuyển sang UC-02.
- Quy tắc nghiệp vụ: BR-20, BR-22.
- Yêu cầu liên quan: FR-01, NFR-10 (từ lúc mở web đến khi nghe đoạn nhạc đầu tiên ≤ 30 giây, không cần đăng ký).

### UC-02: Đăng nhập bằng tài khoản mạng xã hội
- Tác nhân chính / phụ: Khách / Nhà cung cấp OAuth (Google; Facebook là Should).
- Tiền điều kiện: Người dùng chưa đăng nhập (có thể đang là khách có dữ liệu chơi).
- Hậu điều kiện (thành công / thất bại): Thành công: người dùng đăng nhập, có hồ sơ; nếu trước đó là khách có dữ liệu thì dữ liệu được gộp vào tài khoản. Thất bại: người dùng vẫn là khách, dữ liệu khách giữ nguyên.
- Luồng chính:
  1. Khách chọn "Đăng nhập bằng Google".
  2. Hệ thống chuyển khách sang Nhà cung cấp OAuth.
  3. Khách đăng nhập và đồng ý chia sẻ thông tin cơ bản.
  4. Nhà cung cấp OAuth trả danh tính của khách về Meloo.
  5. Hệ thống tìm tài khoản theo danh tính; nếu chưa có thì tạo tài khoản và hồ sơ mới. Email nằm trong `meloo.admin.emails` được gán vai trò Quản trị viên.
  6. Nếu khách có lịch sử, thống kê, huy hiệu hoặc điểm trận xếp hạng, hệ thống gộp chúng vào tài khoản (UC-03, include); điểm các trận xếp hạng được tính vào bảng xếp hạng của kỳ tương ứng.
  7. Hệ thống đưa người dùng về màn hình trước khi đăng nhập, ở trạng thái đã đăng nhập.
- Luồng phụ và ngoại lệ:
  - 1a. Khách chọn Facebook: luồng giống trên (Should, FR-03).
  - 3a. Khách từ chối hoặc đóng cửa sổ đăng nhập: hệ thống quay về màn hình trước, người dùng vẫn là khách.
  - 4a. Nhà cung cấp OAuth lỗi hoặc không phản hồi: hệ thống báo lỗi và cho thử lại; người dùng vẫn chơi được như khách.
  - 5a. Tài khoản bị khóa: hệ thống từ chối đăng nhập và báo tài khoản đã bị khóa.
  - 6a. Khách và tài khoản cùng đã chơi thử thách ngày trong một ngày: giữ lượt của tài khoản.
- Quy tắc nghiệp vụ: BR-21, BR-28 (tài khoản bị khóa không đăng nhập được).
- Yêu cầu liên quan: FR-02, FR-03, FR-04, NFR-05.
