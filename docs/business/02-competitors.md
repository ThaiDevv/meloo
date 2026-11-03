# Phân tích đối thủ và điểm khác biệt

> Ticket: BIZ-04 (MEL-20), BIZ-09 (MEL-117) · Trạng thái: hoàn thiện theo BIZ-09, chờ Thái duyệt (bản BIZ-04 Thái đã duyệt ngày 19/10/2026) · Ngày truy cập và kiểm chứng nguồn: 19/10/2026 – 02/11/2026
> Liên quan: BIZ-02 (`01-problem.md`, giả thuyết vấn đề 1–3), BIZ-03 (`03-survey.md`), BIZ-08 (kế hoạch khởi nghiệp)

## 1. Mục tiêu và cách làm

Tài liệu trả lời câu hỏi: "Đã có game đoán nhạc rồi, Meloo khác gì?".

Cách làm:
- Chọn 6 sản phẩm thuộc 3 nhóm: game đoán nhạc (web và app), quiz chung, ứng dụng nghe nhạc có mini-game.
- So sánh theo 6 tiêu chí ticket yêu cầu: ngôn ngữ giao diện, có nhạc Việt, chơi nhiều người realtime, voice, thử thách ngày, giá.
- Sử dụng thông tin công khai từ trang chính thức, kho ứng dụng hoặc báo công nghệ, kết hợp với kết quả chơi thử trực tiếp của Thái [16] và kiểm chứng tính năng. Mỗi ô có số nguồn `[n]` trỏ tới mục 7.
- Thông tin không tìm thấy trong nguồn công khai hoặc chưa được kiểm chứng trực tiếp được ghi là "không thấy", không suy ra là "không có". Kết luận ở mục 4 và mục 8 giữ đúng mức chắc chắn này: khoảng trống chưa được xác nhận trực tiếp là giả thuyết cần kiểm chứng.

Giới hạn: Trong 6 sản phẩm, Thái đã chơi thử SongTrivia, SongPop và Kahoot! ngày 19/10/2026 [16], và web musci.io được kiểm chứng trực tiếp ngày 02/11/2026 [16]. Heardle đã đóng cửa (chỉ đọc tài liệu lưu trữ) và Spotify Music Quiz chưa mở tại Việt Nam (chỉ đọc thông cáo báo chí). Giá và tính năng có thể thay đổi theo khu vực, thời gian và khuyến mãi.

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
- **SongTrivia:** chủ nhà tạo phòng, nhận link hoặc mã để mời bạn, điểm và câu trả lời hiện realtime, có chế độ chat chữ [3]. Chơi thử: không có voice, không có nhạc Việt [16]. Có quiz hằng ngày ("Every day, a fresh quiz awaits") [2] và hơn 200 quiz miễn phí [11]. Có gói Premium theo tháng hoặc năm và mua trong ứng dụng, nhưng trang không ghi giá [12].
- **SongPop:** có đấu trực tiếp 1-1 với người chơi toàn cầu, phòng riêng với bạn bè, chế độ lượt chậm (async) và giải đấu; hơn 100.000 đoạn nhạc có bản quyền [5]. Trả lời bằng cách chọn 1 trong 4 đáp án [4]. Chơi thử: không có playlist nhạc Việt [16]; không thấy voice chat (chỉ thấy nhắn tin và chat trong app [5]). Không thấy bản web (chỉ thấy app iOS/Android [5]).
- **Heardle:** mỗi lượt có 6 đoạn dài dần từ 1 đến 16 giây [7]. Spotify mua tháng 7/2022 [7] và cho ngừng hoạt động từ 5/5/2023 để "tập trung vào các tính năng khám phá nhạc khác" [6].
- **Đoán Bài Hát (musci.io):** trang chỉ mô tả và cung cấp chế độ chơi một mình (1 người), 10 câu mỗi lượt, không thấy chế độ nhiều người/phòng [8][16]. Mỗi câu phát một đoạn 30 giây, chọn 1 trong 4 tên bài trong 15 giây. Không cần tài khoản, "không giới hạn theo ngày", không thấy voice và không thấy nhạc Việt [8][16].
- **Kahoot!:** quiz trực tiếp theo phiên. Người dùng tự soạn hoặc chơi quiz công khai có sẵn [9]. Danh mục nhạc có quiz miễn phí về nhạc pop, lý thuyết âm nhạc, nhạc cổ điển, world music, K-pop; danh mục này không liệt kê quiz nhạc Việt [14], nhưng khi tìm trực tiếp trong Discover thì có các quiz nhạc Việt do cộng đồng tạo [16]. Không thấy tính năng voice chat tích hợp trên web/app (người chơi thường gọi ngoài qua Zoom/Discord) [9][16]. Từ 24/9/2026 có bộ quiz nhạc cổ điển miễn phí làm cùng Apple Music Classical, trong đó có quiz luyện nghe [15]. Gói miễn phí tối đa 10 người mỗi phiên [9].
- **Spotify Music Quiz:** chỉ dành cho tài khoản Premium từ 18 tuổi, ở 11 quốc gia ban đầu (không có Việt Nam), sau đó mở thêm các nước nói tiếng Tây Ban Nha. Quiz bằng tiếng Anh, sau thêm tiếng Tây Ban Nha. Có câu nhận diện bài qua đoạn preview; người chơi chia sẻ điểm và thách bạn bè, không thấy đề cập chơi realtime [10].

