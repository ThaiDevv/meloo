# ADR-008: Chống gian lận audio
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 (chốt: 2026-10-28) · Ticket: MEL-26, MEL-118

## Bối cảnh
Cơ chế chơi là mở dần đoạn preview 2 → 5 → 10 → 20 → 30 giây, đoán càng sớm càng nhiều điểm (BR-02). Người chơi gian lận bằng cách lấy đáp án hoặc nghe nhiều hơn mức được phép: đọc tên bài trong URL hay metadata, tải trước cả đoạn 30 giây rồi tua, dùng lại link của vòng khác, hoặc chia sẻ link cho người ngoài phòng. Nguyên tắc không thể phá vỡ: server là trọng tài, đoạn nhạc chỉ cấp qua token mờ, URL không chứa id hay tên bài, và đáp án không được lộ trước khi kết thúc vòng (NFR-07).

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Trả thẳng URL preview của nhà cung cấp cho client | Không tốn băng thông backend | Lộ id/tên bài trong URL; không kiểm soát được mức nghe; không thu hồi được |
| Cấp URL cố định theo bài nhưng cắt sẵn đoạn | Đơn giản | Link dùng lại được, đoán được cấu trúc, chia sẻ ngoài phòng |
| **Backend làm trung gian: streaming proxy + token mờ HMAC theo (vòng, người chơi, mức), hết hạn ngắn** | Kiểm soát mức nghe và người dùng ở server; URL không lộ metadata; hết hạn nhanh; không vi phạm lưu trữ audio | Backend phải làm trung gian proxy audio; cần quản lý băng thông và RAM trên máy chủ nhỏ |

## Quyết định
Chọn phương án **Backend làm streaming proxy với token mờ HMAC ngắn hạn**. Kiến trúc này là giải pháp cốt lõi độc lập với nhà cung cấp; việc tích hợp cụ thể dựa trên nguồn nhạc đề xuất từ SPK-01 / ADR-003:

- **Nguồn nhạc:** Deezer làm nguồn chính đề xuất có điều kiện (chuẩn MP3 128 kbps theo ADR-003, hiện đang ở trạng thái Đề xuất và chờ cổng pháp lý LEG-02/MEL-120), iTunes làm dự phòng kỹ thuật tắt mặc định (M4A/AAC 268,1 kbps). Nếu cổng pháp lý không thông qua, kiến trúc proxy token mờ này vẫn áp dụng nguyên vẹn cho nguồn audio được cấp phép riêng do Thái chọn.
- **Cách phát đoạn qua streaming proxy:** Backend đóng vai trò streaming proxy chuyển tiếp trực tiếp luồng byte audio từ URL preview có chữ ký của nhà cung cấp (`hdnea` hết hạn ~900 giây của Deezer). Backend không lưu file audio xuống đĩa cục bộ và không tải toàn bộ file vào heap RAM (nhằm hạn chế rủi ro bản quyền và tiết kiệm tài nguyên hệ thống). URL ký của nhà cung cấp được giữ tạm trong bộ nhớ tiến trình (memory cache) theo `trackId` tối đa tới khi vòng chơi kết thúc hoặc URL hết hạn, không persist vào cơ sở dữ liệu.
- **Audio token:** Mã hóa bằng HMAC-SHA256 trên bộ tứ (`roundId`, `playerId`, `level`, `expiresAt`), sử dụng khóa bí mật cấu hình trên server. Khi giải mã và xác thực token, so sánh chữ ký bằng thuật toán hằng số thời gian (`MessageDigest.isEqual`) để ngăn chặn tấn công định thời (timing attack). Thời hạn sống của token ngắn (≤ 2 phút). Endpoint phục vụ audio có dạng `/api/v1/audio/{token}`, tuyệt đối không chứa `trackId`, tên bài hát hay metadata nhận diện.
- **Khống chế độ dài mức nghe:** Mỗi mức nghe (2, 5, 10, 20, 30 giây) được cấp một token riêng. Server chỉ cấp token cho mức người chơi đã mở hợp lệ trong vòng đấu đó. Backend khống chế độ dài phát bằng cách giới hạn số byte truyền ra tính từ đầu stream, ước tính theo bitrate CBR 128 kbps (~16 KB/giây của Deezer: 2s ≈ 32 KB, 5s ≈ 80 KB, 10s ≈ 160 KB, 20s ≈ 320 KB, 30s ≈ 480 KB). Backend không hỗ trợ HTTP `Range` vượt quá phạm vi byte của mức đã mở; nếu client gửi header `Range` vượt quá giới hạn, server chỉ phục vụ trong phạm vi tối đa của mức hoặc từ chối request. Client chỉ nhận đúng thời lượng cho phép, không thể tua hay nghe trước đoạn sau.
- **Bảo mật đáp án:** Toàn bộ thông tin định danh bài hát (`trackId`, tên bài, ca sĩ, URL gốc của nhà cung cấp) không xuất hiện ở bất kỳ API response, thông điệp STOMP hay log ứng dụng nào trước thời điểm `ROUND_END`.
- **Chống lạm dụng:** Áp dụng rate limiting theo người chơi bằng Bucket4j tại endpoint cấp và phát audio token để ngăn chặn vét dữ liệu hoặc tấn công từ chối dịch vụ.

