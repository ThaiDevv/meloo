# MEL-25 — Đo nguồn preview Deezer và iTunes

Spike này đo khả năng tìm và phát preview của hai nguồn trên cùng 70 bài: 30 V-pop, 20 K-pop và 20 US-UK. Lần đo được chốt ngày 2026-10-21, từ mạng tại Việt Nam; iTunes dùng storefront `VN`.

## Kết quả

| Nhóm | Số bài | Deezer có preview | iTunes có preview |
|---|---:|---:|---:|
| V-pop | 30 | 30/30 (100%) | 30/30 (100%) |
| K-pop | 20 | 16/20 (80%) | 20/20 (100%) |
| US-UK | 20 | 20/20 (100%) | 20/20 (100%) |
| **Tổng** | **70** | **66/70 (94,3%)** | **70/70 (100%)** |

Bốn bài không tìm được bản đúng nghệ sĩ trên Deezer là `How You Like That`, `SOLO`, `Money` và `Supernova`. Script từ chối bản cover có cùng tên bằng cách yêu cầu cả tên bài và nghệ sĩ đạt ngưỡng ghép.

| Thuộc tính đo | Deezer | iTunes |
|---|---|---|
| Container / codec | MP3 / MP3 | M4A / AAC |
| Bitrate | 128 kbps ở 66/66 preview | 253–290 kbps, trung bình 268,1 kbps |
| Độ dài | 29,989–30,015 giây | 29,977–30,022 giây |
| Container start time | 0 hoặc 0,048 giây | 0 giây trong mẫu đo |
| Vị trí đoạn trích trong bài gốc | API không công bố | API không công bố |
| Dấu hiệu URL hết hạn | Chữ ký `hdnea` hết hạn sau khoảng 900 giây | Không có tham số hết hạn; không suy ra URL tồn tại vĩnh viễn |

`container start time` chỉ là timestamp đầu stream. Nó không cho biết preview lấy từ giây thứ bao nhiêu của bản thu đầy đủ. Muốn đo vị trí âm nhạc phải có bản đầy đủ được cấp phép để đối sánh fingerprint; hai API không trả trường offset nên kết quả ghi `not-exposed-by-provider`.

Dữ liệu từng bài nằm ở [results.csv](results.csv), tổng hợp máy đọc ở [summary.json](summary.json). File kết quả không chứa URL preview đầy đủ; chỉ giữ SHA-256 để so sánh độ ổn định mà không commit link phát tạm thời.

## Cách chạy lại

Yêu cầu Node.js 22+, `ffprobe` và kết nối Internet:

```bash
cd spikes/music-provider
node measure.mjs
```

Mặc định script chờ 3 giây giữa hai bài để không vượt giới hạn gần 20 lệnh/phút của iTunes. Đo lại một bài và ghép vào kết quả hiện có:

```bash
node measure.mjs --only=v17 --delay-ms=0
```

Quy trình cho mỗi bài:

1. Gọi Search API với tên bài và nghệ sĩ.
2. Chuẩn hóa dấu, chữ hoa và ký tự phân cách; chấm 70% tên bài + 30% nghệ sĩ.
3. Chỉ nhận kết quả khi tổng điểm ≥ 0,7 và từng phần tên bài, nghệ sĩ ≥ 0,5.
4. Dùng `ffprobe` đọc stream trực tiếp, không lưu audio, để lấy container, codec, bitrate, duration và start time.
5. Phân tích tham số URL để ghi bằng chứng hết hạn, rồi chỉ lưu SHA-256 của URL.

Kết quả phụ thuộc catalog, vùng và thời điểm. Mỗi dòng có `measuredAt`; cần chạy lại trước khi nhập kho nhạc thật.

## API, giới hạn và nhập hàng loạt

| Nhu cầu | Deezer API | iTunes Search API / Apple Music API |
|---|---|---|
| Tìm bài | `GET /search?q=...` | iTunes `GET /search?term=...&entity=song` |
| Chart | `GET /chart/{genre_id}/tracks` chạy không cần token trong spike | iTunes Search không có; Apple Music `GET /v1/catalog/{storefront}/charts` có nhưng cần developer token |
| Playlist | `GET /playlist/{id}` và `/playlist/{id}/tracks` | iTunes Search không có; Apple Music có catalog playlist nhưng cần developer token |
| Ca sĩ / nhập top bài | `GET /artist/{id}/top` | iTunes `lookup?id={artistId}&entity=song`; Apple Music có artist relationships nhưng cần developer token |
| Xác thực lúc đo | Không cần token cho endpoint công khai đã dùng | iTunes Search không cần token; mọi request Apple Music API cần developer token ES256 |
| Rate limit công bố | Không thấy con số trong tài liệu công khai; Terms cho phép Deezer hạn chế hoặc thu hồi truy cập bất kỳ lúc nào | iTunes Search khoảng 20 calls/phút, có thể thay đổi; Apple Music trả 429 khi vượt hạn mức nhưng không công bố số cố định |