## 3. Bảng so sánh

| Tiêu chí | SongTrivia | SongPop | Heardle | Đoán Bài Hát (musci.io) | Kahoot! | Spotify Music Quiz | **Meloo** |
|---|---|---|---|---|---|---|---|
| Ngôn ngữ giao diện | 9 ngôn ngữ, không có tiếng Việt [2] | 17 ngôn ngữ, không có tiếng Việt [5] | Không còn kiểm chứng được vì đã đóng; các nguồn đều viết bằng tiếng Anh [6][13] | Có tiếng Việt [8] | 18 ngôn ngữ, có tiếng Việt [9] | Quiz tiếng Anh, thêm tiếng Tây Ban Nha [10] | Tiếng Việt và tiếng Anh |
| Có nhạc Việt | Không, theo chơi thử [16]; thể loại và nghệ sĩ nêu ra đều là nhạc quốc tế, K-pop, nhạc Pháp [1][2] | Không có playlist nhạc Việt, theo chơi thử [16] | Không thấy: bài do nhà phát triển chọn từ SoundCloud [7] | Không thấy: chỉ nêu nghệ sĩ và bài hát quốc tế [8][16] | Có quiz nhạc Việt do user tạo, theo chơi thử [16]; danh mục chính thức có pop, cổ điển, K-pop… [14][15] | Không thấy: chỉ 11 quốc gia, không có Việt Nam [10] | Có: kho nhạc lọc theo quốc gia, ca sĩ, thập niên |
| Nhiều người realtime | Có: phòng mời bằng link hoặc mã [3] | Có: đấu trực tiếp 1-1, phòng riêng trên app [5] | Không thấy [7][13] | Không thấy: chỉ chơi đơn (1 người) [8][16] | Có: tối đa 10 người (miễn phí) [9] | Không thấy; chỉ thách bạn bè qua điểm [10] | Có: phòng 2–5 người, mã 6 ký tự hoặc link (BR-11) |
| Voice | Không, theo chơi thử [16]; có chat chữ [3] | Không thấy voice; chỉ có nhắn tin và chat chữ trong app [5][16] | Không thấy [7] | Không thấy [8][16] | Không thấy voice tích hợp (dùng Zoom/Discord ngoài) [9][16] | Không thấy [10] | Có trong phòng riêng, mic mặc định tắt (BR-18) · ưu tiên Should |
| Thử thách ngày | Có quiz hằng ngày [2] | Không thấy [5] | Có: "song of the day" [13] | Không thấy: "không giới hạn theo ngày" [8][16] | Không thấy [9] | Không thấy [10] | Có: một bài chung mỗi ngày, đổi lúc 00:00 giờ Việt Nam (BR-25) · ưu tiên Should |
| Giá | Miễn phí; có Premium tháng/năm, trang không ghi giá [1][12] | Miễn phí kèm mua trong app; SongPop Plus 9,99 USD (App Store Mỹ) [5] | Miễn phí; đã đóng cửa từ 5/5/2023 [6] | Miễn phí, không cần tài khoản [8] | Go miễn phí (≤ 10 người); Kahoot!+ Gold 12,99 USD/tháng khi trả theo năm (156 USD/năm, giá khuyến mãi) hoặc 24,99 USD/tháng [9] | Chỉ cho Spotify Premium [10] | Miễn phí trong thời gian môn học; freemium ở Phase 2 (BIZ-08) |
| Cách đoán | Trả lời nhanh để được nhiều điểm; Autocomplete mode: gõ chữ, có gợi ý, tìm đúng tên bài và nghệ sĩ [1][3] | Chọn 1 trong 4, nhanh hơn được nhiều điểm [4] | Gõ tên bài, đoạn mở dần 1→16 giây [7] | Chọn 1 trong 4 trong 15 giây [8] | Chọn đáp án; quiz tự soạn hoặc có sẵn, có quiz luyện nghe [9][15] | Câu đố nhiều dạng, có nhận diện bài [10] | Tìm tên bài trên toàn kho (không dấu); đoạn mở dần 2→5→10→20→30 giây; điểm theo mức (BR-01, BR-02, BR-05) |

