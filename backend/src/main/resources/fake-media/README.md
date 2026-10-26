# Fake Media Samples

Thư mục này chứa các file âm thanh mẫu định dạng MP3 (`sample1.mp3`, `sample2.mp3`, `sample3.mp3`) phục vụ cho việc kiểm thử tự động, profile `test` và `e2e`.

## 1. Nguồn gốc & Bản quyền
Các file MP3 trong thư mục này được tạo tự động bằng `ffmpeg` với tín hiệu âm thanh tổng hợp hình sin (sine wave), không sử dụng bất kỳ bản thu thương mại hay tác quyền của bên thứ ba nào:
- `sample1.mp3`: Tone 440 Hz (Note A4), dài 5,00 giây, mono 128 kbps (CBR).
  ```bash
  ffmpeg -f lavfi -i "sine=frequency=440:duration=5" -b:a 128k sample1.mp3
  ```
- `sample2.mp3`: Tone 554 Hz (Note C#5), dài 5,00 giây, mono 128 kbps (CBR).
  ```bash
  ffmpeg -f lavfi -i "sine=frequency=554:duration=5" -b:a 128k sample2.mp3
  ```
- `sample3.mp3`: Tone 659 Hz (Note E5), dài 5,00 giây, mono 128 kbps (CBR).
  ```bash
  ffmpeg -f lavfi -i "sine=frequency=659:duration=5" -b:a 128k sample3.mp3
  ```
Tất cả file do dự án Meloo tự sinh, thuộc giấy phép nội bộ của đồ án môn học, không có ràng buộc bản quyền bên ngoài.

## 2. Quy ước kỹ thuật
- **`previewUrl`**: Giá trị `/fake-media/sample{1,2,3}.mp3` trong `FakeMusicProvider` trỏ tới đường dẫn classpath resource `classpath:fake-media/`. Khi triển khai module phát đoạn nhạc `audio` (BE-AUD-01 / MEL-44 theo ADR-008), backend đọc file trực tiếp từ classpath thay vì gửi HTTP request ra ngoài mạng.
- **Thời lượng (`durationSeconds`)**: Metadata của bài hát trong `FakeMusicProvider` khai báo thời lượng danh nghĩa từ 185s đến 265s để mô phỏng bài hát đầy đủ cho domain model. File audio mẫu chỉ có độ dài 5 giây để tối ưu kích thước repository và tốc độ kiểm thử.
- **`coverUrl`**: Giá trị `https://fake-media.meloo.internal/covers/...` là URL giả định cố định để kiểm tra trường dữ liệu không rỗng/rỗng. Host này không phân giải được trên Internet và không được kỳ vọng tải ảnh thực tế trong các kịch bản kiểm thử giao diện E2E.
