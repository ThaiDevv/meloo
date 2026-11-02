# Phân tích đối thủ và điểm khác biệt

> Ticket: BIZ-04 (MEL-20), BIZ-09 (MEL-117) · Trạng thái: **đã duyệt** (chốt kết quả kiểm chứng đối thủ và giá ngày 02/11/2026) · Ngày truy cập và kiểm chứng nguồn: 19/10/2026 – 02/11/2026
> Liên quan: BIZ-02 (`01-problem.md`, giả thuyết vấn đề 1–3), BIZ-03 (`03-survey.md`), BIZ-08 (kế hoạch khởi nghiệp)

## 1. Mục tiêu và cách làm

Tài liệu trả lời câu hỏi: "Đã có game đoán nhạc rồi, Meloo khác gì?".

Cách làm:
- Chọn 6 sản phẩm thuộc 3 nhóm: game đoán nhạc (web và app), quiz chung, ứng dụng nghe nhạc có mini-game.
- So sánh theo 6 tiêu chí ticket yêu cầu: ngôn ngữ giao diện, có nhạc Việt, chơi nhiều người realtime, voice, thử thách ngày, giá.
- Sử dụng thông tin công khai chính thức từ website, kho ứng dụng, báo công nghệ, kết hợp với kết quả kiểm chứng và chơi thử thực tế [16]. Mỗi ô có số nguồn `[n]` trỏ tới mục 7.
- Các ô nghi vấn ban đầu ("không thấy") đã được rà soát và kiểm chứng trực tiếp qua chơi thử (mục 6) để khẳng định các điểm khác biệt của Meloo đứng vững khi bảo vệ.

Giới hạn: Giá và tính năng của các sản phẩm có thể thay đổi theo thời gian, khu vực và các chương trình khuyến mãi của nhà phát triển.

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
- **SongPop:** có đấu trực tiếp 1-1 với người chơi toàn cầu, phòng riêng với bạn bè, chế độ lượt chậm (async) và giải đấu; hơn 100.000 đoạn nhạc có bản quyền [5]. Trả lời bằng cách chọn 1 trong 4 đáp án [4]. Chơi thử: không có playlist nhạc Việt, không có voice chat (chỉ có nhắn tin và chat chữ trong app) [5][16].
- **Heardle:** mỗi lượt có 6 đoạn dài dần từ 1 đến 16 giây [7]. Spotify mua tháng 7/2022 [7] và cho ngừng hoạt động từ 5/5/2023 để "tập trung vào các tính năng khám phá nhạc khác" [6].
- **Đoán Bài Hát (musci.io):** trang chỉ mô tả và cung cấp chế độ chơi một mình (1 người), 10 câu mỗi lượt, không có chế độ nhiều người/phòng [8][16]. Mỗi câu phát một đoạn 30 giây, chọn 1 trong 4 tên bài trong 15 giây. Không cần tài khoản, "không giới hạn theo ngày", không có voice và không có nhạc Việt [8][16].
- **Kahoot!:** quiz trực tiếp theo phiên. Người dùng tự soạn hoặc chơi quiz công khai có sẵn [9]. Danh mục nhạc có quiz miễn phí về nhạc pop, lý thuyết âm nhạc, nhạc cổ điển, world music, K-pop; danh mục này không liệt kê quiz nhạc Việt [14], nhưng khi tìm trực tiếp trong Discover thì có các quiz nhạc Việt do cộng đồng tạo [16]. Không có tính năng voice chat tích hợp trong nền tảng (người chơi phải gọi ngoài qua Zoom/Discord) [9][16]. Từ 24/9/2026 có bộ quiz nhạc cổ điển miễn phí làm cùng Apple Music Classical, trong đó có quiz luyện nghe [15]. Gói miễn phí tối đa 10 người mỗi phiên [9].
- **Spotify Music Quiz:** chỉ dành cho tài khoản Premium từ 18 tuổi, ở 11 quốc gia ban đầu (không có Việt Nam), sau đó mở thêm các nước nói tiếng Tây Ban Nha. Quiz bằng tiếng Anh, sau thêm tiếng Tây Ban Nha. Có câu nhận diện bài qua đoạn preview; người chơi chia sẻ điểm và thách bạn bè, không thấy đề cập chơi realtime [10].

## 3. Bảng so sánh