Dòng "Cách đoán" không có trong yêu cầu ticket; thêm vào vì đây là nơi Meloo khác rõ nhất về luật chơi.

Cột Meloo lấy từ tài liệu kế hoạch dự án (phạm vi sản phẩm và BR-01…BR-29), chưa phải sản phẩm đã chạy. Voice (S5) và thử thách ngày (S4) là ưu tiên Should, có thể bị cắt nếu trễ.

## 4. Ba điểm khác biệt của Meloo

Ba điểm dưới đây là khoảng trống thị trường quan sát được từ việc phân tích nguồn công khai và kết quả chơi thử, kiểm chứng đối thủ [16]. Việc rà soát và chơi thử cung cấp bằng chứng sơ bộ để khẳng định ba điểm khác biệt này khi bảo vệ.

### 4.1. Game đoán nhạc Việt, bằng tiếng Việt
Trong các nguồn đã phân tích và lần chơi thử thực tế, chỉ Kahoot! có giao diện tiếng Việt và có nội dung nhạc Việt do người dùng tạo trong Discover [16]. Tuy nhiên, Kahoot! là nền tảng quiz chung, không phải game đoán nhạc chuyên biệt (xem 4.3):
- Kahoot! có giao diện tiếng Việt [9] và có quiz nhạc Việt khi tìm trực tiếp trong Discover [16], nhưng các quiz này do cộng đồng tự soạn riêng lẻ [14][16], không phải hệ thống game đoán nhạc có kho bài hát chuẩn hóa, phân loại ca sĩ/thập niên bài bản và công cụ tìm kiếm bài không dấu (BR-05).
- musci.io có giao diện tiếng Việt [8], nhưng qua kiểm chứng không thấy nhạc Việt [8][16] (chỉ thấy nghệ sĩ và bài hát quốc tế) và chỉ là game chơi một mình.
- SongTrivia và SongPop hoàn toàn không có tiếng Việt trong danh sách ngôn ngữ [2][5]. Chơi thử xác nhận SongTrivia không có nhạc Việt [16], SongPop không có playlist hay bài hát nhạc Việt [16]. Spotify Music Quiz không có tiếng Việt và chưa hỗ trợ thị trường Việt Nam [10]. Heardle đã đóng cửa [6].

Vì vậy điểm khác biệt của Meloo là: game đoán nhạc chuyên biệt phục vụ người Việt với giao diện song ngữ vi/en, kho nhạc V-pop lọc theo quốc gia, ca sĩ, thập niên, và thuật toán tìm tên bài không dấu (BR-05). Điểm này là bằng chứng sơ bộ cho giả thuyết H2 của BIZ-02 ("Game đoán nhạc phổ biến chủ yếu tiếng Anh, ít nhạc Việt, không có giao diện tiếng Việt").

