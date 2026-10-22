# Thuật ngữ Meloo

> Ticket: MEL-28 (DOC-01) · Trạng thái: **đã chốt ngày 22/10/2026**
> Mục đích: tài liệu, code và Jira gọi cùng một tên. Định nghĩa khớp với `02-business-rules.md` bản 22/10. Tên tiếng Anh là tên dự kiến dùng trong code; tên cuối cùng chốt ở DES-01.

## Luật chơi

| Thuật ngữ | Tiếng Anh | Định nghĩa | BR |
|---|---|---|---|
| Preview | Preview | Đoạn nhạc khoảng 30 giây do nền tảng nhạc cung cấp qua API (nguồn chính đề xuất ở ADR-003: Deezer). API không công bố đoạn này nằm ở đâu trong bài (SPK-01). | BR-01 |
| Mức nghe | Listening level | Một trong 5 độ dài đoạn nghe: 2, 5, 10, 20, 30 giây, tính từ đầu đoạn preview. Khi chơi nhiều người, mỗi người tự mở mức của mình. | BR-01, BR-07 |
| Nhãn mức nghe | Level label | Trên giao diện ghi gọn 2s, 5s, 10s, 20s, 30s, giống nhau ở tiếng Việt và tiếng Anh (nút mức nghe, thông báo "đúng ở mức 5s"). Câu văn trong tài liệu và giao diện ghi "5 giây" / "5 seconds". | BR-01 |
| Vòng | Round | Một bài hát cần đoán, qua tối đa 5 mức nghe. Khi chơi nhiều người, một vòng dài tối đa 45 giây. | BR-01, BR-07 |
| Trận | Match | Chuỗi 5, 10 hoặc 15 vòng (mặc định 10), không lặp bài. | BR-09 |
| Lượt đoán | Guess | Một lần người chơi chọn đáp án ở một mức nghe. | BR-04 |
| Nghe thêm | Reveal more | Thao tác mở mức nghe kế tiếp; hệ quả giống đoán sai. | BR-04 |
| Bỏ qua | Give up | Nút thay cho "Nghe thêm" ở mức 30 giây; vòng đó được 0 điểm. | BR-04 |
| Chuẩn hóa tên bài | Title normalization | Bỏ dấu, viết thường, bỏ phần trong ngoặc và phần ghi phiên bản (Remix, Live, Acoustic, feat. …) trước khi so khớp. Dùng để chấm đáp án và chặn chat lộ đáp án. | BR-05, BR-16 |
| Đoán đúng | Correct guess | Tên bài đã chọn trùng tên bài đáp án sau chuẩn hóa; mọi phiên bản và bản cover cùng tên đều tính. | BR-05 |
| Đúng ca sĩ | Right artist | Gợi ý hiện khi chọn đúng ca sĩ chính nhưng sai bài; vẫn tính là đoán sai. | BR-06 |
| Ô gợi ý | Autocomplete | Ô tìm tên bài trên toàn kho nhạc, không cần gõ dấu; dùng thay cho câu hỏi trắc nghiệm. | BR-05 |
| Điểm cơ bản | Base score | Điểm theo mức đoán đúng: 1.000 / 800 / 600 / 400 / 200. | BR-02 |
| Combo | Combo | Chuỗi vòng đoán đúng liên tiếp ở mức 2 hoặc 5 giây; vòng thứ 2 của chuỗi được +10%, từ vòng thứ 3 được +20% điểm cơ bản. Áp dụng cho chơi đơn và nhiều người, trừ thử thách ngày. | BR-08 |
| Thưởng tốc độ | Speed bonus | max(0, ⌊200 × số mili giây còn lại ÷ 45.000⌋) theo đồng hồ server, chỉ khi chơi nhiều người; lượt đoán server nhận từ mốc 45.000 ms trở đi bị từ chối. | BR-07 |
| Điểm vòng | Round score | Điểm cơ bản × (1 + hệ số combo) + thưởng tốc độ, chỉ khi đoán đúng; vòng không đoán đúng được 0 điểm. | BR-02, BR-07, BR-08 |
| Người thắng | Winner | Người có tổng điểm cao nhất; hòa thì người có tổng thời gian trả lời đúng ít hơn (tính bằng mili giây). | BR-10 |

## Chế độ chơi và phòng

