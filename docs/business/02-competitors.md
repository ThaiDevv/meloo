# Phân tích đối thủ và điểm khác biệt

> Ticket: BIZ-04 (MEL-20) · Trạng thái: nháp, chờ Thái duyệt · Ngày truy cập mọi nguồn: 19/10/2026
> Liên quan: BIZ-02 (`01-problem.md`, giả thuyết vấn đề 1–3), BIZ-03 (`03-survey.md`), BIZ-08 (kế hoạch khởi nghiệp)

## 1. Mục tiêu và cách làm

Tài liệu trả lời câu hỏi: "Đã có game đoán nhạc rồi, Meloo khác gì?".

Cách làm:
- Chọn 6 sản phẩm thuộc 3 nhóm: game đoán nhạc (web và app), quiz chung, ứng dụng nghe nhạc có mini-game.
- So sánh theo 6 tiêu chí ticket yêu cầu: ngôn ngữ giao diện, có nhạc Việt, chơi nhiều người realtime, voice, thử thách ngày, giá.
- Chỉ dùng thông tin đọc được trên trang chính thức, kho ứng dụng hoặc báo công nghệ. Mỗi ô có số nguồn `[n]` trỏ tới mục 7.
- Thông tin không tìm thấy trong nguồn được ghi là "không thấy", không suy ra là "không có". Kết luận ở mục 4 và mục 8 giữ đúng mức chắc chắn này: khoảng trống chưa được nguồn xác nhận trực tiếp là giả thuyết, cần chơi thử để chốt (mục 6).

Giới hạn: đây là khảo sát trên trang công khai trong một ngày, chưa cài và chơi thử từng sản phẩm. Giá có thể đổi theo khu vực và khuyến mãi.

## 2. Sản phẩm được chọn

| # | Sản phẩm | Nhóm | Nền tảng | Vì sao chọn |
|---|---|---|---|---|
| 1 | SongTrivia (songtrivia.io, trước là SongTrivia2.io) | Game đoán nhạc | Web | Đối thủ gần nhất: web, có phòng chơi với bạn realtime [1][2][3] |
| 2 | SongPop | Game đoán nhạc | App iOS/Android | Thương hiệu lâu đời, 100 triệu lượt tải tới 2015 [4][5] |
| 3 | Heardle | Game đoán nhạc | Web | Mẫu "thử thách ngày" kiểu Wordle, mở dần đoạn nhạc; Spotify mua rồi đóng [6][7] |
| 4 | Đoán Bài Hát (musci.io) | Game đoán nhạc | Web | Có giao diện tiếng Việt, đối thủ gần với người dùng Việt [8] |
| 5 | Kahoot! | Quiz chung | Web, app | Công cụ quiz nhóm phổ biến ở lớp học và team building [9] |
| 6 | Spotify Music Quiz | App nghe nhạc có mini-game | App Spotify | Nền tảng streaming lớn vừa mở rộng mini-game đoán nhạc (10/2026) [10] |

Ghi chú từng sản phẩm:
- **SongTrivia:** chủ nhà tạo phòng, nhận link hoặc mã để mời bạn, điểm và câu trả lời hiện realtime, có chế độ chat chữ [3]. Có quiz hằng ngày ("Every day, a fresh quiz awaits") [2] và hơn 200 quiz miễn phí [11]. Có gói Premium theo tháng hoặc năm và mua trong ứng dụng, nhưng trang không ghi giá [12].
- **SongPop:** có đấu trực tiếp 1-1 với người chơi toàn cầu, phòng riêng với bạn bè, chế độ lượt chậm (async) và giải đấu; hơn 100.000 đoạn nhạc có bản quyền [5]. Trả lời bằng cách chọn 1 trong 4 đáp án [4].
- **Heardle:** mỗi lượt có 6 đoạn dài dần từ 1 đến 16 giây [7]. Spotify mua tháng 7/2022 [7] và cho ngừng hoạt động từ 5/5/2023 để "tập trung vào các tính năng khám phá nhạc khác" [6].
- **Đoán Bài Hát (musci.io):** trang chỉ mô tả chơi một mình, 10 câu mỗi lượt. Mỗi câu phát một đoạn 30 giây, chọn 1 trong 4 tên bài trong 15 giây. Không cần tài khoản, "không giới hạn theo ngày" [8].
- **Kahoot!:** quiz trực tiếp theo phiên. Người dùng tự soạn hoặc chơi quiz công khai có sẵn [9]. Danh mục nhạc có quiz miễn phí về nhạc pop, lý thuyết âm nhạc, nhạc cổ điển, world music, K-pop; trong danh mục không thấy quiz nhạc Việt [14]. Từ 24/9/2026 có bộ quiz nhạc cổ điển miễn phí làm cùng Apple Music Classical, trong đó có quiz luyện nghe [15]. Gói miễn phí tối đa 10 người mỗi phiên [9].
- **Spotify Music Quiz:** chỉ dành cho tài khoản Premium từ 18 tuổi, ở 11 quốc gia ban đầu (không có Việt Nam), sau đó mở thêm các nước nói tiếng Tây Ban Nha. Quiz bằng tiếng Anh, sau thêm tiếng Tây Ban Nha. Có câu nhận diện bài qua đoạn preview; người chơi chia sẻ điểm và thách bạn bè, không thấy đề cập chơi realtime [10].

