# ADR-003: Nguồn preview nhạc cho Meloo
- Trạng thái: Chấp nhận có điều kiện
- Ngày: 2026-10-21 · Ticket: MEL-25

## Bối cảnh

Meloo cần preview tối đa 30 giây cho cơ chế mở dần 2 → 5 → 10 → 20 → 30 giây. Nguồn nhạc phải có độ phủ V-pop, K-pop và US-UK đủ tốt, nhập hàng loạt được, phù hợp EC2 1 GB và không làm lộ đáp án qua URL. Đây là đồ án phi thương mại nhưng có beta công khai và phòng nhiều người.

Spike SPK-01 đo cùng 70 bài trên mạng tại Việt Nam ngày 2026-10-21. iTunes dùng storefront `VN`. Dữ liệu và cách tái hiện nằm ở `spikes/music-provider/`.

## Các phương án

| Phương án | Ưu | Nhược |
|---|---|---|
| Deezer | Có preview 66/70 (94,3%); V-pop và US-UK 100%; MP3 128 kbps; URL ký hết hạn khoảng 15 phút; có chart, playlist và artist top công khai | K-pop chỉ 16/20 (80%); API có thể bị hạn chế không báo trước; điều khoản giới hạn phi thương mại và nghe riêng tư trong phạm vi gia đình nên beta công khai cần xác nhận |
| iTunes Search | Có preview 70/70 (100%); tìm kiếm theo storefront; không cần token | M4A/AAC trung bình 268,1 kbps; khoảng 20 calls/phút; không có chart/playlist; URL không có dấu hết hạn; điều khoản cấm dùng preview cho giá trị giải trí độc lập |
| Nguồn audio được cấp phép riêng | Quyền sử dụng và cách cắt/proxy rõ; kiểm soát catalog, offset và expiry | Tốn chi phí và công sức ký quyền; chưa có trong phạm vi đồ án hiện tại |

## Quyết định

Chọn **Deezer làm nguồn chính có điều kiện** cho adapter catalog. Chọn **iTunes làm dự phòng kỹ thuật nhưng tắt mặc định**.

Lý do chọn Deezer là băng thông thấp hơn, URL có chữ ký hết hạn và có endpoint nhập hàng loạt. Độ phủ tổng 94,3% đủ để tạo bộ đề ban đầu; bốn khoảng trống K-pop có thể loại khỏi catalog hoặc nhập từ nguồn đã cấp phép khác. Không tự chuyển sang bản cover chỉ vì trùng tên bài.

Điều kiện phát hành: Thái phải có xác nhận bằng văn bản hoặc giấy phép cho phép dùng preview Deezer trong beta game nhiều người. iTunes chỉ được bật khi có quyền riêng cho mục đích game; attribution và badge không thay thế quyền này. Nếu không đạt cổng pháp lý, thay cả hai bằng nguồn audio được cấp phép trước beta.

Adapter phải lưu provider track ID và metadata, không lưu URL preview lâu dài. URL được lấy sát lúc phát; lỗi provider phải được xử lý như nguồn tạm thời không sẵn sàng.

## Ảnh hưởng tới ADR-008

- Client chỉ nhận token mờ của Meloo. Tên bài, nghệ sĩ, provider ID và URL gốc không xuất hiện trước `ROUND_END`.
- Backend resolve URL Deezer theo provider track ID rồi stream qua endpoint audio token. URL Deezer hết hạn khoảng 15 phút nên không được persist hoặc tái sử dụng giữa các phiên.
- Mỗi mức nghe vẫn dùng token HMAC riêng theo `(roundId, playerId, level, expiresAt)` và chỉ stream đúng 2/5/10/20/30 giây.
- Không cache, tải hoặc cắt sẵn preview cho đến khi giấy phép nguồn cho phép. Việc cắt stream tại thời gian chạy cũng phải được xác nhận trong phạm vi cấp phép.
- Mẫu Deezer 128 kbps tương đương khoảng 480 KB cho 30 giây; mẫu iTunes trung bình 268,1 kbps tương đương khoảng 1,01 MB. Deezer giảm băng thông proxy khoảng 52% so với iTunes trong mẫu đo.

## Hệ quả

- Tốt: catalog có nguồn chính rõ ràng, nhập được từ chart/playlist/artist; băng thông phù hợp máy nhỏ; URL nguồn có hạn dùng; provider adapter có đường thay thế.
- Tốt: dữ liệu đo và script tái hiện được, ghép cả tên bài lẫn nghệ sĩ để tránh nhập nhầm cover.
- Rủi ro: Deezer thiếu 20% mẫu K-pop. Catalog import phải báo bài không match để quản trị viên duyệt, không tự hạ ngưỡng.
- Rủi ro: quyền sử dụng chưa được xác nhận cho beta công khai. Đây là release gate; không giải quyết bằng thay đổi kỹ thuật.
- Rủi ro: provider có thể đổi catalog, format, URL và quota. Chạy lại spike trước khi seed catalog và theo dõi lỗi resolve preview.
- Chi phí: backend phải proxy audio; 30 giây Deezer khoảng 480 KB/người/lượt ở bitrate đo được, chưa tính overhead.

## Nguồn

- [Kết quả và phương pháp SPK-01](../../../spikes/music-provider/README.md), đo 2026-10-21.
- [Deezer for Developers Terms of Use](https://developers.deezer.com/termsofuse), truy cập 2026-10-21.
- [iTunes Search API và Legal](https://performance-partners.apple.com/search-api), truy cập 2026-10-21.
- [Apple Music catalog charts](https://developer.apple.com/documentation/applemusicapi/charts), truy cập 2026-10-21.
- [Apple Music developer tokens](https://developer.apple.com/documentation/applemusicapi/generating-developer-tokens), truy cập 2026-10-21.

## Câu hỏi bảo vệ

1. Vì sao chọn Deezer dù iTunes đạt 100%? — Deezer dùng khoảng nửa băng thông, URL có hạn và có endpoint nhập hàng loạt; iTunes còn cấm dùng preview cho giá trị giải trí độc lập.
2. Bốn bài K-pop thiếu xử lý thế nào? — Loại khỏi bộ đề hoặc lấy từ nguồn có quyền khác; không dùng cover sai nghệ sĩ.
3. URL Deezer hết hạn có gây gián đoạn vòng không? — Backend resolve sát lúc phát; hạn khoảng 15 phút dài hơn một vòng nhưng URL không được lưu lâu dài.
4. Vì sao vẫn cần token Meloo khi URL Deezer đã ký? — URL nguồn không gắn với người chơi, vòng và mức nghe; token Meloo mới kiểm quyền và che metadata đáp án.
5. Có thể ra beta ngay với Deezer không? — Chưa. Cần xác nhận quyền cho beta nhiều người và việc proxy/cắt stream; nếu không có thì dùng nguồn đã cấp phép.