| Thuật ngữ | Tiếng Anh | Định nghĩa | BR |
|---|---|---|---|
| Chơi đơn | Single player | Luyện tập một mình theo bộ lọc, có combo, không giới hạn thời gian mỗi vòng. | BR-07, BR-08 |
| Bộ chuẩn | Standard set | Bộ nhạc do hệ thống chọn cho chơi đơn xếp hạng. | BR-23 |
| Thử thách hằng ngày | Daily challenge | Một bài chung cho mọi người mỗi ngày, mỗi người một lượt, đổi bài lúc 00:00 giờ Việt Nam. | BR-25 |
| Phòng | Room | Nơi 2–5 người chơi trận thời gian thực; số người tối đa là cấu hình. | BR-11 |
| Phòng tự tạo | Custom room | Phòng do người chơi tạo, riêng hoặc công khai; không tính xếp hạng. | BR-23 |
| Phòng riêng | Private room | Chỉ vào bằng mã hoặc link; có voice. | BR-11, BR-18 |
| Phòng công khai | Public room | Phòng tự tạo hiện trong danh sách phòng; không có voice. | BR-11, BR-18 |
| Mã phòng | Room code | 6 ký tự chữ in hoa và số, không dùng 0, O, 1, I, L. | BR-11 |
| Sảnh chờ | Lobby | Màn hình trong phòng trước và sau mỗi trận; phòng tự tạo có nút sẵn sàng, phòng ghép trận có đếm ngược. Người vào lúc trận đang chạy chờ ở đây. | BR-12, BR-13, BR-15 |
| Chủ phòng | Room owner | Người chọn bộ nhạc, số vòng, bấm bắt đầu, mời người khác ra khỏi phòng. Rời phòng thì quyền chuyển cho người vào sớm nhất. Chỉ có ở phòng tự tạo. Không nhầm với chế độ Host ở Phase 2. | BR-12 |
| Mời ra | Kick | Chủ phòng đưa một người ra khỏi phòng; người đó không vào lại phòng đó được. | BR-12 |
| Chơi lại | Rematch | Phòng tự tạo: hết trận, cả phòng về sảnh để chơi tiếp, giữ nguyên mã phòng. Phòng ghép trận: ai ở lại sảnh thì đếm ngược lại. | BR-12, BR-13 |
| Ghép trận nhanh | Quick match | Tự vào phòng ghép trận đang chờ và hợp bộ lọc, không có thì tạo mới; không vào phòng công khai tự tạo; tính xếp hạng. | BR-13, BR-23 |
| Phòng ghép trận | Quick match room | Phòng do hệ thống tạo cho ghép trận nhanh; không hiện trong danh sách phòng công khai, không vào được bằng mã hay link, không có chủ phòng. | BR-13 |
| Đếm ngược ghép trận | Quick match countdown | 30 giây, bắt đầu khi có người thứ 2, không đặt lại khi thêm người. | BR-13 |
| Giữ chỗ | Reconnect grace | 30 giây giữ chỗ khi mất kết nối; trận không dừng để chờ. | BR-14 |
| Trận xếp hạng | Ranked match | Chơi đơn bộ chuẩn, thử thách ngày, ghép trận nhanh. | BR-23 |
| Chế độ Host / Event | Host mode | Chế độ chiếu màn hình lớn cho lớp học, team building, streamer. Thuộc Phase 2. | — |

## Người dùng và tài khoản

| Thuật ngữ | Tiếng Anh | Định nghĩa | BR |
|---|---|---|---|
| Khách | Guest | Người chơi chưa đăng nhập, dùng biệt danh, chơi mọi chế độ; không lên bảng xếp hạng; dữ liệu bị xóa sau 30 ngày không hoạt động. | BR-20 |
| Biệt danh | Nickname | Tên hiển thị 3–20 ký tự, không chứa từ cấm. | BR-22 |
| Gộp khách | Guest merge | Khi khách đăng nhập, lịch sử, thống kê, huy hiệu và điểm trận xếp hạng được gộp vào tài khoản (sự kiện `GuestMerged`). | BR-21 |
| Xóa tài khoản | Account deletion | Người dùng tự xóa tài khoản; dữ liệu cá nhân bị xóa, lịch sử trận được ẩn danh (sự kiện `AccountDeleted`). | BR-21 |
| Quản trị viên | Admin | Người vận hành hệ thống; email tài khoản ADMIN đầu tiên nằm trong `meloo.admin.emails`. | BR-28 |

## Nội dung và tiến trình

