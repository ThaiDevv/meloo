# Thiết kế Sơ đồ máy trạng thái (State Machine Diagrams) - DOC-03 (MEL-51)

Thư mục này chứa các sơ đồ máy trạng thái mô tả vòng đời và các chuyển dịch trạng thái của Phòng, Vòng chơi và Người chơi trong phòng, đối chiếu tại commit `c0f723e`.

## Danh mục sơ đồ
1. **`state-phong.puml` / `state-phong.png`**: Vòng đời Phòng chơi (Room State Machine - BR-11..15)
   - **`WAITING_LOBBY` (Sảnh chờ)**: Nơi người chơi vào/ra, chỉnh cài đặt phòng. Tự động đóng nếu phòng trống quá 10 phút (BR-15).
   - **`COUNTDOWN` (Đang đếm ngược)**: Kích hoạt khi chủ phòng bấm Bắt đầu (≥ 2 người, tất cả sẵn sàng - BR-12) hoặc khi ghép trận có người thứ 2 (đếm ngược 30s - BR-13). Nếu số người giảm xuống < 2, đếm ngược dừng lại (BR-13).
   - **`IN_MATCH` (Trận đấu đang diễn ra)**: Điều phối các trạng thái con của từng vòng đấu (`ROUND_PREPARING` → `ROUND_PLAYING` → `ROUND_REVEAL` → `INTERMISSION`). Nếu phòng chỉ còn 1 người kết nối > 30s, trận đấu kết thúc sớm (BR-14).
   - **`MATCH_FINISHED` (Trận kết thúc)**: Tổng kết điểm số, xếp hạng và lưu kết quả. Cả phòng có thể chọn quay về sảnh để chơi tiếp, giữ nguyên mã phòng (BR-12).
   - **`CLOSED` (Phòng đóng)**: Hủy `RoomActor` và dọn dẹp tài nguyên.

2. **`state-vong.puml` / `state-vong.png`**: Vòng chơi (Round State Machine - BE-GAME-01, BR-01..07)
   - **`WAITING` (CHỜ)**: Khởi tạo dữ liệu vòng, nạp bài hát mục tiêu và cấp token 2s.
   - **`IN_PROGRESS` (ĐANG CHƠI)**: Điều phối 5 mức nghe (2s, 5s, 10s, 20s, 30s).
   - **Các trạng thái kết thúc lượt của người chơi**:
     - `CORRECT` (ĐÚNG): Đoán đúng tên bài sau chuẩn hóa.
     - `WRONG_OUT` (HẾT LƯỢT): Đoán sai tại mức 30 giây hoặc hết lượt (0 điểm).
     - `GAVE_UP` (BỎ QUA): Người chơi chủ động bấm bỏ qua tại mức 30 giây (0 điểm, BR-04).
     - `TIMEOUT` (HẾT GIỜ): Hết 45 giây server trong chế độ nhiều người (0 điểm, BR-07).
   - **`ENDED` (KẾT THÚC)**: Công bố bài hát đáp án, link nghe Deezer (BR-29), cộng dồn điểm tích lũy.

3. **`state-nguoi-choi-trong-phong.puml` / `state-nguoi-choi-trong-phong.png`**: Vòng đời Người chơi trong phòng (Player Lifecycle - BR-12, BR-14, BR-15)
   - **`LOBBY` (Sảnh chờ)**: Trạng thái `NOT_READY` ↔ `READY`. Bị chủ phòng mời ra chuyển sang `KICKED` (không vào lại được, BR-12).
   - **`IN_MATCH` (Trong trận)**: Trạng thái `PLAYING` (đang nghe/đoán) → `FINISHED_ROUND` (đã xong lượt, chờ người khác hoàn thành, BR-07).
   - **Xử lý mất kết nối (Disconnection Handling - BR-14)**:
     - Khi rớt mạng: Chuyển sang `DISCONNECTED_GRACE`. Server giữ chỗ trong 30 giây, trận đấu KHÔNG dừng lại chờ.
     - Nếu kết nối lại trong 30s: Tiếp tục chơi vòng hiện tại; các vòng bị lỡ được 0 điểm.
     - Quá 30s không kết nối lại: Chuyển sang `FORFEITED` (coi như rời trận, điểm các vòng đã chơi vẫn được bảo lưu).

## Hướng dẫn giải thích khi bảo vệ đồ án
- **Nếu người chơi bị rớt mạng 10 giây rồi quay lại thì hệ thống xử lý thế nào?**
  *Trả lời:* Theo BR-14, hệ thống có vùng đệm 30 giây giữ chỗ (`DISCONNECTED_GRACE`). Người chơi quay lại trong 30 giây sẽ tái kết nối WebSocket và tiếp tục chơi tiếp ở vòng hiện tại với mức nghe đã mở trước đó; điểm số trước đó không bị mất và trận đấu của những người khác không bị gián đoạn.
- **Tại sao sau khi kết thúc trận lại quay về sảnh và giữ nguyên mã phòng?**
  *Trả lời:* Theo BR-12, tính năng "Chơi lại giữ mã phòng" giúp nhóm bạn có thể tiếp tục chơi nhiều ván liên tục mà không cần chủ phòng phải tạo phòng mới và gửi lại link mời, giúp giữ chân người chơi lâu hơn.
