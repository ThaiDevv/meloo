# BIZ-02 · Phát biểu bài toán, personas và giá trị cốt lõi

> Ticket: MEL-18 · Trạng thái: **đã duyệt** (Thái, 19/10/2026)
> Nguồn: tài liệu kế hoạch Meloo (mục "Bài toán & giá trị") và số liệu công khai ở mục Nguồn.
> `[CẦN SỐ LIỆU]` đánh dấu chỗ chờ kết quả khảo sát (BIZ-03, BIZ-05) hoặc beta (BIZ-07).

## 1. Meloo trong một câu

**Meloo là trò chơi đoán tên bài hát 10–15 phút, vào ngay bằng một đường link, để nhóm bạn người Việt cùng chơi, cùng nói chuyện và so tài bằng chính nhạc Việt họ nghe hằng ngày.**

## 2. Bối cảnh

- Nghe nhạc là thói quen hằng ngày. Theo khảo sát của Q&Me, 75% người Việt nghe nhạc mỗi ngày, 93% nghe trên điện thoại [S1].
- Nhạc được nghe chủ yếu trên nền tảng streaming và mạng xã hội. Tỷ lệ người dùng: YouTube 74%, Zing MP3 45%, TikTok 32%, Spotify 27% (Decision Lab, quý 4/2025) [S1]. Ở đây, nghe nhạc là việc **một người làm một mình và thụ động**: không có thử thách, không có ai để so tài.
- Game đoán nhạc đã chứng minh sức hút. Heardle, phiên bản âm nhạc của Wordle, đạt khoảng **69 triệu lượt truy cập trong tháng 3/2022** [S3]. Spotify mua Heardle năm 2022, sau đó đóng cửa ngày 05/05/2023 để tập trung vào tính năng khám phá nhạc trong app [S2][S3].
- Các game đoán nhạc phổ biến hiện nay gần như không phục vụ người Việt. SongPop hỗ trợ 17 ngôn ngữ nhưng không có tiếng Việt, và cần cài app [S4]. Một số web có giao diện tiếng Việt nhưng chỉ chơi một mình, chọn 1 trong 4 đáp án, dùng nhạc quốc tế [S5]. Phân tích đối thủ đầy đủ: BIZ-04 (`02-competitors.md`).

## 3. Phát biểu bài toán

> **Nhóm bạn trẻ người Việt (sinh viên, đồng nghiệp)** muốn có một hoạt động online ngắn để chơi cùng nhau vào buổi tối hoặc giờ nghỉ.
> **Nhưng hiện tại** các game đoán nhạc có sẵn hoặc không có nhạc Việt và tiếng Việt, hoặc phải cài app và đăng ký, hoặc chỉ chơi một mình. Muốn chơi cùng nhau, họ phải ghép một game nước ngoài với Discord hoặc Messenger để nói chuyện.
> **Hệ quả:** mất 5–10 phút chuẩn bị cho một trò chơi chỉ 10 phút. Người không rành công nghệ bỏ cuộc giữa chừng. Nội dung lại không phải nhạc họ thuộc.
> **Meloo giải quyết** bằng phòng chơi realtime mở bằng link, chơi không cần tài khoản, có sẵn chat và voice, kho nhạc Việt và quốc tế phân loại rõ, cơ chế nghe mở dần 5 mức để người thuộc bài nhiều được thưởng điểm cao hơn.

Phần in đậm "Nhóm bạn…" và "Nhưng hiện tại…" là **giả thuyết**. Mục 4 nêu cách kiểm chứng từng ý.

## 4. Giả thuyết vấn đề và cách kiểm chứng

Mỗi giả thuyết có hai lớp bằng chứng: **khảo sát** (BIZ-03, trước khi code xong) và **hành vi thật** (beta, BIZ-06/BIZ-07). Câu khảo sát mang mã `K…` để BIZ-03 dùng lại.