| Thuật ngữ | Tiếng Anh | Định nghĩa | BR |
|---|---|---|---|
| Kho nhạc | Catalog | Tập bài hát nhập từ API nhạc, phân loại theo thể loại, quốc gia, ca sĩ, thập niên. | BR-27 |
| Bộ nhạc / bộ đề | Playlist / question set | Tập bài chọn trước làm nguồn cho một trận; phải có đủ bài hợp lệ. | BR-09 |
| Bài bị vô hiệu hóa | Disabled track | Bài lỗi preview hoặc không phù hợp; không xuất hiện ở trận mới. Lỗi giữa trận thì vòng được thay bằng bài khác. | BR-27 |
| Bảng xếp hạng | Leaderboard | Bảng tuần và toàn thời gian theo tổng điểm trận xếp hạng; bảng tuần reset lúc 00:00 thứ Hai giờ Việt Nam. | BR-24 |
| Bảng theo ngày | Daily leaderboard | Bảng của thử thách ngày, xếp theo mức đoán đúng rồi theo thời gian. | BR-25 |
| Kết quả chia sẻ | Share card | Kết quả thử thách ngày dạng ô màu theo từng mức, không lộ tên bài. | BR-25 |
| Huy hiệu | Badge | Thành tích mở khóa tự động khi đạt điều kiện, không bị thu hồi; tính cả ở phòng tự tạo. | BR-23, BR-26 |
| Lịch sử trận | Match history | Danh sách trận đã chơi của một người, phục vụ `/me/matches`. | — |

## Giao tiếp

| Thuật ngữ | Tiếng Anh | Định nghĩa | BR |
|---|---|---|---|
| Chat phòng | Room chat | Tin nhắn tối đa 200 ký tự, 1 tin mỗi giây; trong vòng, tin chứa tên bài đáp án (so khớp sau chuẩn hóa) bị chặn. | BR-16 |
| Từ cấm | Banned words | Danh sách do admin quản lý, dùng để lọc chat và biệt danh. | BR-17, BR-22 |
| Báo cáo vi phạm | Report | Người chơi báo một tin nhắn vi phạm cho admin xử lý. | BR-17 |
| Voice | Voice chat | Trò chuyện giọng nói trong phòng riêng; mic mặc định tắt, tự tắt khi đoạn nhạc của chính người đó đang phát và tự bật lại sau đó. | BR-18, BR-19 |

## Kiến trúc và quy trình

| Thuật ngữ | Tiếng Anh | Định nghĩa |
|---|---|---|
| Server là trọng tài | Server-authoritative | Đáp án, điểm, đồng hồ do server quyết; client chỉ hiển thị. |
| Token mờ | Opaque token | Đường dẫn đoạn nhạc không chứa tên bài, hết hạn sau thời gian ngắn, chỉ cấp đúng mức đã mở. |
| Hàng đợi phòng | Room command queue | Mỗi phòng xử lý lệnh tuần tự trong hàng đợi riêng để tránh race condition. |
| Modular monolith | Modular monolith | Một ứng dụng Spring Boot chia thành các module (shared, identity, catalog, audio, game, room, chat, voice, progression, daily, admin), chỉ gọi nhau qua package `api/` và sự kiện trong `events/`. |
| Hợp đồng API | API contract | `contracts/openapi.yaml` (REST) và `contracts/asyncapi.yaml` (WebSocket); chốt trước khi code. |
| MoSCoW | MoSCoW | Phân ưu tiên: Must, Should, Could, Won't. |
| BR / FR / NFR | — | Quy tắc nghiệp vụ / Yêu cầu chức năng / Yêu cầu phi chức năng. |
| AC | Acceptance Criteria | Tiêu chí nghiệm thu kiểm chứng được của một ticket. |
| DoR / DoD | Definition of Ready / Done | Điều kiện để giao ticket cho agent / để coi ticket là hoàn thành. |
| Phạm vi file | File scope | Danh sách file một ticket được phép sửa. |
| File nóng | Hot file | File nhiều ticket hay chạm; mỗi label `hot-*` chỉ một ticket In Progress giữ. |
| RTO / RPO | Recovery Time / Point Objective | Thời gian tối đa để khôi phục dịch vụ / lượng dữ liệu tối đa có thể mất khi có sự cố. |
| Phase 2 | Phase 2 | Phần làm sau môn học: Premium, quảng cáo, Host, bài tài trợ, app di động. |