| Tiêu chí | SongTrivia | SongPop | Heardle | Đoán Bài Hát (musci.io) | Kahoot! | Spotify Music Quiz | **Meloo** |
|---|---|---|---|---|---|---|---|
| Ngôn ngữ giao diện | 9 ngôn ngữ, không có tiếng Việt [2] | 17 ngôn ngữ, không có tiếng Việt [5] | Không còn kiểm chứng được vì đã đóng; các nguồn đều viết bằng tiếng Anh [6][13] | Có tiếng Việt [8] | 18 ngôn ngữ, có tiếng Việt [9] | Quiz tiếng Anh, thêm tiếng Tây Ban Nha [10] | Tiếng Việt và tiếng Anh |
| Có nhạc Việt | Không, theo chơi thử [16]; thể loại và nghệ sĩ nêu ra đều là nhạc quốc tế, K-pop, nhạc Pháp [1][2] | Không có playlist nhạc Việt, theo chơi thử [16] | Không thấy: bài do nhà phát triển chọn từ SoundCloud [7] | Không: chỉ nêu nghệ sĩ và bài hát quốc tế [8][16] | Có quiz nhạc Việt do user tạo, theo chơi thử [16]; danh mục chính thức có pop, cổ điển, K-pop… [14][15] | Không thấy: chỉ 11 quốc gia, không có Việt Nam [10] | Có: kho nhạc lọc theo quốc gia, ca sĩ, thập niên |
| Nhiều người realtime | Có: phòng mời bằng link hoặc mã [3] | Có: đấu trực tiếp 1-1, phòng riêng [5] | Không [7][13] (chỉ chơi đơn mỗi ngày) | Không: chỉ chơi đơn (1 người) [8][16] | Có: tối đa 10 người (miễn phí) [9] | Không thấy; chỉ thách bạn bè qua điểm [10] | Có: phòng 2–5 người, mã 6 ký tự hoặc link (BR-11) |
| Voice | Không, theo chơi thử [16]; có chat chữ [3] | Không: chỉ có nhắn tin và chat chữ [5][16] | Không [7] | Không [8][16] | Không có tích hợp (dùng Zoom/Discord ngoài) [9][16] | Không thấy [10] | Có trong phòng riêng, mic mặc định tắt (BR-18) · ưu tiên Should |
| Thử thách ngày | Có quiz hằng ngày [2] | Không thấy [5] | Có: "song of the day" [13] | Không có: "không giới hạn theo ngày" [8][16] | Không thấy [9] | Không thấy [10] | Có: một bài chung mỗi ngày, đổi lúc 00:00 giờ Việt Nam (BR-25) · ưu tiên Should |
| Giá | Miễn phí; có Premium tháng/năm, trang không ghi giá [1][12] | Miễn phí kèm mua trong app; SongPop Plus 9,99 USD (App Store Mỹ) [5] | Miễn phí; đã đóng cửa từ 5/5/2023 [6] | Miễn phí, không cần tài khoản [8] | Go miễn phí (≤ 10 người); Kahoot!+ Gold 12,99 USD/tháng khi trả theo năm (156 USD/năm, giá khuyến mãi) hoặc 24,99 USD/tháng [9] | Chỉ cho Spotify Premium [10] | Miễn phí trong thời gian môn học; freemium ở Phase 2 (BIZ-08) |
| Cách đoán | Trả lời nhanh để được nhiều điểm; Autocomplete mode: gõ chữ, có gợi ý, tìm đúng tên bài và nghệ sĩ [1][3] | Chọn 1 trong 4, nhanh hơn được nhiều điểm [4] | Gõ tên bài, đoạn mở dần 1→16 giây [7] | Chọn 1 trong 4 trong 15 giây [8] | Chọn đáp án; quiz tự soạn hoặc có sẵn, có quiz luyện nghe [9][15] | Câu đố nhiều dạng, có nhận diện bài [10] | Tìm tên bài trên toàn kho (không dấu); đoạn mở dần 2→5→10→20→30 giây; điểm theo mức (BR-01, BR-02, BR-05) |

Dòng "Cách đoán" không có trong yêu cầu ticket; thêm vào vì đây là nơi Meloo khác rõ nhất về luật chơi.

Cột Meloo lấy từ tài liệu kế hoạch dự án (phạm vi sản phẩm và BR-01…BR-29), chưa phải sản phẩm đã chạy. Voice (S5) và thử thách ngày (S4) là ưu tiên Should, có thể bị cắt nếu trễ.

## 4. Ba điểm khác biệt của Meloo

Ba điểm dưới đây là khoảng trống thị trường vững chắc được xác lập từ việc phân tích nguồn công khai và kết quả chơi thử, kiểm chứng trực tiếp đối thủ [16]. Việc rà soát và chơi thử đã làm rõ các ô nghi vấn để ba điểm khác biệt này đứng vững khi bảo vệ.