| Mã | Giả thuyết | Câu khảo sát (BIZ-03) | Xác nhận khi | Số liệu beta |
|---|---|---|---|---|
| **H1** | Nhóm bạn thiếu một hoạt động online **ngắn, vào ngay**; rào cản lớn nhất là phải cài app hoặc đăng ký. | K1. Trong 3 tháng qua bạn chơi game online cùng bạn bè bao lâu một lần? · K2. Khi chơi cùng nhau, bạn dùng gì để nói chuyện? · K3. Điều gì khiến bạn ngại thử một game mới cùng bạn bè? (nhiều lựa chọn) | ≥ 50% chơi cùng bạn bè từ 1 lần/tháng **và** ≥ 40% chọn "phải cài app/đăng ký" hoặc "mất thời gian rủ/hướng dẫn" ở K3 | Tỷ lệ người vào phòng bằng link mời mà chơi hết trận; số người trung bình mỗi phòng riêng |
| **H2** | Người chơi muốn đoán **nhạc Việt** với **giao diện tiếng Việt**; game hiện có không đáp ứng. | K4. Bạn đã từng chơi game đoán nhạc nào? · K5. Bạn muốn đoán loại nhạc nào? (V-pop, US-UK, K-pop, nhạc phim…, chọn tối đa 2) · K6. Game đoán nhạc bạn từng chơi thiếu điều gì? | ≥ 60% chọn V-pop trong 2 lựa chọn ở K5 **và** trong nhóm từng chơi game đoán nhạc, ≥ 40% chọn "ít nhạc Việt" hoặc "không có tiếng Việt" ở K6 | Tỷ lệ trận chọn bộ đề nhạc Việt |
| **H3** | Người nghe nhạc thích **thử thách ngắn hằng ngày** và **so tài, khoe kết quả** (hiệu ứng Wordle/Heardle). | K7. Bạn có chơi mini-game hằng ngày (Wordle, Heardle, đố vui…) không? · K8. Bạn có từng chia sẻ kết quả game lên mạng xã hội hoặc nhóm chat không? | ≥ 30% trả lời "có" ở K7 **hoặc** ≥ 40% trả lời "có" ở K8 | Người chơi quay lại ≥ 2 ngày khác nhau ≥ 30% (KPI); số lượt bấm "Chia sẻ" ở thử thách ngày |
| **H4** | Người tổ chức (lớp trưởng, giáo viên, HR, streamer) cần **hoạt động khởi động 10 phút** có bộ đề theo chủ đề. *Cơ hội Phase 2, không phải lõi MVP.* | K9. Bạn có từng tổ chức hoạt động cho lớp/nhóm/công ty không? · K10. Nếu có, bạn có muốn dùng một game đoán nhạc cho phần khởi động không? | ≥ 20% từng tổ chức **và** trong số đó ≥ 50% quan tâm | Số phòng riêng có ≥ 5 người dùng bộ đề tuyển chọn |

Ngoài 4 giả thuyết, BIZ-03 hỏi thêm K11 (sẵn sàng trả tiền cho gói Premium, mức giá) và K12 (thông tin nhân khẩu: tuổi, nghề nghiệp). Hai câu này phục vụ BIZ-08, không dùng để xác nhận bài toán.

**Nếu giả thuyết bị bác bỏ:** ghi kết quả thật vào BIZ-05, đổi trọng tâm và báo cáo trung thực. Ví dụ H2 sai (người chơi thích US-UK hơn) thì giữ nguyên sản phẩm nhưng đổi bộ đề mặc định và thông điệp; H1 sai thì nhấn mạnh chơi đơn và thử thách ngày thay vì phòng nhóm. Thầy đánh giá cao việc kiểm chứng giả thuyết hơn việc giả thuyết đúng ngay từ đầu.

**Ngưỡng đã chốt ngày 19/10/2026, trước khi phát khảo sát, và không đổi sau khi có phản hồi.** Đặt ngưỡng trước để kết luận không bị chỉnh theo số liệu.

## 5. Personas

Bốn nhân vật dưới đây là **hư cấu, đại diện cho từng nhóm người dùng**, dựng từ giả thuyết. Sau BIZ-05, sửa lại theo số liệu khảo sát.