### 4.2. Phòng của nhóm bạn: vào bằng link, chơi realtime, nói chuyện ngay trong trang
- Kahoot! chạy theo phiên do host mở; gói miễn phí giới hạn 10 người [9] và không thấy voice tích hợp (người chơi thường dùng Zoom/Meet/Discord song song) [16].
- SongPop chỉ phát hành dưới dạng app di động; chế độ realtime đấu 1-1 và phòng riêng bạn bè có chat chữ/tin nhắn, không thấy voice chat [5][16].
- SongTrivia có phòng realtime và chat chữ [3], nhưng không có voice chat [16].
- musci.io chỉ hỗ trợ chơi đơn một mình, không thấy chế độ nhiều người chơi hay phòng chơi [8][16].

Meloo là sản phẩm duy nhất gom trọn vẹn trong một trang web: mã phòng 6 ký tự hoặc link (BR-11), chơi ngay không cần tài khoản (BR-20), chat chữ tự chặn lộ đáp án (BR-16), và voice chat tích hợp ngay trong phòng riêng (BR-18, BR-19). Nhóm bạn vào là chơi và trò chuyện trực tiếp được ngay, không phải ghép một game nước ngoài với Discord như giả thuyết H1 của BIZ-02.

### 4.3. Luật "nghe càng ít, điểm càng cao" cho cả chơi đơn lẫn nhiều người
- Heardle có đoạn nhạc mở dần [7] và một bài mỗi ngày [13], nhưng không thấy chế độ nhiều người và đã đóng cửa từ tháng 5/2023 [6].
- SongPop và musci.io phát đoạn nhạc cố định (15–30s) rồi chọn 1 trong 4 đáp án [4][8]; cách chơi trắc nghiệm này dễ bị đoán mò.
- SongTrivia có Autocomplete mode gõ chữ có gợi ý [3], nhưng phát trọn đoạn nhạc cố định, không có cơ chế mở dần đoạn nhạc theo mức điểm [16].
- Kahoot! là quiz trắc nghiệm tĩnh soạn sẵn, không thấy cơ chế mở dần đoạn nhạc theo thời gian [9].