## 3. Bảng so sánh

| Tiêu chí | SongTrivia | SongPop | Heardle | Đoán Bài Hát (musci.io) | Kahoot! | Spotify Music Quiz | **Meloo** |
|---|---|---|---|---|---|---|---|
| Ngôn ngữ giao diện | 9 ngôn ngữ, không có tiếng Việt [2] | 17 ngôn ngữ, không có tiếng Việt [5] | Không còn kiểm chứng được vì đã đóng; các nguồn đều viết bằng tiếng Anh [6][13] | Có tiếng Việt [8] | 18 ngôn ngữ, có tiếng Việt [9] | Quiz tiếng Anh, thêm tiếng Tây Ban Nha [10] | Tiếng Việt và tiếng Anh |
| Có nhạc Việt | Không thấy: thể loại và nghệ sĩ nêu ra đều là nhạc quốc tế, K-pop, nhạc Pháp [1][2] | Chưa kiểm chứng [5] | Không thấy: bài do nhà phát triển chọn từ SoundCloud [7] | Không thấy: chỉ nêu nghệ sĩ quốc tế [8] | Không thấy trong danh mục quiz nhạc; có quiz nhạc quốc tế, K-pop, cổ điển [14][15] | Không thấy: chỉ 11 quốc gia, không có Việt Nam [10] | Có: kho nhạc lọc theo quốc gia, ca sĩ, thập niên |
| Nhiều người realtime | Có: phòng mời bằng link hoặc mã [3] | Có: đấu trực tiếp 1-1, phòng riêng [5] | Không thấy [7][13] | Không thấy; trang chỉ mô tả chơi một mình [8] | Có: tối đa 10 người (miễn phí) [9] | Không thấy; chỉ thách bạn bè qua điểm [10] | Có: phòng 2–5 người, mã 6 ký tự hoặc link (BR-11) |
| Voice | Không thấy; có chat chữ [3] | Không thấy; có nhắn tin và chat [5] | Không thấy [7] | Không thấy [8] | Không thấy [9] | Không thấy [10] | Có trong phòng riêng, mic mặc định tắt (BR-18) · ưu tiên Should |
| Thử thách ngày | Có quiz hằng ngày [2] | Không thấy [5] | Có: "song of the day" [13] | Không thấy; trang ghi "không giới hạn theo ngày" [8] | Không thấy [9] | Không thấy [10] | Có: một bài chung mỗi ngày, đổi lúc 00:00 giờ Việt Nam (BR-25) · ưu tiên Should |
| Giá | Miễn phí; có Premium tháng/năm, trang không ghi giá [1][12] | Miễn phí kèm mua trong app; SongPop Plus 9,99 USD (App Store Mỹ) [5] | Miễn phí; đã đóng cửa từ 5/5/2023 [6] | Miễn phí, không cần tài khoản [8] | Go miễn phí (≤ 10 người); Kahoot!+ Gold 12,99 USD/tháng khi trả theo năm (156 USD/năm, giá khuyến mãi) hoặc 24,99 USD/tháng [9] | Chỉ cho Spotify Premium [10] | Miễn phí trong thời gian môn học; freemium ở Phase 2 theo tài liệu kế hoạch (BIZ-08) `[HỎI THÁI: giữ cách ghi này hay chờ BIZ-08 chốt giá]` |
| Cách đoán | Trả lời nhanh để được nhiều điểm; Autocomplete mode: gõ chữ, có gợi ý, tìm đúng tên bài và nghệ sĩ [1][3] | Chọn 1 trong 4, nhanh hơn được nhiều điểm [4] | Gõ tên bài, đoạn mở dần 1→16 giây [7] | Chọn 1 trong 4 trong 15 giây [8] | Chọn đáp án; quiz tự soạn hoặc có sẵn, có quiz luyện nghe [9][15] | Câu đố nhiều dạng, có nhận diện bài [10] | Tìm tên bài trên toàn kho (không dấu); đoạn mở dần 2→5→10→20→30 giây; điểm theo mức (BR-01, BR-02, BR-05) |

