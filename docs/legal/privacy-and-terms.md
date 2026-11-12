# Chính sách quyền riêng tư, điều khoản và xóa dữ liệu (LEG-01)

- Ticket: MEL-77 · Tác giả: Codex · Reviewer: Antigravity.
- Ngày soạn: 12/11/2026 · Trạng thái: chờ Thái duyệt nội dung qua PR.
- Nội dung công khai song ngữ nằm tại `frontend/src/locales/{vi,en}/legal.json`, dùng chung với ba trang trong `frontend/src/features/legal/`.

## URL công khai

Dùng origin HTTPS của frontend đã triển khai, không dùng origin API hoặc preview có bảo vệ đăng nhập:

| Trang | Path | Khai báo |
|---|---|---|
| Chính sách quyền riêng tư | `/legal/privacy` | Privacy Policy URL của Google và Facebook |
| Điều khoản sử dụng | `/legal/terms` | Terms of Service URL |
| Hướng dẫn xóa dữ liệu | `/legal/data-deletion` | Data Deletion Instructions URL của Facebook |

Đây là trang SPA công khai, không yêu cầu phiên. Footer hiện có dẫn tới quyền riêng tư và điều khoản; navigation trên mỗi trang dẫn tới cả ba trang. Không dùng URL hướng dẫn thay cho callback xóa dữ liệu của Facebook trong hợp đồng API.

Chưa xác minh domain production và URL đã triển khai trong ticket này. Khi triển khai, mở trực tiếp cả ba URL bằng cửa sổ không đăng nhập, tải lại trang, đổi vi/en rồi mới khai báo trong Console; kiểm tra HTTPS và fallback SPA trên host. Bản preview Vercel cần được Thái nghiệm thu.

## Cơ sở nội dung và dữ liệu

- BR-20 trong [quy tắc nghiệp vụ](../srs/02-business-rules.md): xóa dữ liệu khách sau 30 ngày không hoạt động. Quyết định mới hơn mô tả Jira: nếu cắt BE-ID-03 thì chuyển job sang ticket Must, giữ cam kết chính sách.
- Đối chiếu `AppUser`, `AuthIdentity`, phiên xác thực và các module tiến bộ, daily, chat, kiểm duyệt: dữ liệu nhận dạng, email nếu được cung cấp, hoạt động, lịch sử, thống kê, huy hiệu, tin nhắn và báo cáo; không nhận mật khẩu Google.
- Đoạn nghe thử chuyển tiếp qua proxy; không lưu file nhạc trên máy chủ. Xem [LEG-02](music-preview-licensing.md). Không suy diễn rằng nhà cung cấp đã cấp phép cho Meloo.
- Thái chốt qua coordinator ngày 12/11/2026: chủ vận hành Trần Văn Thái, email công khai `tranvanthai20062@gmail.com`, xử lý yêu cầu trong 30 ngày.
- Không tự đặt thời hạn lưu log hoặc backup. Nội dung công khai nói rõ các thời hạn riêng chưa được ấn định; cần Thái chốt trước khi hoàn thiện chính sách vận hành.

## Điều kiện trước khi beta công khai

MEL-84 (BE-ID-03) chưa triển khai job xóa khách và xóa tài khoản tại thời điểm viết. Trang công khai nêu đúng trạng thái này, cung cấp email yêu cầu thủ công; không hiển thị nút xóa giả hoặc khẳng định job đã chạy. Không xem MEL-77 là bằng chứng backend đã đáp ứng cam kết 30 ngày.

Trước beta công khai, Thái cần nghiệm thu job xóa khách và luồng xóa tài khoản/dữ liệu liên quan, xác nhận quy trình xử lý email trong 30 ngày, thời hạn log/backup và nội dung hai ngôn ngữ. Sau khi MEL-84 hoàn tất, cập nhật đoạn mô tả “chưa sẵn sàng/đang chờ triển khai” và hướng dẫn thao tác theo chức năng thật. Nếu cắt MEL-84, tạo ticket Must riêng cho job theo BR-20.

Xóa cookie, đăng xuất hoặc thu hồi quyền ứng dụng tại Google/Facebook không tự xóa dữ liệu máy chủ Meloo. Khi người dùng yêu cầu, xác minh quyền sở hữu qua email, xác nhận phạm vi và kết quả; không yêu cầu gửi mật khẩu, cookie hay token.