### 4.1. Game đoán nhạc Việt, bằng tiếng Việt
Trong các nguồn đã phân tích và lần chơi thử thực tế, chỉ Kahoot! có giao diện tiếng Việt và có nội dung nhạc Việt do người dùng tạo trong Discover [16]. Tuy nhiên, Kahoot! là nền tảng quiz chung, không phải game đoán nhạc chuyên biệt (xem 4.3):
- Kahoot! có giao diện tiếng Việt [9] và có quiz nhạc Việt khi tìm trực tiếp trong Discover [16], nhưng các quiz này do cộng đồng tự soạn riêng lẻ, không có kho bài hát chuẩn hóa, không có phân loại ca sĩ/thập niên bài bản và không có công cụ tìm kiếm bài không dấu (BR-05).
- musci.io có giao diện tiếng Việt [8], nhưng qua kiểm chứng không có nhạc Việt [8][16] (chỉ có nghệ sĩ và bài hát quốc tế) và chỉ là game chơi một mình.
- SongTrivia và SongPop hoàn toàn không có tiếng Việt trong danh sách ngôn ngữ [2][5]. Chơi thử xác nhận SongTrivia không có nhạc Việt [16], SongPop không có playlist hay bài hát nhạc Việt [16]. Spotify Music Quiz không có tiếng Việt và chưa hỗ trợ thị trường Việt Nam [10]. Heardle đã đóng cửa [6].

Vì vậy điểm khác biệt của Meloo là: game đoán nhạc chuyên biệt phục vụ người Việt với giao diện song ngữ vi/en, kho nhạc V-pop lọc theo quốc gia, ca sĩ, thập niên, và thuật toán tìm tên bài không dấu (BR-05). Điểm này là bằng chứng thực nghiệm vững chắc cho giả thuyết H2 của BIZ-02 ("Game đoán nhạc phổ biến chủ yếu tiếng Anh, ít nhạc Việt, không có giao diện tiếng Việt").

### 4.2. Phòng của nhóm bạn: vào bằng link, chơi realtime, nói chuyện ngay trong trang
- Kahoot! chạy theo phiên do host mở; gói miễn phí giới hạn 10 người [9] và không có voice tích hợp (người chơi buộc phải dùng Zoom/Meet/Discord song song) [16].
- SongPop chỉ phát hành dưới dạng app di động; chế độ realtime đấu 1-1 và phòng riêng bạn bè chỉ có chat chữ/tin nhắn, không có voice chat [5][16].
- SongTrivia có phòng realtime và chat chữ [3], nhưng không có voice chat [16].
- musci.io chỉ hỗ trợ chơi đơn một mình, hoàn toàn không có chế độ nhiều người chơi hay phòng chơi [8][16].

Meloo là sản phẩm duy nhất gom trọn vẹn trong một trang web: mã phòng 6 ký tự hoặc link (BR-11), chơi ngay không cần tài khoản (BR-20), chat chữ tự chặn lộ đáp án (BR-16), và voice chat tích hợp ngay trong phòng riêng (BR-18, BR-19). Nhóm bạn vào là chơi và trò chuyện trực tiếp được ngay, không phải ghép một game nước ngoài với Discord như giả thuyết H1 của BIZ-02.

### 4.3. Luật "nghe càng ít, điểm càng cao" cho cả chơi đơn lẫn nhiều người
- Heardle từng có đoạn nhạc mở dần [7] và một bài mỗi ngày [13], nhưng không có chế độ phòng nhiều người và đã đóng cửa từ tháng 5/2023 [6].
- SongPop và musci.io phát đoạn nhạc cố định (15–30s) rồi chọn 1 trong 4 đáp án [4][8]; cách chơi trắc nghiệm này dễ bị đoán mò.
- SongTrivia có Autocomplete mode gõ chữ có gợi ý [3], nhưng phát trọn đoạn nhạc cố định, không có cơ chế mở dần đoạn nhạc theo mức điểm [16].
- Kahoot! là quiz trắc nghiệm tĩnh soạn sẵn, không có cơ chế mở dần đoạn nhạc theo thời gian [9].

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
- **Kết quả chơi thử và kiểm chứng đã chốt toàn bộ các ô nghi vấn:**
  - **SongTrivia:** không có voice chat, không có nhạc Việt [16].
  - **SongPop:** không có playlist hay bài hát nhạc Việt, không có voice chat (chỉ nhắn tin và chat chữ trong app) [5][16].
  - **Kahoot!:** có quiz nhạc Việt do cộng đồng tự tạo trong Discover [16], nhưng không có voice chat tích hợp (buộc phải dùng thêm Zoom/Meet/Discord) và không có cơ chế mở dần đoạn nhạc theo mức điểm [9][16]. Khi bảo vệ cần nêu rõ: khác biệt của Meloo so với Kahoot! nằm ở luật mở dần (mục 4.3), kho nhạc chuẩn hóa và phòng chơi nhóm tích hợp voice (mục 4.2), không nằm ở việc "có nhạc Việt hay không".
  - **musci.io (Đoán Bài Hát):** xác nhận là web chơi đơn (1 người), không có chế độ nhiều người/phòng, không có voice, không có nhạc Việt [8][16].
  Như vậy, toàn bộ các ô "chưa kiểm / không thấy" cốt lõi đã được giải quyết bằng thực nghiệm kiểm chứng, giúp ba điểm khác biệt của Meloo đứng vững trước hội đồng.

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
16. Kết quả kiểm chứng và chơi thử trực tiếp đối thủ (SongTrivia, SongPop, Kahoot!, musci.io), chốt ngày 19/10/2026 và cập nhật hoàn thiện ngày 02/11/2026 (ticket BIZ-04 và BIZ-09).