## Hệ quả
- **Tốt:**
  - Không thể trích xuất hoặc suy đoán đáp án bài hát từ URL hoặc response headers.
  - Token gắn chặt với từng người chơi, từng vòng và từng mức nghe; token của mức cao không thể dùng cho mức thấp; không thể chia sẻ link cho người ngoài phòng hoặc tái sử dụng sau khi hết hạn.
  - Dự kiến phù hợp với điều khoản cấp phép của Deezer vì backend không lưu trữ đĩa (no persistent caching) hay phân phối lại toàn bộ bản thu; phạm vi stream và proxy cần cổng pháp lý LEG-02 xác nhận.
  - Tiết kiệm 52% băng thông so với iTunes nhờ định dạng MP3 128 kbps từ Deezer.
- **Xấu hoặc rủi ro kèm giải pháp:**
  - *Nhận diện nhạc bằng ứng dụng ngoài:* Không thể chống được người chơi dùng thiết bị thứ hai (Shazam, SoundHound qua micro/loa ngoài). Đây là giới hạn vật lý không thể tránh khỏi của mọi game đoán nhạc trên trình duyệt, được chấp nhận và ghi nhận rõ trong tài liệu bảo mật.
  - *Ước tính băng thông và bộ nhớ RAM trên EC2 1 GB:*
    - **Băng thông:** Tính từ bitrate 128 kbps đo ở SPK-01 (16 KB/giây). Một người chơi nghe đủ 5 mức (phát lại từ giây 0: 2+5+10+20+30 = 67 giây) nhận tổng cộng khoảng 1,07 MB dữ liệu (so với 2,24 MB của iTunes). Nếu client chỉ nhận đoạn mới (tổng 30 giây), lưu lượng chỉ còn khoảng 0,48 MB. Một phòng 5 người nghe đủ 5 mức tốn khoảng 5,36 MB/vòng (so với 11,23 MB của iTunes). Ở mức tải mục tiêu 20 phòng đồng thời (100 người cùng stream ở mức cao điểm), lưu lượng mạng ra (egress) ước tính tối đa ~1,6 MB/s (12,8 Mbps).
    - **Bộ nhớ RAM:** Ước lượng lý thuyết theo kích thước bộ đệm của streaming proxy (buffer nhỏ ~8 KB đến 64 KB cho mỗi stream kết nối). Về mặt lý thuyết, 100 kết nối stream đồng thời chỉ cần 1–6,4 MB cho buffer truyền dữ liệu giữa input stream và output stream, giúp tránh nạp trọn vẹn tệp nhạc vào RAM heap. Tuy nhiên, con số này chưa tính chi phí kết nối 2 đầu (socket client ↔ backend và backend ↔ Deezer), TLS context, thread pool của servlet container, cũng như bộ nhớ giữ trạng thái phòng (ADR-007). Khả năng chịu tải thực tế trên máy chủ EC2 1 GB RAM (heap ~600 MB theo `-XX:MaxRAMPercentage=60`, ADR-006) chưa được đo thực nghiệm tại SPK-01 và cần được kiểm chứng thông qua kiểm thử tải k6 ở QA-03 theo mục tiêu NFR-03 (20 phòng × 5 người).
  - *Lệch đồng hồ (clock skew):* Đồng hồ trên thiết bị người dùng lệch có thể làm token hết hạn sớm hoặc muộn so với phán đoán của client. Giảm thiểu bằng cách luôn gửi kèm mốc thời gian máy chủ `serverTime` trong payload trạng thái vòng chơi và đặt thời hạn token là 2 phút. Trong phòng nhiều người, thời gian vòng tối đa là 45 giây (BR-07); trong chế độ chơi đơn không giới hạn thời gian vòng, nếu token hết hạn client chỉ cần gọi `getCurrentRound` để nhận audio token mới theo hợp đồng OpenAPI.
  - *Quyền sử dụng pháp lý:* LEG-02 (MEL-120, 2026-10-28): Deezer đã phản hồi bằng văn bản, không xác nhận cách dùng của Meloo, không nhận đăng ký ứng dụng API mới và không có đường xin duyệt. Cơ chế streaming proxy và in-memory cache URL ký trong thời gian vòng chơi (< 15 phút) là giải pháp kỹ thuật nhằm hạn chế rủi ro lưu trữ audio đĩa cục bộ, không phải quyền đã được Deezer cho phép. Đối với Public Beta (BIZ-07), Deezer chưa được cho phép; Thái quyết định vẫn dùng và tự chấp nhận rủi ro (LEG-02, Mục 6.1), Jamendo API là nguồn thay thế ưu tiên nếu cần đổi. Điều khoản Jamendo không đề cập việc stream qua proxy và cấm thiết kế ứng dụng để cache nội dung, nên thiết kế không persist audio vẫn giữ nguyên và việc proxy cắt đoạn cần được hỏi Jamendo (xem `docs/legal/music-preview-licensing.md`).