Meloo đưa luật mở dần 2 → 5 → 10 → 20 → 30 giây vào cả phòng nhiều người: điểm theo mức nghe (1.000 → 200), thêm thưởng tốc độ và combo (BR-01, BR-02, BR-07, BR-08). Đoán sai và bấm "Nghe thêm" đều tụt mức điểm (BR-04), và đáp án phải tìm kiếm trên toàn kho bài (BR-05). Cơ chế này loại bỏ hoàn toàn khả năng đoán mò của trắc nghiệm, tưởng thưởng xứng đáng cho người nghe nhận ra bài sớm, và tạo kịch tính cao cho phòng chơi nhóm. Khác biệt chính so với SongTrivia là mở dần theo mức nghe, không phải cách nhập đáp án.

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
- **Kết quả chơi thử và kiểm chứng bước đầu làm rõ các ô nghi vấn:**
  - **SongTrivia:** chơi thử xác nhận không có voice chat, không có nhạc Việt [16].
  - **SongPop:** chơi thử xác nhận không có playlist nhạc Việt [16]; không thấy voice chat (chỉ thấy nhắn tin và chat trong app [5]). Không thấy bản web (chỉ thấy app iOS/Android [5]).
  - **Kahoot!:** chơi thử xác nhận có quiz nhạc Việt do cộng đồng tạo trong Discover [16], nhưng không thấy voice chat tích hợp [9][16] và không thấy cơ chế mở dần đoạn nhạc theo mức điểm [9]. Khi bảo vệ cần nói rõ: Kahoot! có quiz nhạc Việt, khác biệt của Meloo so với Kahoot! nằm ở luật mở dần (mục 4.3) và phòng chơi nhóm tích hợp voice trên web (mục 4.2).
  - **musci.io (Đoán Bài Hát):** kiểm chứng trực tiếp trang web xác nhận chỉ có chế độ chơi một mình (1 người), không thấy chế độ nhiều người/phòng, không thấy voice, không thấy nhạc Việt [8][16].
  Như vậy, các giả thuyết khác biệt ở mục 4 đã có thêm bằng chứng thực tế hỗ trợ, giúp chuẩn bị tốt cho buổi bảo vệ.

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
16. Nhật ký chơi thử và kiểm chứng trực tiếp:
   - Thái chơi thử trực tiếp SongTrivia, SongPop và Kahoot! ngày 19/10/2026: SongTrivia không có voice, không có nhạc Việt; SongPop không có playlist nhạc Việt; Kahoot! tìm thấy quiz nhạc Việt do người dùng tạo trong Discover.
   - Kiểm chứng trực tiếp giao diện và tính năng web musci.io (https://musci.io/vi/guess-the-song) ngày 02/11/2026: xác nhận chỉ có giao diện chơi 1 người (10 câu hỏi, 15 giây/câu, trắc nghiệm 1 trong 4), không thấy tạo phòng hay multiplayer, không thấy voice, bài hát mẫu đều là nghệ sĩ quốc tế.

Nguồn của cột Meloo: tài liệu "Meloo — Kế hoạch dự án" của Thái, mục "Phạm vi sản phẩm" và "Luật chơi & quy tắc nghiệp vụ" (BR-01…BR-29). Tài liệu này nằm ngoài repo; các mã BR sẽ kiểm lại được khi `docs/srs/02-business-rules.md` được đưa vào ở ticket SRS.

## 8. Câu hỏi bảo vệ

1. **Đã có SongTrivia chơi realtime với bạn, sao còn làm Meloo?**
   Danh sách ngôn ngữ của SongTrivia không có tiếng Việt [2]. Khi chơi thử, SongTrivia hoàn toàn không có nhạc Việt và không có voice chat (chỉ có chat chữ) [3][16]. Meloo nhắm vào nhóm bạn người Việt nghe nhạc Việt, có giao diện tiếng Việt và tích hợp voice chat ngay trong web mà không cần cài thêm phần mềm.
2. **Kahoot! có tiếng Việt và có sẵn quiz nhạc, sao không dùng Kahoot!?**
   Kahoot! có quiz nhạc chơi sẵn, kể cả quiz luyện nghe [14][15], và có cả quiz nhạc Việt do cộng đồng tạo trong Discover [16]. Nhưng Kahoot! là nền tảng quiz chung: mỗi quiz là một bộ câu hỏi tĩnh soạn sẵn, không thấy voice tích hợp (phải mở song song Zoom/Discord), và không thấy cơ chế nghe mở dần theo mức điểm [9][16]. Meloo là game đoán nhạc chuyên biệt: bài lấy từ kho nhạc chuẩn hóa lọc theo ca sĩ, thập niên, cấp audio mờ chống gian lận, tính điểm theo mức nghe 5 mức (BR-01, BR-02).
3. **Spotify đã có Music Quiz, Meloo cạnh tranh thế nào?**
   Music Quiz cần Premium, tuổi từ 18, và Việt Nam chưa có trong danh sách quốc gia [10]. Nguồn chỉ mô tả chia sẻ điểm và thách bạn bè, không thấy chơi realtime [10]. Nếu Spotify mở rộng, Meloo vẫn khác ở phòng chơi nhóm và luật mở dần. Đây cũng là rủi ro được ghi ở mục 5.
4. **Heardle nổi tiếng mà vẫn đóng cửa, Meloo có lặp lại không?**
   Heardle có một bài mỗi ngày ("song of the day") [13], và nguồn không thấy chế độ nhiều người [7]. Spotify nêu lý do đóng là để tập trung vào các tính năng khám phá nhạc khác [6]. Meloo lấy phòng chơi nhóm làm lõi; thử thách ngày chỉ là tính năng phụ.
5. **Số liệu nào chứng minh người dùng cần ba điểm khác biệt này?**
   Bảng so sánh và kết quả chơi thử bước đầu chỉ cho thấy khoảng trống tính năng trong các sản phẩm đã khảo sát (bằng chứng sơ bộ). Khoảng trống thị trường và nhu cầu người dùng thực tế phải được chứng minh bằng khảo sát BIZ-03/BIZ-05 (ngưỡng H1–H4 với ≥ 50 phản hồi) và số liệu sử dụng thực tế trong giai đoạn beta (≥ 30 tài khoản, ≥ 100 trận, tỷ lệ quay lại ≥ 30%).