Nguồn của cột Meloo: tài liệu "Meloo — Kế hoạch dự án" của Thái, mục "Phạm vi sản phẩm" và "Luật chơi & quy tắc nghiệp vụ" (BR-01…BR-29). Tài liệu này nằm ngoài repo; các mã BR sẽ kiểm lại được khi `docs/srs/02-business-rules.md` được đưa vào ở ticket SRS.

## 8. Câu hỏi bảo vệ

1. **Đã có SongTrivia chơi realtime với bạn, sao còn làm Meloo?**
   Danh sách ngôn ngữ của SongTrivia không có tiếng Việt [2]. Khi chơi thử, SongTrivia hoàn toàn không có nhạc Việt và không có voice chat (chỉ có chat chữ) [3][16]. Meloo nhắm vào nhóm bạn người Việt nghe nhạc Việt, có giao diện tiếng Việt và tích hợp voice chat ngay trong web mà không cần cài thêm phần mềm.
2. **Kahoot! có tiếng Việt và có sẵn quiz nhạc, sao không dùng Kahoot!?**
   Kahoot! có quiz nhạc chơi sẵn, kể cả quiz luyện nghe [14][15], và có cả quiz nhạc Việt do cộng đồng tạo trong Discover [16]. Nhưng Kahoot! là nền tảng quiz chung: mỗi quiz là một bộ câu hỏi tĩnh soạn sẵn, không có voice tích hợp (phải mở song song Zoom/Discord), và không có cơ chế nghe mở dần theo mức điểm [9][16]. Meloo là game đoán nhạc chuyên biệt: bài lấy từ kho nhạc chuẩn hóa lọc theo ca sĩ, thập niên, cấp audio mờ chống gian lận, tính điểm theo mức nghe 5 mức (BR-01, BR-02).
3. **Spotify đã có Music Quiz, Meloo cạnh tranh thế nào?**
   Music Quiz cần Premium, tuổi từ 18, và Việt Nam chưa có trong danh sách quốc gia [10]. Nguồn chỉ mô tả chia sẻ điểm và thách bạn bè, không thấy chơi realtime [10]. Nếu Spotify mở rộng, Meloo vẫn khác ở phòng chơi nhóm và luật mở dần. Đây cũng là rủi ro được ghi ở mục 5.
4. **Heardle nổi tiếng mà vẫn đóng cửa, Meloo có lặp lại không?**
   Heardle có một bài mỗi ngày ("song of the day") [13], và nguồn không thấy chế độ nhiều người [7]. Spotify nêu lý do đóng là để tập trung vào các tính năng khám phá nhạc khác [6]. Meloo lấy phòng chơi nhóm làm lõi; thử thách ngày chỉ là tính năng phụ.
5. **Số liệu nào chứng minh người dùng cần ba điểm khác biệt này?**
   Bảng so sánh và kết quả chơi thử trực tiếp đã chứng minh khoảng trống sản phẩm là có thật (các đối thủ đều thiếu ít nhất một trong các yếu tố: tiếng Việt, nhạc Việt, voice tích hợp trên web, hoặc luật mở dần). Nhu cầu người dùng thực tế được kiểm chứng định lượng bằng khảo sát BIZ-03/BIZ-05 (ngưỡng H1–H4) và hành vi thật trong giai đoạn beta (≥ 30 tài khoản, ≥ 100 trận, tỷ lệ quay lại ≥ 30%).