### P1 · Minh, 20 tuổi: nhóm bạn sinh viên *(người dùng chính)*
- **Bối cảnh:** sinh viên năm 2 ở TP.HCM. Tối nào cũng gọi Discord với 4 bạn cùng phòng ký túc xá cũ, giờ mỗi người học một trường.
- **Muốn:** một trò chơi nhanh, cả nhóm cùng vào trong 1 phút, vừa chơi vừa trêu nhau.
- **Khó khăn:** game đoán nhạc nước ngoài thì toàn bài không ai biết; có bạn chơi trên điện thoại, không muốn cài app hay tạo tài khoản; phải mở song song game và Discord.
- **Kịch bản dùng Meloo:** Minh tạo phòng riêng, gửi link vào nhóm chat. Bạn bè bấm link, nhập biệt danh rồi vào sảnh. Cả nhóm bật voice, chọn bộ "V-pop 2020s", chơi 10 vòng.
- **Tính năng chính:** khách không cần đăng ký, phòng riêng bằng mã hoặc link, voice và chat trong phòng, bảng điểm trực tiếp.
- **Meloo thành công với Minh khi:** nhóm chơi lại vào tối khác mà Minh không cần rủ.

### P2 · Lan, 24 tuổi: fan âm nhạc
- **Bối cảnh:** nhân viên văn phòng, nghe Zing MP3 và Spotify cả ngày, thuộc lời rất nhiều bài, từng chơi Heardle mỗi sáng.
- **Muốn:** chứng minh mình "nghe 2 giây là biết bài", có thành tích để khoe.
- **Khó khăn:** Heardle đã đóng cửa; các game còn lại chủ yếu nhạc quốc tế, chọn đáp án trắc nghiệm nên dễ đoán mò.
- **Kịch bản dùng Meloo:** mỗi sáng trên xe buýt chơi thử thách ngày, rồi chia sẻ kết quả vào nhóm chat công ty. Cuối tuần leo bảng xếp hạng.
- **Tính năng chính:** thử thách ngày, đoán bằng ô gợi ý (không phải trắc nghiệm), điểm theo mức nghe, huy hiệu, bảng xếp hạng.
- **Meloo thành công với Lan khi:** Lan quay lại ít nhất 3 ngày mỗi tuần.

### P3 · Cô Hạnh, 32 tuổi: người tổ chức
- **Bối cảnh:** giáo viên tiếng Anh, phụ trách hoạt động ngoại khóa. Cũng đại diện cho lớp trưởng, HR công ty, streamer.
- **Muốn:** một hoạt động khởi động 10 phút cho lớp 30 học sinh, có bộ đề theo chủ đề (nhạc tiếng Anh, nhạc thiếu nhi, nhạc Tết…).
- **Khó khăn:** Kahoot phải tự soạn câu hỏi và không phát nhạc kiểu mở dần; tự bật nhạc trên YouTube cho cả lớp đoán thì không chấm điểm được.
- **Kịch bản dùng Meloo (MVP):** cô Hạnh dùng phòng riêng với bộ đề tuyển chọn; học sinh chia nhóm, mỗi nhóm một máy. **Phase 2:** chế độ Host chiếu lên màn hình lớn, hơn 5 người chơi, bộ đề tự tạo.
- **Tính năng chính:** bộ đề tuyển chọn (MVP); chế độ Host, bộ đề tự tạo (Phase 2).
- **Vì sao vẫn giữ persona này:** đây là nhóm có thể trả tiền (B2B) trong kế hoạch khởi nghiệp (BIZ-08).

### P4 · Quản trị viên Meloo: vận hành
- **Bối cảnh:** người vận hành hệ thống (trong đồ án là Thái).
- **Muốn:** kho nhạc sạch, không có nội dung xấu trong chat, biết sản phẩm có người dùng thật hay không.
- **Khó khăn:** không theo dõi được hệ thống thì không chứng minh được KPI, cũng không xử lý kịp báo cáo vi phạm.
- **Tính năng chính:** dashboard thống kê, quản lý kho nhạc và bộ đề, quản lý người dùng, xử lý báo cáo chat, nhật ký thao tác.
- **Meloo thành công với P4 khi:** mọi KPI ở mục 7 lấy được trực tiếp từ dashboard, không phải đếm tay.

## 6. Giá trị cốt lõi và điểm khác biệt

Bốn giá trị cốt lõi, theo thứ tự ưu tiên:

