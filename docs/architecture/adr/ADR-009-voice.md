# ADR-009: Thử nghiệm voice mesh WebRTC

- Trạng thái: Đề xuất, chờ số đo và Thái duyệt
- Ngày: 2026-11-14 · Ticket: MEL-80

## Bối cảnh

Phòng riêng có tối đa 5 người; BR-18 quy định voice chỉ có ở phòng riêng và mic mặc định tắt. BR-19 yêu cầu tạm tắt mic khi phát nhạc của chính người chơi, khôi phục nếu trước đó mic bật và thông báo trên giao diện. Cần đánh giá khả năng kết nối qua mạng di động, băng thông và chi phí trước khi triển khai. Prototype trong `spikes/voice/` là môi trường thử độc lập, không phải triển khai bảo mật hoặc hợp đồng production.

## Các phương án

| Phương án | Ưu điểm | Giới hạn |
|---|---|---|
| WebRTC mesh, chỉ STUN | Ít hạ tầng relay | Không bảo đảm vượt mọi NAT/firewall; chưa có tỷ lệ thực nghiệm 4G |
| WebRTC mesh, STUN và TURN dự phòng | Có đường relay khi kết nối trực tiếp không thành | Cần vận hành TURN, credential ngắn hạn và chi phí băng thông; chưa có số đo |
| Hoãn voice sang Phase 2 | Tránh triển khai khi thiếu bằng chứng | Người chơi chưa có voice |

## Đề xuất

Thử mesh WebRTC với STUN và TURN dự phòng cho phòng riêng 2–5 người; signaling production qua STOMP sau khi có ticket/hợp đồng tương ứng, xác thực thành viên và kiểm tra quyền ở server. Đây là hướng thử, **chưa phải quyết định triển khai**: cần kết quả 4G, đánh giá chi phí và Thái phê duyệt. Mic mặc định tắt; trạng thái mong muốn của người dùng tách với trạng thái tạm tắt trong lúc phát nhạc, để khôi phục đúng BR-19. Không tin client như cơ chế chống gian lận tuyệt đối; xử lý các nguồn phát khác và hành vi trình duyệt cần được kiểm tra khi tích hợp.

Nếu đo không đạt, thiếu thời gian, hoặc chi phí không phù hợp, đề xuất chuyển voice sang Phase 2; ngưỡng chấp nhận và quyết định cuối do Thái đặt. Chưa có số liệu để kết luận tỷ lệ thành công hay chi phí. Protocol và bảng ghi số liệu nằm tại `spikes/voice/README.md`.

## Hệ quả và giới hạn

Mesh cần một kết nối cho mỗi cặp (tối đa 10 cặp với 5 người), tăng tải uplink theo số người. TURN có thể phát sinh chi phí; cần đo traffic relay và kiểm soát quota. Prototype relay không xác thực, không được triển khai công khai hoặc tái sử dụng như production. Chưa có bằng chứng demo 3–5 browser, kiểm tra âm thanh lọt mic thực tế, so sánh STUN/TURN qua 4G, hay chi phí; các kết quả này phải được bổ sung trước khi chấp nhận ADR.

## Câu hỏi bảo vệ

1. Vì sao mesh? Quy mô phòng nhỏ, có thể thử mà chưa cần media server; phải đo tải trước khi quyết.
2. TURN khác STUN thế nào? STUN hỗ trợ tìm đường trực tiếp, TURN chuyển tiếp lưu lượng khi cần.
3. Vì sao mic tự tắt? BR-19 nhằm hạn chế nhạc lọt sang người chơi khác khi các mức nghe khác nhau.
4. Nếu mạng di động không kết nối được? Đo TURN và chi phí; nếu không khả thi thì đề xuất Phase 2.