Endpoint Deezer được kiểm tra trực tiếp trong lần đo: [search](https://api.deezer.com/search?q=Adele%20Hello&limit=1), [chart](https://api.deezer.com/chart/0/tracks?limit=1), [playlist](https://api.deezer.com/playlist/908622995) và [artist top](https://api.deezer.com/artist/75798/top?limit=1).

Nguồn chính thức của Apple:

- [iTunes Search API](https://performance-partners.apple.com/search-api): tham số, giới hạn gần 20 calls/phút, preview 30 giây và điều khoản Promo Content.
- [Apple Music catalog charts](https://developer.apple.com/documentation/applemusicapi/charts): chart theo storefront, tối đa 200 resource mỗi request.
- [Apple Music catalog playlist](https://developer.apple.com/documentation/applemusicapi/get-a-catalog-playlist): lấy playlist theo ID.
- [Apple Music developer token](https://developer.apple.com/documentation/applemusicapi/generating-developer-tokens): mọi request cần JWT ES256; vượt hạn mức nhận HTTP 429.

## Điều khoản sử dụng và ghi nguồn

### Deezer

[Deezer for Developers Terms of Use](https://developers.deezer.com/termsofuse), truy cập 2026-10-21, quy định:

- API và content chỉ dùng trong môi trường phi thương mại.
- Người dùng không Premium chỉ nghe tối đa 30 giây.
- Phải thông báo điều kiện sử dụng; việc nghe content bị giới hạn cho sử dụng riêng tư trong phạm vi gia đình.
- Không được né DRM, tải trái phép hoặc dùng cho streaming/chia sẻ không được phép.
- Deezer có thể thay đổi, hạn chế hoặc thu hồi API không báo trước.
- Việc dùng logo phải theo Trademark Guidelines; trang tài liệu API hiện yêu cầu đăng nhập.

Meloo là đồ án phi thương mại nhưng beta công khai và game nhiều người chưa chắc thuộc “phạm vi gia đình”. Cần Deezer xác nhận bằng văn bản hoặc giấy phép phù hợp trước khi phát hành beta.

### iTunes / Apple

[iTunes Search API Legal](https://performance-partners.apple.com/search-api), truy cập 2026-10-21, chỉ cho dùng preview để quảng bá chính nội dung đó. Khi dùng preview phải đặt gần badge/link iTunes, ghi “provided courtesy of iTunes”, chỉ stream, không download/cache/sync, và không dùng preview cho giá trị giải trí độc lập.

Game đoán nhạc dùng preview làm giá trị giải trí chính nên không phù hợp điều khoản mặc định. iTunes chỉ có thể là nguồn dự phòng khi Apple hoặc chủ quyền cấp phép riêng; thêm attribution và badge vẫn chưa giải quyết điều cấm này.

## Kết luận kỹ thuật

- Chọn **Deezer làm nguồn chính có điều kiện**: coverage 94,3%, MP3 128 kbps giảm khoảng một nửa băng thông so với mẫu iTunes, URL ký hết hạn khoảng 15 phút, và có chart/playlist/artist endpoint đơn giản.
- Chọn **iTunes làm dự phòng kỹ thuật, tắt mặc định**: coverage 100% nhưng Search API thiếu import chart/playlist, bitrate cao hơn, URL không có dấu hết hạn và điều khoản cấm dùng preview làm giải trí độc lập.
- Trước beta, phải hoàn tất cổng pháp lý. Nếu không có xác nhận phù hợp từ Deezer và Apple, thay cả hai bằng nguồn audio đã mua quyền; adapter catalog cần giữ ranh giới provider để thay mà không đổi game domain.
- Dù dùng nguồn nào, client không nhận URL gốc. Backend fetch/proxy và cấp token mờ theo ADR-008; không cache hay cắt sẵn audio cho đến khi giấy phép cho phép.