1. **Chơi cùng nhau ngay lập tức.** Một link là vào, không cài app, không cần tài khoản. Chat và voice có sẵn trong phòng. *(giải quyết H1, P1)*
2. **Nhạc người Việt thật sự nghe.** Bộ đề V-pop theo thập niên, thể loại, ca sĩ; giao diện tiếng Việt và tiếng Anh; tìm tên bài không cần gõ dấu. *(H2, P1, P2)*
3. **Thưởng cho người thuộc bài.** Nghe mở dần 2 → 5 → 10 → 20 → 30 giây, đoán sớm được nhiều điểm (1.000 → 200). Đoán bằng ô gợi ý thay vì trắc nghiệm nên khó đoán mò. *(H3, P2)*
4. **Công bằng.** Server quyết định đáp án, điểm và đồng hồ. Không thể xem đáp án qua mạng, chat tự chặn tin nhắn lộ đáp án. *(điều kiện để so tài và xếp hạng có ý nghĩa)*

So sánh sơ bộ. Đây là ghi nhận ban đầu, **BIZ-04 sẽ kiểm chứng và bổ sung đối thủ**:

| Tiêu chí | Meloo | SongPop [S4] | Web đoán nhạc tiếng Việt (VD Musci.io) [S5] | Heardle (đã đóng) [S2] |
|---|---|---|---|---|
| Chơi trên web, không cài app | Có | Không (app) | Có | Có |
| Chơi nhiều người realtime | Phòng 2–5 người | Có | Không ghi nhận | Không |
| Voice trong phòng | Có (phòng riêng) | Không ghi nhận | Không ghi nhận | Không |
| Giao diện tiếng Việt | Có | Không | Có | Không |
| Kho nhạc Việt | Có | `[BIZ-04]` | Không ghi nhận | Không |
| Nghe mở dần nhiều mức | 5 mức | Không | Không | Có |
| Cách đoán | Ô gợi ý | Trắc nghiệm | Trắc nghiệm 4 đáp án | Ô gợi ý |
| Thử thách hằng ngày | Có | `[BIZ-04]` | Không ghi nhận | Có |

**Điểm khác biệt một câu:** Meloo ghép **cơ chế nghe mở dần của Heardle** với **phòng chơi realtime có voice**, trên **nhạc Việt và tiếng Việt**, mở bằng một link.

## 7. KPI chứng minh có người dùng thật

Lấy từ bảng KPI trong tài liệu kế hoạch. Số liệu chụp từ dashboard admin (BE-ADM-01) và Google Form, đưa vào báo cáo cuối kỳ.

| Chỉ số | Mục tiêu | Đo vào | Nguồn | Kiểm chứng giả thuyết |
|---|---|---|---|---|
| Phản hồi khảo sát nhu cầu | ≥ 50 | 08/11 | Google Form | H1–H4 |
| Tài khoản đăng ký | ≥ 30 | 29/11 | Dashboard admin | Sản phẩm có người dùng |
| Trận đã chơi | ≥ 100 | 29/11 | Dashboard admin | H1, H3 |
| Người chơi quay lại ≥ 2 ngày khác nhau | ≥ 30% | 29/11 | Dashboard admin | H3 |
| Điểm hài lòng (CSAT, thang 1–5) | ≥ 4,0 | 29/11 | Form sau beta | Giá trị cốt lõi |

Kết quả thực tế: `[CẦN SỐ LIỆU: điền ở BIZ-05 và BIZ-07]`.

## 8. Ngoài phạm vi

Nói rõ trước để tránh bị hỏi "sao không làm X":
- **Không phát cả bài.** Chỉ dùng đoạn preview 30 giây từ API âm nhạc hợp lệ; phát cả bài cần bản quyền mà đồ án không có.
- **Không phải ứng dụng nghe nhạc**, không thay Zing MP3 hay Spotify.
- **Không phải mạng xã hội:** không có kết bạn, nhắn tin riêng hay bảng tin.
- Phòng lớn hơn 5 người, chế độ Host, bộ đề tự tạo và thu phí thuộc **Phase 2**.

## 9. Câu hỏi bảo vệ