Dòng "Cách đoán" không có trong yêu cầu ticket; thêm vào vì đây là nơi Meloo khác rõ nhất về luật chơi.

Cột Meloo lấy từ tài liệu kế hoạch dự án (phạm vi sản phẩm và BR-01…BR-29), chưa phải sản phẩm đã chạy. Voice (S5) và thử thách ngày (S4) là ưu tiên Should, có thể bị cắt nếu trễ.

## 4. Ba điểm khác biệt của Meloo

Ba điểm dưới đây là khoảng trống quan sát được trong các nguồn công khai đã đọc. Đây là giả thuyết khác biệt, chưa phải kết luận chắc chắn: phần lớn ô so sánh là "không thấy", chưa phải "không có". Việc cần làm để chốt nằm ở mục 6.

### 4.1. Đoán nhạc Việt, bằng tiếng Việt
Trong nguồn của 6 sản phẩm, chưa thấy sản phẩm nào vừa có giao diện tiếng Việt vừa có nhạc Việt:
- Kahoot! và musci.io có giao diện tiếng Việt [8][9], nhưng nguồn không thấy nhạc Việt: danh mục quiz nhạc của Kahoot! có pop, cổ điển, K-pop… [14]; musci.io chỉ nêu thể loại và nghệ sĩ quốc tế [8].
- SongTrivia, SongPop và Spotify Music Quiz không có tiếng Việt trong danh sách ngôn ngữ [2][5][10]. SongPop bị loại ở tiêu chí này dù chưa kiểm chứng được có nhạc Việt hay không. Heardle đã đóng nên không còn kiểm chứng được [6].

Meloo nhắm làm cả hai: giao diện vi/en, kho nhạc lọc theo quốc gia và ca sĩ, tìm tên bài không dấu (BR-05). Điểm này là bằng chứng sơ bộ cho giả thuyết 2 của BIZ-02 ("Game đoán nhạc phổ biến chủ yếu tiếng Anh, ít nhạc Việt, không có giao diện tiếng Việt").

### 4.2. Phòng của nhóm bạn: vào bằng link, chơi realtime, nói chuyện ngay trong trang
- Kahoot! chạy theo phiên do một người làm host; bản miễn phí giới hạn 10 người [9].
- SongPop bản hiện tại được phát hành dưới dạng app, chế độ realtime được mô tả là đấu 1-1 [5]; nguồn không thấy bản web.
- SongTrivia có phòng realtime và chat chữ; nguồn không thấy voice [3].

Nếu các ô "không thấy voice" được xác nhận khi chơi thử, Meloo khác ở chỗ gom đủ trong một trang web: mã phòng 6 ký tự hoặc link (BR-11), chơi không cần tài khoản (BR-20), chat chữ chặn lộ đáp án (BR-16), và voice trong phòng riêng (BR-18, BR-19). Nhóm bạn không phải ghép một game nước ngoài với Discord như giả thuyết 1 của BIZ-02.

### 4.3. Luật "nghe càng ít, điểm càng cao" cho cả chơi đơn lẫn nhiều người
- Heardle có đoạn nhạc mở dần nhưng chỉ chơi một mình, mỗi ngày một bài, và đã đóng cửa [6][7][13].
- SongPop và musci.io phát đoạn nhạc rồi cho chọn 1 trong 4, tính điểm theo tốc độ [4][8].
- SongTrivia có chế độ trả lời nhanh, và có Autocomplete mode: gõ chữ, có gợi ý để tìm đúng tên bài và nghệ sĩ [3]. Vì vậy cách nhập đáp án bằng tìm kiếm không phải khác biệt riêng của Meloo so với SongTrivia.
- Trong nguồn của SongTrivia, SongPop, musci.io và Kahoot!, không thấy cơ chế mở dần đoạn nhạc theo mức điểm [3][5][8][9].

