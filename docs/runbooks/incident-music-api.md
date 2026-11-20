# API nhạc Deezer lỗi

Mục đích: xác định lỗi provider mà không lộ đáp án hoặc đổi luật chơi.
Điều kiện: Thái xem Sentry/release và health, có tài khoản thử; Deezer là nguồn chính theo ADR-003.

1. Ghi UTC, số vòng lỗi, status upstream nếu có; không ghi track ID/tên bài, URL ký hoặc audio token.
   Kiểm `/actuator/health` để loại lỗi backend/DB; health UP không chứng minh provider hoạt động.
2. Đối chiếu Sentry class/method với logger `DeezerAudioSourceResolver`/`AudioStreamService`;
   thử vòng mới bằng tài khoản vận hành. Kiểm DNS/network outbound EC2 và tín hiệu 429/5xx/timeout.
   Không in response metadata Deezer vào log; không lặp thử dồn dập khi rate limited.
3. BR-27: chỉ server xác nhận audio lỗi rồi thay vòng bằng bài khác, không ai bị trừ điểm; client báo lỗi không đủ căn cứ.
   Không tự sửa điểm/đồng hồ, tự đổi provider hoặc reuse URL preview đã hết hạn.
   Nếu hành vi thay bài chưa có/không đúng, ghi ticket lỗi để Thái tạo; không tuyên bố đã khôi phục chỉ vì health UP.
4. Khi provider phục hồi, chơi vòng thử từ mức 2 giây tới kết thúc, xác nhận audio/điểm và link nguồn sau vòng;
   theo dõi lỗi 10 phút. Lỗi do release mới thì rollback theo [backend](incident-backend.md).

Quay lại: giữ Deezer; iTunes tắt mặc định. Jamendo cần ticket xác minh quyền/adapter riêng,
không bật chuyển nguồn tự động (ADR-003, LEG-02 và ngoại lệ PO ngày 28/10/2026).
Chi phí: không mua API hay bật nguồn trả phí trong xử lý sự cố.
Biên bản: UTC, release, loại lỗi, phạm vi ảnh hưởng và test sau phục hồi, không dữ liệu bài hát/token.
Nguồn nghiệp vụ: [BR-27](../srs/02-business-rules.md), [ADR-003](../architecture/adr/ADR-003-music-provider.md).
