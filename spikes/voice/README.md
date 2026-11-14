# MEL-80 — thử nghiệm voice mesh

Prototype độc lập, không dùng backend hay giao thức production: relay triển khai tập con STOMP 1.2 qua WebSocket và gửi offer/answer/ICE giữa các tab cùng phòng. Không có xác thực, mã phòng bí mật, giới hạn số người hay kiểm tra phòng riêng; chỉ chạy trong mạng thử nghiệm tin cậy, không đưa lên Internet công khai. Không đưa thông tin thật, nhạc có bản quyền hoặc credential lâu dài vào log/repo. Backend MEL-46 chưa triển khai signaling voice; prototype không chứng minh tích hợp production.

## Chạy

Cần Node 22; `cd spikes/voice && npm test && npm start`, mở `http://localhost:8088` bằng 3–5 browser/profile, cùng mã phòng. Chấp nhận quyền mic, bấm **Tham gia**, sau đó bật mic từng người và nghe ở các máy khác (dùng tai nghe để tránh feedback). Mic mặc định tắt. Các browser cùng máy chỉ là smoke test, không thay thử mạng. HTTPS là bắt buộc với `getUserMedia` ngoài localhost: để thử nhiều thiết bị, người vận hành phải bố trí reverse proxy HTTPS/WSS có kiểm soát truy cập đến `HOST=0.0.0.0 npm start` trong mạng thử nghiệm, tránh phơi relay công khai. Cấp quyền mic và xác nhận autoplay trên từng trình duyệt. Dừng server bằng Ctrl-C.

Để dùng TURN, nhập ICE servers JSON **trên từng máy** trước khi tham gia, ví dụ `[{"urls":"stun:stun.l.google.com:19302"},{"urls":"turn:HOST:3478?transport=udp","username":"TEMP_USER","credential":"TEMP_PASSWORD"}]`. Dùng credential tạm do người vận hành cấp; không lưu trong repo hoặc ảnh/log. Để trống dùng STUN-only. Nút **Thống kê ICE** hiển thị loại candidate và bộ đếm byte trên candidate pair đã chọn; không lưu IP, SDP hay credential. Các byte là tổng theo pair, không phải riêng âm thanh; tính chênh lệch hai mẫu chia thời gian, nhân 8 để ra bit/s. Các giá trị hiển thị không chứng minh chi phí relay hay kết nối 4G nếu không đo thực tế.

## Kịch bản BR-19

Trong phòng ít nhất hai người, bật mic A, mở file âm thanh thử không có bản quyền trên A, phát qua loa A trong khi B nghe. Xác nhận B nghe giọng trước khi phát, A hiện “Mic tạm tắt khi phát nhạc”, B không nghe nhạc/giọng từ mic A trong lúc phát, và giọng trở lại sau khi pause/kết thúc. Lặp khi mic A đã tắt trước đó: sau pause mic vẫn tắt. Lặp khi tắt mic thủ công trong lúc nhạc phát: sau pause vẫn tắt. Thử nhiều lần play/pause và thay file. Ghi thiết bị, browser, phiên bản, kết quả và thời điểm; `track.enabled=false` là mute track, không thu hồi quyền microphone. Đây là kiểm tra prototype, không chứng minh behavior của game production.

## Kịch bản đo STUN/TURN

1. Chuẩn bị 3–5 thiết bị/browser, ít nhất một kết nối 4G độc lập, TURN endpoint có credential tạm, HTTPS/WSS và đồng hồ. Ghi nhà mạng, loại NAT nếu biết, browser/phiên bản, địa điểm và ngày; không ghi địa chỉ IP hay credential.
2. Cùng cấu hình thiết bị và mạng, chạy STUN-only rồi STUN+TURN, ít nhất 10 lượt độc lập mỗi cấu hình; đóng tất cả tab giữa lượt và thay thứ tự để giảm lệch. Kết nối từng người, nói trong 60 giây. Ghi số cặp thực sự `connected` trên tổng n(n−1)/2, thời gian kết nối, chất lượng nghe và lỗi; một lượt thành công nếu mọi cặp kết nối và nghe hai chiều. Thống kê thành công theo lượt và theo cặp, mẫu nhỏ không đại diện dân số.
3. Chụp số byte pair ở đầu/cuối thời gian đo cho từng đầu; tính bitrate gửi/nhận của từng người. Ghi loại candidate; STUN+TURN không đảm bảo relay được chọn. Báo riêng số cặp có candidate `relay` và xác minh log/metric TURN. Không cộng byte hai đầu thành lưu lượng TURN. Kiểm tra quota/giá hiện hành của nhà cung cấp và đo egress server thực tế; ước tính chi phí = lưu lượng tính phí × đơn giá + phí máy/dịch vụ, ghi nguồn/ngày và giả định. Không suy ra giá từ byte pair.
4. Điền bảng bên dưới và lưu bằng chứng đã loại IP/credential. Nếu thiếu thiết bị, endpoint hoặc số đo thì ghi **chưa kiểm**, không suy diễn.

| Ngày / browser / mạng / số người | Cấu hình | Lượt thành công / tổng | Cặp thành công / tổng | Relay / tổng cặp | bitrate gửi/nhận | chi phí và nguồn | Ghi chú |
|---|---|---|---|---|---|---|---|
| Chưa đo | STUN-only | — | — | — | — | — | Cần thiết bị 4G |
| Chưa đo | STUN+TURN | — | — | — | — | — | Cần endpoint TURN |