1. **"Đã có SongPop, Kahoot, sao còn làm Meloo?"** SongPop phải cài app, không có tiếng Việt, chơi trắc nghiệm. Kahoot phải tự soạn câu hỏi, không có cơ chế nghe mở dần. Meloo nhắm vào khoảng trống: nhóm bạn Việt, mở bằng link, nhạc Việt, có voice. Số liệu ở mục 2 và 6.
2. **"Làm sao biết người dùng cần cái này?"** Ban đầu là giả thuyết (mục 4). Em kiểm chứng bằng khảo sát với ngưỡng đặt trước, sau đó bằng số liệu beta thật (mục 7).
3. **"Nếu khảo sát cho kết quả ngược lại thì sao?"** Em báo cáo đúng kết quả và điều chỉnh trọng tâm, ví dụ đổi bộ đề mặc định hoặc nhấn mạnh chế độ chơi đơn. Kiểm chứng giả thuyết chính là mục đích của bước này.
4. **"Bản quyền nhạc?"** Chỉ dùng đoạn preview 30 giây mà nền tảng âm nhạc cung cấp công khai qua API. Đường dẫn audio là token mờ, hết hạn sau thời gian ngắn, không chứa tên bài.
5. **"Heardle từng nổi tiếng rồi đóng cửa, Meloo có bị vậy không?"** Heardle giảm từ khoảng 69 triệu xuống 41 triệu lượt truy cập mỗi tháng khi độ mới lạ qua đi, và Spotify chọn tập trung vào tính năng khám phá nhạc trong app [S2][S3]. Heardle chỉ có một câu đố mỗi ngày, chơi một mình. Meloo không dựa vào độ mới lạ đó: chế độ chính là chơi nhóm, một hoạt động xã hội lặp lại được. Thử thách ngày chỉ là phần phụ.

## 10. Bản trình bày 2 phút

> Ai cũng nghe nhạc mỗi ngày, 75% người Việt nghe nhạc hằng ngày. Nhưng nghe nhạc là việc một mình, không có thử thách. Năm 2022, Heardle, game đoán nhạc kiểu Wordle, có 69 triệu lượt truy cập trong một tháng. Điều đó cho thấy người ta thích thử tài âm nhạc.
>
> Vấn đề là các game đoán nhạc hiện nay không dành cho nhóm bạn người Việt: phải cài app, không có tiếng Việt, ít nhạc Việt, và muốn nói chuyện thì phải mở thêm Discord.
>
> Meloo giải quyết bằng một đường link: bạn tạo phòng, gửi link, bạn bè vào ngay không cần tài khoản, có sẵn chat và voice. Mỗi vòng, đoạn nhạc mở dần từ 2 giây tới 30 giây, đoán càng sớm càng nhiều điểm, nên người thuộc bài thật sự được thưởng.
>
> Em có bốn nhóm người dùng: nhóm bạn sinh viên là chính, fan âm nhạc chơi thử thách ngày, người tổ chức sự kiện là hướng thu phí sau này, và quản trị viên vận hành hệ thống.
>
> Những vấn đề này em coi là giả thuyết. Em kiểm chứng bằng khảo sát với ngưỡng đặt trước, rồi bằng số liệu beta: mục tiêu 30 tài khoản, 100 trận và 30% người chơi quay lại.

## Nguồn

Ngày truy cập: 19/10/2026.

- [S1] Việt Báo, "Nền tảng nghe nhạc nội địa tiếp tục chiếm số lượng người dùng lớn nhất Việt Nam", 11/03/2026, dẫn Decision Lab *The Connected Consumer* Q4/2025 và khảo sát Q&Me. https://vietbao.vn/nen-tang-nghe-nhac-noi-dia-tiep-tuc-chiem-so-luong-nguoi-dung-lon-nhat-viet-nam-586202.html Việc cần làm sau (BIZ-04): tìm báo cáo gốc của Decision Lab và Q&Me để trích trực tiếp.
- [S2] 9to5Mac, "Spotify is shutting down Heardle", 14/04/2023. https://9to5mac.com/2023/04/14/heardle-spotify-shutting-down/
- [S3] dot.la, "Spotify Shuts Down Heardle, Signaling the Waning of the 'Wordle Craze'", 17/04/2023. https://dot.la/spotify-heardle-shut-down-2659860389.html
- [S4] App Store, "SongPop® - Guess The Song". https://apps.apple.com/us/app/-/id1528066727
- [S5] Musci.io, "Đoán bài hát". https://musci.io/vi/guess-the-song
