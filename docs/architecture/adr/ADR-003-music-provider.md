# ADR-003: Nguồn preview nhạc cho Meloo
- Trạng thái: Đề xuất
- Ngày: 2026-10-21 · Ticket: MEL-25

## Bối cảnh

Meloo cần preview tối đa 30 giây cho cơ chế mở dần 2 → 5 → 10 → 20 → 30 giây. Nguồn nhạc phải có độ phủ V-pop, K-pop và US-UK đủ tốt, nhập hàng loạt được, phù hợp EC2 1 GB và không làm lộ đáp án qua URL. Đây là đồ án phi thương mại nhưng có beta công khai và phòng nhiều người.

Spike SPK-01 đo cùng 70 bài trên mạng tại Việt Nam ngày 2026-10-21. iTunes dùng storefront `VN`. Dữ liệu và cách tái hiện nằm ở `spikes/music-provider/`.

## Các phương án

| Phương án | Ưu | Nhược |
|---|---|---|
| Deezer | Có preview 66/70 (94,3%); V-pop và US-UK 100%; MP3 128 kbps; URL ký hết hạn khoảng 15 phút; có chart, playlist và artist top công khai | K-pop chỉ 16/20 (80%); API có thể bị hạn chế không báo trước; điều khoản giới hạn phi thương mại và nghe riêng tư trong phạm vi gia đình nên beta công khai cần xác nhận |
| iTunes Search + Apple RSS | Có preview 70/70 truy vấn (100%), gồm 68 bản thu gốc và 2 biến thể; RSS có top songs/playlists; tìm theo storefront; không cần token cho RSS và iTunes Search | M4A/AAC trung bình 268,1 kbps; Search khoảng 20 calls/phút; độ bền RSS chưa kiểm; URL không có dấu hết hạn; điều khoản cấm dùng preview cho giá trị giải trí độc lập |
| Nguồn audio được cấp phép riêng | Quyền sử dụng và cách cắt/proxy rõ; kiểm soát catalog, offset và expiry | Tốn chi phí và công sức ký quyền; chưa có trong phạm vi đồ án hiện tại |

## Quyết định

Đề xuất **Deezer làm nguồn chính có điều kiện** cho adapter catalog. Đề xuất **iTunes làm dự phòng kỹ thuật, tắt mặc định (LEG-02 kết luận không kích hoạt nếu không có hợp đồng thương mại riêng với Apple)**. Quyết định vận hành chờ hoàn tất xác nhận quyền sử dụng trước beta.

Lý do ưu tiên Deezer là băng thông thấp hơn, URL có chữ ký hết hạn và có endpoint chart/playlist/artist để nhập hàng loạt. Độ phủ tổng 94,3% đủ để tạo bộ đề ban đầu; bốn khoảng trống K-pop có thể loại khỏi catalog hoặc nhập từ nguồn đã cấp phép khác. Không tự chuyển sang bản cover chỉ vì trùng tên bài.

iTunes Search tự nó không có chart endpoint. Apple RSS Builder có feed top songs và top playlists; feed VN đã trả HTTP 200 trong lần kiểm tra ngày 2026-10-21. Có thể lấy preview bằng iTunes lookup theo track ID trong feed. Độ bền, rate limit và điều kiện dùng feed cho mục đích của Meloo chưa được xác nhận, nên không dùng điều này làm lý do pháp lý để chọn provider.

Điều kiện phát hành: phải có xác nhận bằng văn bản hoặc giấy phép cho phép dùng preview Deezer trong beta game nhiều người, gồm proxy và giới hạn stream. iTunes tắt mặc định; chỉ được bật khi có hợp đồng cấp phép riêng cho mục đích game; attribution và badge không thay thế quyền này.

Kết quả LEG-02 (MEL-120, 2026-10-28, xem `docs/legal/music-preview-licensing.md`): Deezer đã phản hồi bằng văn bản, không xác nhận được việc dùng preview cho gameplay nhiều người, và cho biết hiện không nhận đăng ký ứng dụng API mới và không có mốc mở lại. Vì vậy **điều kiện phát hành chưa được đáp ứng**. Thái quyết định (2026-10-28) vẫn dùng Deezer ở mọi môi trường, kể cả closed beta và beta công khai, và tự chấp nhận rủi ro. Đây là ngoại lệ so với cổng phát hành ở trên, được ghi nhận có chủ đích trong LEG-02 (Mục 6.1) và **không phải sự cho phép của Deezer**. Jamendo API là nguồn thay thế ưu tiên nếu Deezer chặn truy cập hoặc yêu cầu dừng; việc xác minh giấy phép từng bài, đo mẫu và hỏi Jamendo về proxy cần ticket riêng, và agent không tự chọn nhà cung cấp thay thế. iTunes giữ nguyên là dự phòng kỹ thuật, tắt mặc định.

Adapter lưu provider track ID và metadata, không persist URL preview hoặc audio. Có thể giữ URL ký Deezer trong memory của tiến trình cho request/vòng hiện tại, tối đa tới khi vòng kết thúc và không quá thời hạn URL (đo khoảng 15 phút); không dùng URL sau hạn hoặc qua phiên. Đây là cách triển khai đề xuất cần được xác nhận trong cổng pháp lý. Lỗi provider phải được xử lý như nguồn tạm thời không sẵn sàng.

## Ảnh hưởng tới ADR-008

