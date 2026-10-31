# Thiết kế Sơ đồ hoạt động (Activity Diagram) - DOC-03 (MEL-51)

Thư mục này chứa sơ đồ hoạt động mô tả chi tiết luồng xử lý của một vòng chơi trong hệ thống Meloo, đối chiếu tại commit `c0f723e`.

## Danh mục sơ đồ
1. **`act-luong-mot-vong-choi.puml` / `act-luong-mot-vong-choi.png`**: Luồng một vòng chơi Meloo
   - **Giai đoạn chuẩn bị**:
     - Chọn bài hát từ bộ đề, kiểm tra preview hợp lệ (BR-27); nếu lỗi tự động thay bài mới mà không phạt người chơi.
     - Sinh token audio mức 1 (2 giây) và nạp danh sách gợi ý tìm kiếm.
     - Kích hoạt đồng hồ: Nếu chơi nhiều người, kích hoạt đếm ngược 45 giây của server (BR-07); chơi đơn không giới hạn giờ.
   - **Vòng lặp tương tác của người chơi**:
     - **Nghe lại**: Phát lại đoạn audio mức hiện tại (không giới hạn, không mất điểm, BR-03).
     - **Chat**: Kiểm tra tin nhắn, chặn tin chứa từ cấm (BR-17) hoặc chứa tên bài đáp án (BR-16).
     - **Nghe thêm**: Mở mức kế tiếp 2s → 5s → 10s → 20s → 30s. Tại mức 30s, nút chuyển thành "Bỏ qua" (BR-04).
     - **Bỏ qua**: Ghi nhận kết quả `GAVE_UP`, 0 điểm (BR-04).
     - **Đoán bài**:
       - Kiểm tra thời gian: Nếu server nhận lệnh khi đã ≥ 45.000 ms thì từ chối (kết quả `TIMEOUT`, 0 điểm, BR-07).
       - Chuẩn hóa tên bài (BR-05) và so khớp.
       - **Nếu ĐÚNG**: Tính điểm cơ bản (1.000/800/600/400/200, BR-02), xét chuỗi combo (BR-08), tính thưởng tốc độ nếu là nhiều người (BR-07), phát thông báo giấu đáp án ra phòng.
       - **Nếu SAI**: Gợi ý nếu đúng ca sĩ chính (BR-06); tự động mở mức nghe tiếp theo nếu còn lượt; nếu đang ở mức 30s thì tính là `WRONG_OUT` (0 điểm).
   - **Giai đoạn kết thúc vòng**:
     - Đồng bộ kết thúc khi mọi người hoàn thành hoặc hết 45 giây.
     - Dừng phát nhạc, công bố bài hát đáp án, ảnh bìa, nguồn Deezer và link nghe đầy đủ (BR-29).
     - Cập nhật điểm số vào bảng tổng sắp và chuỗi combo của trận đấu.

## Hướng dẫn giải thích khi bảo vệ đồ án
- **Nếu người chơi mất kết nối mạng rồi gửi lệnh đoán ngay lúc 45 giây thì xử lý ra sao?**
  *Trả lời:* Theo BR-07, server phân xử dựa trên mốc thời gian server nhận được request so với mốc 45.000 ms, không phụ thuộc vào đồng hồ client hay độ trễ mạng. Mọi lượt đoán server nhận từ mốc 45.000 ms trở đi đều bị từ chối và ghi nhận hết giờ.
- **Tại sao đoán sai lại tự động mở mức nghe tiếp theo thay vì cho đoán lại ở cùng mức?**
  *Trả lời:* Theo BR-04, nếu cho đoán lại ở cùng mức mà không mất gì, người chơi sẽ đoán bừa liên tục để thử vận may. Cơ chế Heardle trừ lượt và mở đoạn nhạc dài hơn vừa giúp người chơi có thêm dữ kiện âm nhạc vừa hạn chế đoán mò.
