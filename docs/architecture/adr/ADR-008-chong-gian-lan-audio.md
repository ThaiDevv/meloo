# ADR-008: Chống gian lận audio
- Trạng thái: Đề xuất (chốt sau SPK-01, MEL-25)
- Ngày: 2026-10-21 · Ticket: MEL-26

> `[CHỜ SPK-01]` ADR này mới ghi hướng đã thống nhất và các điểm phụ thuộc kết quả spike. Khi SPK-01 merge (cùng ADR-003 về nguồn nhạc), cập nhật mục "Quyết định" và đổi trạng thái thành Chấp nhận. Chưa có số liệu đo nào được dùng ở đây.

## Bối cảnh
Cơ chế chơi là mở dần đoạn preview 2 → 5 → 10 → 20 → 30 giây, đoán càng sớm càng nhiều điểm. Người chơi gian lận bằng cách lấy đáp án hoặc nghe nhiều hơn mức được phép: đọc tên bài trong URL hay metadata, tải trước cả đoạn 30 giây rồi tua, dùng lại link của vòng khác, hoặc chia sẻ link cho người ngoài phòng. Nguyên tắc không phá: server là trọng tài, đoạn nhạc chỉ cấp qua token mờ, URL không chứa id hay tên bài.

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Trả thẳng URL preview của nhà cung cấp cho client | Không tốn băng thông backend | Lộ id/tên bài trong URL; không kiểm được mức nghe; không thu hồi được |
| Cấp URL cố định theo bài nhưng cắt sẵn đoạn | Đơn giản | Link dùng lại được, đoán được cấu trúc, chia sẻ ngoài phòng |
| **Backend làm trung gian: token mờ HMAC theo (vòng, người chơi, mức), hết hạn ngắn** | Kiểm mức nghe và người dùng ở server; URL không lộ gì; hết hạn nhanh | Backend phải phát hoặc proxy audio, tốn băng thông và RAM; phụ thuộc cách lấy nguồn nhạc |

## Quyết định (hướng đề xuất)
- Audio token: HMAC-SHA256 trên (`roundId`, `playerId`, `level`, `expiresAt`), so sánh bằng `MessageDigest.isEqual`, hết hạn ≤ 2 phút. URL chỉ chứa token mờ, không có id hay tên bài.
- Mỗi mức nghe (2/5/10/20/30 giây) cần token riêng; server chỉ cấp token cho mức người chơi đã mở trong vòng đó và chỉ trả đúng độ dài tương ứng.
- Đáp án (tên bài, ca sĩ, trackId, URL gốc) không xuất hiện ở bất kỳ API, sự kiện hay log nào trước `ROUND_END`.
- Cách phát đoạn (backend cắt sẵn rồi phát, hay chuyển tiếp từ nguồn) phụ thuộc nguồn nhạc: `[CHỜ SPK-01]` (xem ADR-003).
- Giới hạn tần suất cấp token theo người chơi bằng Bucket4j.

## Hệ quả
- Tốt: không thể suy ra đáp án từ URL; link của mức cao không dùng được ở mức thấp, vòng khác hay sau khi hết hạn; kiểm quyền ở một chỗ.
- Xấu hoặc rủi ro: không chống được ghi âm hoặc nhận diện bằng ứng dụng bên ngoài (nhận diện nhạc qua loa): chấp nhận, thuộc giới hạn của mọi game đoán nhạc, ghi rõ trong tài liệu bảo mật. Chi phí băng thông và RAM trên EC2 1 GB `[CẦN SỐ LIỆU: đo ở SPK-01]`. Đồng hồ lệch có thể làm token hết hạn sớm: giảm bằng `serverTime` và thời hạn đủ rộng. Người chơi có thể mở thêm mức bằng cách đoán nhanh: điểm tính theo mức đã mở, do server quyết.

## Câu hỏi bảo vệ
1. Vì sao không dùng thẳng URL preview? — URL lộ id/tên bài, không kiểm được mức nghe và không thu hồi được.
2. Token chống gian lận bằng cách nào? — Gắn với (vòng, người chơi, mức) và hết hạn ngắn, ký HMAC nên không giả được.
3. Còn cách gian lận nào chưa chặn? — Nhận diện nhạc bằng thiết bị bên ngoài; không chặn được ở phía server.
4. Vì sao ADR này chưa chốt? — Cách phát đoạn nhạc phụ thuộc kết quả đo của SPK-01 về nguồn nhạc.