Meloo đưa luật mở dần 2 → 5 → 10 → 20 → 30 giây vào cả phòng nhiều người: điểm theo mức nghe (1.000 → 200), thêm thưởng tốc độ và combo (BR-01, BR-02, BR-07, BR-08). Đoán sai và bấm "Nghe thêm" đều xuống một mức (BR-04), và đáp án phải tìm trên toàn kho (BR-05) thay vì chọn 1 trong 4 như SongPop, musci.io. Nhờ vậy người chơi khó đoán bừa, và người nhận ra bài sớm thắng rõ ràng. Khác biệt chính so với SongTrivia là mở dần theo mức nghe, không phải cách nhập đáp án.

## 5. Điểm Meloo đang kém đối thủ

Cần nói thật khi bảo vệ:
- **Kho nhạc nhỏ và phụ thuộc API:** SongPop có hơn 100.000 đoạn nhạc có bản quyền [5]. Meloo chỉ dùng preview 30 giây từ API nhạc và không phát cả bài (phạm vi Won't).
- **Chưa có app di động:** SongPop, Kahoot! và Spotify đều có app. Meloo chỉ có web responsive, PWA là Should.
- **Ít nội dung:** Kahoot! có kho quiz công khai sẵn để chơi, gồm cả quiz nhạc [9][14]; SongTrivia có hơn 200 quiz miễn phí [11]. Meloo chỉ có bộ đề tuyển chọn.
- **Đối thủ lớn có thể vào thị trường:** Spotify vừa mở Music Quiz lên hơn 2.000 nghệ sĩ và hứa thêm ngôn ngữ và thị trường [10].

## 6. Bài học rút ra

- **Thử thách ngày giữ chân nhưng không đủ làm sản phẩm.** Heardle được Spotify mua nhưng vẫn bị đóng sau khoảng 10 tháng [6][7]. Với Meloo, thử thách ngày là tính năng phụ (Should), lõi là phòng chơi nhóm.
- **Bản miễn phí phải đủ cho một nhóm bạn.** Kahoot! giới hạn 10 người ở gói miễn phí [9]. Phòng 2–5 người của Meloo nằm gọn trong mức miễn phí. Ý tưởng "phòng tới 10 người" của gói Premium (BIZ-08) cần câu hỏi sẵn lòng trả phí trong khảo sát BIZ-03 để kiểm chứng.
- **Khác biệt dễ bị chép.** Từng điểm ở mục 4 đều có đối thủ làm được một phần. Lợi thế của Meloo là gộp cả ba cho người dùng Việt; cần số liệu khảo sát và beta để chứng minh nhóm này có thật. `[CẦN SỐ LIỆU: tỷ lệ người trả lời khảo sát BIZ-03 từng dùng Discord hoặc gọi video kèm game khi chơi với bạn]`
- **Cần chơi thử để chốt khác biệt.** Các ô "không thấy" quan trọng nhất cần kiểm tra trực tiếp trước khi bảo vệ: SongTrivia có voice và có danh sách nhạc Việt không; SongPop có playlist nhạc Việt không; tìm "nhạc Việt" trong Discover của Kahoot!; musci.io có chơi nhiều người không. `[HỎI THÁI: ai chơi thử và ghi kết quả vào bảng mục 3, hạn trước buổi bảo vệ]`

## 7. Nguồn

Ngày truy cập mọi nguồn: 19/10/2026.

1. AlternativeTo, "SongTrivia2.io": https://alternativeto.net/software/songtrivia2-io/about (trang cập nhật 10/6/2020; dùng cho mô tả gốc "play in real time with your friends or against strangers", miễn phí)
2. SongTrivia, "About": https://songtrivia.io/about (danh sách ngôn ngữ, quiz hằng ngày, thể loại)
3. SongTrivia, "Play with friends": https://songtrivia.io/challenge-friends (phòng, link/mã, chat realtime)
4. Wikipedia, "SongPop": https://en.wikipedia.org/wiki/SongPop (lịch sử, cách chơi, 100 triệu lượt tải tới 2015)
5. App Store (Mỹ), "SongPop® - Guess The Song": https://apps.apple.com/us/app/-/id1528066727 (chế độ chơi, 17 ngôn ngữ, giá mua trong app, nhãn "Messaging and Chat")
6. BGR, "Spotify Is Shutting Down Music Guessing Game Heardle A Year After Acquiring It", 14/4/2023: https://bgr.com/tech/spotify-is-shutting-down-music-guessing-game-heardle-a-year-after-acquiring-it/
7. Wikipedia, "Heardle": https://en.wikipedia.org/wiki/Heardle (cách chơi 1–16 giây, Omakase Studios, Spotify mua 7/2022)
8. musci.io, "Đoán Bài Hát": https://musci.io/vi/guess-the-song
9. Kahoot!, trang chủ và bảng gói cá nhân: https://kahoot.com/home/ (gói Go và Kahoot!+ Gold, danh sách ngôn ngữ; giá có khuyến mãi tới 25/10/2026)
10. Spotify Newsroom, "Music Quiz expansion", 6/10/2026 (cập nhật 15/10/2026): https://newsroom.spotify.com/2026-10-06/music-quiz-expansion/
11. SongTrivia, trang chủ: https://songtrivia.io/ ("200+ free quizzes", "daily quizzes")
12. SongTrivia, "Terms of sale": https://songtrivia.io/terms-of-sale (Premium tháng/năm, gói xu, mở khóa chủ đề)
13. Exclaim!, "Heardle Is Getting Shut Down", 14/4/2023: https://exclaim.ca/music/article/heardle_is_getting_shut_down ("guess the song of the day by listening to short snippets")
14. Kahoot!, danh mục "The world of music": https://kahoot.com/discover/en/the-world-of-music (quiz nhạc có sẵn, đa số ghi "Free")
15. Kahoot! Blog, "Apple Music Classical on Kahoot!", 24/9/2026: https://kahoot.com/blog/2026/09/24/apple-music-classical-on-kahoot/ ("free, ready-to-play kahoots", quiz luyện nghe Mozart)

Nguồn của cột Meloo: tài liệu "Meloo — Kế hoạch dự án" của Thái, mục "Phạm vi sản phẩm" và "Luật chơi & quy tắc nghiệp vụ" (BR-01…BR-29). Tài liệu này nằm ngoài repo; các mã BR sẽ kiểm lại được khi `docs/srs/02-business-rules.md` được đưa vào ở ticket SRS.

## 8. Câu hỏi bảo vệ

1. **Đã có SongTrivia chơi realtime với bạn, sao còn làm Meloo?**
   Danh sách ngôn ngữ của SongTrivia không có tiếng Việt, và danh mục thể loại không thấy nhạc Việt [2]. Trang chơi với bạn chỉ mô tả chat chữ, không thấy voice [3]. Meloo nhắm vào nhóm bạn người Việt nghe nhạc Việt; điểm voice cần chơi thử SongTrivia để xác nhận (mục 6).
2. **Kahoot! có tiếng Việt và có sẵn quiz nhạc, sao không dùng Kahoot!?**
   Kahoot! có quiz nhạc chơi sẵn, kể cả quiz luyện nghe [14][15]. Nhưng trong danh mục nhạc không thấy quiz nhạc Việt [14], và nguồn không thấy cơ chế mở dần đoạn nhạc theo mức điểm [9]. Meloo khác ở kho nhạc Việt lọc theo ca sĩ, thập niên và luật nghe càng ít điểm càng cao (BR-01, BR-02).
3. **Spotify đã có Music Quiz, Meloo cạnh tranh thế nào?**
   Music Quiz cần Premium, tuổi từ 18, và Việt Nam chưa có trong danh sách quốc gia [10]. Nguồn chỉ mô tả chia sẻ điểm và thách bạn bè, không thấy chơi realtime [10]. Nếu Spotify mở rộng, Meloo vẫn khác ở phòng chơi nhóm và luật mở dần. Đây cũng là rủi ro được ghi ở mục 5.
4. **Heardle nổi tiếng mà vẫn đóng cửa, Meloo có lặp lại không?**
   Heardle chỉ có một bài mỗi ngày và chơi một mình [13]. Spotify nêu lý do đóng là để tập trung vào các tính năng khám phá nhạc khác [6]. Meloo lấy phòng chơi nhóm làm lõi; thử thách ngày chỉ là tính năng phụ.
5. **Số liệu nào chứng minh người dùng cần ba điểm khác biệt này?**
   Bảng so sánh chỉ cho thấy khoảng trống trong các nguồn công khai đã đọc, chưa chứng minh thị trường còn trống. Nhu cầu phải chứng minh bằng khảo sát BIZ-03 (≥ 50 phản hồi) và số liệu beta (≥ 30 tài khoản, ≥ 100 trận).