- Client chỉ nhận token mờ của Meloo. Tên bài, nghệ sĩ, provider ID và URL gốc không xuất hiện trước `ROUND_END`.
- Backend resolve URL Deezer theo provider track ID rồi stream qua endpoint audio token. URL Deezer hết hạn khoảng 15 phút nên không được persist hoặc tái sử dụng giữa các phiên.
- Mỗi mức nghe vẫn dùng token HMAC riêng theo `(roundId, playerId, level, expiresAt)` và chỉ stream đúng 2/5/10/20/30 giây.
- Không lưu audio hoặc cắt sẵn preview cho đến khi giấy phép cho phép. Proxy, stream từng đoạn và việc giữ URL ký tạm thời trong memory cũng phải được xác nhận trong phạm vi cấp phép.
- Nếu mỗi mức mở phát lại từ giây 0, một người nghe đủ năm mức có thể nhận tổng cộng 67 giây (2+5+10+20+30): khoảng 1,07 MB Deezer ở 128 kbps hoặc 2,24 MB iTunes ở 268,1 kbps. Nếu chỉ truyền phần mới thì tổng là 30 giây, khoảng 0,48 MB hoặc 1,01 MB. Tỷ lệ tiết kiệm băng thông của Deezer so với iTunes vẫn khoảng 52% trong cả hai cách.
- Phòng năm người ở trường hợp phát lại từng mức tương đương khoảng 5,36 MB Deezer hoặc 11,23 MB iTunes mỗi vòng, chưa tính overhead và retransmission. Đây là ước lượng byte truyền, chưa chứng minh RAM hoặc số phòng đồng thời chịu được; MEL-118 cần đo RAM, băng thông và tải proxy trên EC2 1 GB.

## Hệ quả

- Tốt: catalog có nguồn chính rõ ràng, nhập được từ chart/playlist/artist; băng thông phù hợp máy nhỏ; URL nguồn có hạn dùng; provider adapter có đường thay thế.
- Tốt: dữ liệu đo và script tái hiện được, ghép cả tên bài lẫn nghệ sĩ để tránh nhập nhầm cover.
- Rủi ro: Deezer thiếu 20% mẫu K-pop. Catalog import phải báo bài không match để quản trị viên duyệt, không tự hạ ngưỡng.
- Rủi ro: quyền sử dụng chưa được xác nhận cho beta công khai. Đây là release gate; không giải quyết bằng thay đổi kỹ thuật.
- Rủi ro: Deezer đã cho biết không nhận ứng dụng API mới và truy cập hiện có không được đảm bảo giữ nguyên (LEG-02, 2026-10-28). Không xây thêm phần phụ thuộc vào Deezer ngoài dev và test.
- Rủi ro: Jamendo là kho nhạc độc lập, không có bài hit; giấy phép từng bài khác nhau và điều khoản không nói về proxy cắt đoạn (LEG-02, Mục 5.1). Backend cần giá trị enum nhà cung cấp mới, adapter và chỗ lưu giấy phép/ghi công.
- Rủi ro: provider có thể đổi catalog, format, URL và quota. Chạy lại spike trước khi seed catalog và theo dõi lỗi resolve preview.
- Rủi ro: provider không công bố offset trong bài. Đoạn 2 giây đầu có thể khác độ khó giữa các bài, ảnh hưởng công bằng tính điểm; cần playtest và xác định cách chọn/căn đoạn khi có quyền xử lý audio.
- Chi phí: backend phải proxy audio; băng thông ước lượng ở trên chưa gồm overhead. Mức RAM và tải đồng thời còn chờ đo trong MEL-118.

## Nguồn

- [LEG-02: Thẩm định quyền sử dụng preview nhạc cho Meloo](../../legal/music-preview-licensing.md), 2026-10-28.
- [Jamendo API Terms of Use](https://devportal.jamendo.com/api_terms_of_use), truy cập 2026-10-28.
- [Kết quả và phương pháp SPK-01](../../../spikes/music-provider/README.md), đo 2026-10-21.
- [Deezer for Developers Terms of Use](https://developers.deezer.com/termsofuse), truy cập 2026-10-21.
- [iTunes Search API và Legal](https://performance-partners.apple.com/search-api), truy cập 2026-10-21.
- [Apple RSS Builder](https://rss.marketingtools.apple.com/) và [Apple marketing tools](https://performance-partners.apple.com/tools), truy cập 2026-10-21.
- [Apple Music catalog charts](https://developer.apple.com/documentation/applemusicapi/charts), truy cập 2026-10-21.
- [Apple Music developer tokens](https://developer.apple.com/documentation/applemusicapi/generating-developer-tokens), truy cập 2026-10-21.

## Câu hỏi bảo vệ

1. Vì sao ưu tiên Deezer dù iTunes có 70/70 preview? — iTunes có 68 bản thu gốc và 2 biến thể; Deezer dùng khoảng nửa băng thông, URL có hạn và có API nhập hàng loạt; điều khoản của cả hai cần được xác nhận trước beta.
2. Bốn bài K-pop thiếu xử lý thế nào? — Loại khỏi bộ đề hoặc lấy từ nguồn có quyền khác; không dùng cover sai nghệ sĩ.
3. URL Deezer hết hạn có gây gián đoạn vòng không? — Có thể giữ URL ký tạm thời trong memory tối đa vòng hiện tại và hạn URL; thiết kế này vẫn cần provider xác nhận.
4. Vì sao vẫn cần token Meloo khi URL Deezer đã ký? — URL nguồn không gắn với người chơi, vòng và mức nghe; token Meloo mới kiểm quyền và che metadata đáp án.
5. Có thể ra beta ngay với Deezer không? — Về kỹ thuật được; về quyền thì chưa. Deezer đã trả lời không nhận ứng dụng mới và không xác nhận cách dùng của Meloo (LEG-02). Thái quyết định vẫn dùng Deezer cho beta và tự chấp nhận rủi ro; Jamendo là phương án đổi nguồn nếu Deezer chặn.