## Nguồn tham chiếu
- [LEG-02: Thẩm định quyền sử dụng preview nhạc cho Meloo](../../legal/music-preview-licensing.md), 2026-10-28.
- [ADR-003: Nguồn preview nhạc cho Meloo](ADR-003-music-provider.md)
- [Kết quả đo và phương pháp SPK-01](../../../spikes/music-provider/README.md), đo ngày 2026-10-21.

## Câu hỏi bảo vệ
1. **Vì sao không dùng thẳng URL preview của nhà cung cấp?** — URL của nhà cung cấp để lộ `trackId`, tên bài hát trong đường dẫn hoặc metadata file; không kiểm soát được mức nghe 2/5/10/20/30 giây và không thu hồi được sau khi phát.
2. **Token chống gian lận bằng cách nào?** — Token được ký HMAC-SHA256 gắn chặt với bộ tứ `(roundId, playerId, level, expiresAt)`, kiểm tra bằng so sánh hằng số thời gian, chỉ phát đúng số byte ứng với mức nghe cho phép và hết hạn ngắn trong 2 phút.
3. **Vì sao backend proxy audio giảm thiểu áp lực RAM trên EC2 1 GB?** — Thay vì nạp toàn bộ file MP3 vào RAM heap hoặc lưu vào đĩa, backend chỉ dùng streaming proxy chuyển tiếp trực tiếp theo chunk với bộ đệm nhỏ (8–64 KB/kết nối), giúp tiết kiệm đáng kể heap JVM. Tuy nhiên đây mới là ước tính lý thuyết cho buffer; mức tiêu thụ RAM tổng thể (gồm socket, thread và trạng thái phòng) sẽ được đo lường và kiểm chứng thực tế bằng k6 ở QA-03.
4. **Còn hình thức gian lận nào chưa thể ngăn chặn triệt để?** — Người chơi dùng thiết bị khác nhận diện âm thanh qua loa ngoài (như Shazam). Đây là giới hạn cố hữu của nền tảng web game âm nhạc, không thể can thiệp từ phía server và được chấp nhận trong phạm vi đề tài.
