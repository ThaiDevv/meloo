# Mô hình use case Meloo

> Ticket: MEL-29 (DOC-02) · Phụ thuộc: MEL-28 (DOC-01, SRS v1 đã chốt ngày 22/10/2026)
> Đầu vào: `docs/srs/01-srs.md` (FR-01 → FR-19), `docs/srs/02-business-rules.md` (BR-01 → BR-29), `docs/srs/03-glossary.md`.
> Đầu ra: sơ đồ ở `docs/uml/usecase/` (PlantUML + PNG) và đặc tả chi tiết ở các file cùng thư mục này. QA-04 soạn kịch bản UAT từ các use case này.
> Trạng thái: **bản nháp chờ Thái duyệt.** Nội dung nghiệp vụ là của Thái. Ngày 22/10/2026, Thái đã chốt 8 điểm còn mở ở mục 5; đặc tả đã cập nhật theo đó. Các file SRS/BR/thuật ngữ nằm ngoài phạm vi ticket nên chưa sửa; câu cần bổ sung vào BR ghi ở mục "Tài liệu cần cập nhật" của PR.

## 1. Tác nhân

| Tác nhân | Loại | Vai trò trong mô hình | Nguồn |
|---|---|---|---|
| Khách | Chính | Người chơi chưa đăng nhập, dùng biệt danh. Là tác nhân gốc: mọi use case chơi đều mở cho Khách. | SRS 2.1, BR-20 |
| Người chơi (đã đăng nhập) | Chính | Thừa hưởng mọi use case của Khách; thêm xóa tài khoản (hồ sơ và huy hiệu mở cho cả Khách, chỉ xem). | SRS 2.1, BR-21 |
| Chủ phòng | Chính | Vai trò trong phòng tự tạo (riêng hoặc công khai): chọn bộ nhạc và số vòng, bắt đầu trận, mời người ra. Khách cũng làm được chủ phòng, nên Chủ phòng thừa hưởng từ Khách. Phòng ghép trận không có chủ phòng. | BR-12, BR-13 |
| Quản trị viên | Chính | Đội vận hành: dashboard, kho nhạc, người dùng, báo cáo vi phạm, từ cấm, nhật ký. | SRS 2.1, BR-28 |
| Hệ thống API nhạc | Phụ | Nguồn bài hát và đoạn preview (nguồn chính đề xuất ở ADR-003: Deezer). | SRS 2.2, BR-27 |
| Nhà cung cấp OAuth | Phụ | Google (Must), Facebook (Should) xác thực danh tính. | FR-02, FR-03 |

"Hệ thống" trong đặc tả là Meloo (backend là trọng tài: đáp án, điểm, đồng hồ do server quyết; NFR-07).

## 2. Sơ đồ

| Sơ đồ | File | Nội dung |
|---|---|---|
| Tổng quát | `docs/uml/usecase/uc-00-tong-quat.png` | 6 tác nhân và đủ 34 use case, gom theo phân hệ |
| Tài khoản | `docs/uml/usecase/uc-01-tai-khoan.png` | UC-01 → UC-05 |
| Chơi đơn | `docs/uml/usecase/uc-02-choi-don.png` | UC-06 → UC-11 |
| Chơi nhiều người và giao tiếp | `docs/uml/usecase/uc-03-nhieu-nguoi.png` | UC-12 → UC-23 |
| Xếp hạng và tiến trình | `docs/uml/usecase/uc-04-xep-hang.png` | UC-24, UC-25, UC-04 |
| Quản trị | `docs/uml/usecase/uc-05-quan-tri.png` | UC-26 → UC-32 |
| Chi tiết từng use case | `docs/uml/usecase/chi-tiet/uc-<mã>-<tên>.png` | 14 sơ đồ riêng cho 14 use case đã đặc tả: use case đó, tác nhân liên quan và các use case include/extend trực tiếp |

Mã nguồn `.puml` nằm cạnh mỗi PNG (thư mục `chi-tiet/` làm tương tự). Vẽ lại trong thư mục `docs/uml/usecase/`: `docker run --rm -e PLANTUML_LIMIT_SIZE=16384 -v "$PWD":/w:Z -w /w plantuml/plantuml -tpng -charset UTF-8 <file>.puml`. Kiểu vẽ: khung hệ thống, tác nhân ngoài khung, đường nối thẳng (`skinparam linetype polyline`), hệ thống ngoài vẽ dạng hộp `<<actor>>`.

## 3. Danh mục use case

Tên dạng động từ + danh từ. Cột "Đặc tả" cho biết use case được đặc tả chi tiết ở file nào; use case còn lại chỉ nêu ở danh mục (dùng cho sơ đồ và truy vết).

| Mã | Use case | Tác nhân chính | FR | Đặc tả |
|---|---|---|---|---|
| UC-01 | Vào chơi bằng biệt danh | Khách | FR-01 | `01-tai-khoan.md` |
| UC-02 | Đăng nhập bằng tài khoản mạng xã hội | Khách | FR-02, FR-03, FR-04 | `01-tai-khoan.md` |
| UC-03 | Gộp dữ liệu khách (include của UC-02) | Khách | FR-02 | Trong UC-02 |
| UC-04 | Xem hồ sơ và thống kê cá nhân (include UC-25) | Khách | FR-04, FR-13 | — |
| UC-05 | Xóa tài khoản | Người chơi | FR-03 | — |
| UC-06 | Tìm bài hát (ô gợi ý, không cần gõ dấu) | Khách | FR-06 | Trong UC-08 |
| UC-07 | Chơi luyện tập ("Xếp hạng" bộ chuẩn hoặc "Luyện tập" tự chọn bộ lọc) | Khách | FR-07, FR-13, FR-14 | `02-choi-don.md` |
| UC-08 | Đoán bài hát trong vòng | Khách | FR-06, FR-07 | `02-choi-don.md` |
| UC-09 | Mở thêm mức nghe (Nghe thêm, Bỏ qua; extend của UC-08) | Khách | FR-07 | Trong UC-08 |
| UC-10 | Làm thử thách hằng ngày | Khách | FR-08, FR-13, FR-14 | `02-choi-don.md` |
| UC-11 | Chia sẻ kết quả thử thách ngày (extend của UC-10) | Khách | FR-08 | — |
| UC-12 | Tạo phòng | Chủ phòng | FR-09 | `03-nhieu-nguoi.md` |
| UC-13 | Vào phòng bằng mã hoặc link | Khách | FR-09 | `03-nhieu-nguoi.md` |
| UC-14 | Vào phòng công khai từ danh sách | Khách | FR-10 | — |
| UC-15 | Ghép trận nhanh | Khách | FR-10 | `03-nhieu-nguoi.md` |
| UC-16 | Báo sẵn sàng (phòng tự tạo) | Khách | FR-09 | — |
| UC-17 | Bắt đầu trận | Chủ phòng | FR-09 | `03-nhieu-nguoi.md` |
| UC-18 | Chơi trận nhiều người | Khách | FR-09, FR-13 | `03-nhieu-nguoi.md` |
| UC-19 | Mời người ra khỏi phòng | Chủ phòng | FR-09 | — |
| UC-20 | Chơi lại trong phòng | Khách | FR-09, FR-10 | — |
| UC-21 | Nhắn tin trong phòng | Khách | FR-11 | `03-nhieu-nguoi.md` |
| UC-22 | Báo cáo tin nhắn vi phạm (extend của UC-21) | Khách | FR-11 | — |
| UC-23 | Trò chuyện bằng voice (phòng riêng) | Khách | FR-12 | — |
| UC-24 | Xem bảng xếp hạng | Khách | FR-14 | `04-xep-hang-quan-tri.md` |
| UC-25 | Xem huy hiệu | Khách | FR-15 | — |
| UC-26 | Xem dashboard số liệu | Quản trị viên | FR-16 | — |
| UC-27 | Nhập bài từ API nhạc (extend của UC-28) | Quản trị viên | FR-05 | Trong UC-28 |
| UC-28 | Quản lý kho nhạc và bộ đề | Quản trị viên | FR-05, FR-16 | `04-xep-hang-quan-tri.md` |
| UC-29 | Khóa tài khoản hoặc khách (nhập lý do, mở khóa lại được; extend của UC-30) | Quản trị viên | FR-17 | — |
| UC-30 | Xử lý báo cáo vi phạm | Quản trị viên | FR-17 | `04-xep-hang-quan-tri.md` |
| UC-31 | Quản lý từ cấm (extend của UC-30) | Quản trị viên | FR-17 | — |
| UC-32 | Xem nhật ký thao tác | Quản trị viên | FR-17 | — |
| UC-33 | Đổi ngôn ngữ giao diện | Khách | FR-18 | — |
| UC-34 | Cài đặt ứng dụng (PWA) | Khách | FR-19 | — |

Có 34 use case; 14 use case được đặc tả chi tiết: UC-01, 02, 07, 08, 10, 12, 13, 15, 17, 18, 21, 24, 28, 30.

Hồ sơ của khách (quyết định PO 22/10, mục 5 #2): UC-04 mở cho Khách ở chế độ chỉ xem, gồm lịch sử, thống kê và huy hiệu của phiên khách (BR-21 gộp các dữ liệu này vào tài khoản khi đăng nhập), kèm lời nhắc "Đăng nhập để đưa điểm lên bảng xếp hạng". UC-25 là phần include của UC-04 nên cũng mở cho Khách. Xóa tài khoản (UC-05) vẫn chỉ dành cho người đã đăng nhập.

## 4. Truy vết UC ↔ FR

Bảng này bổ sung cho mục 5 của `01-srs.md` (SRS v1 để lại cho DOC-02). Mọi FR đều có ít nhất một use case.

| FR | Ưu tiên | Use case |
|---|---|---|
| FR-01 | Must | UC-01 |
| FR-02 | Must | UC-02, UC-03 |
| FR-03 | Should | UC-02 (Facebook), UC-05 |
| FR-04 | Must | UC-02, UC-04 |
| FR-05 | Must | UC-27, UC-28 |
| FR-06 | Must | UC-06, UC-08 |
| FR-07 | Must | UC-07, UC-08, UC-09 |
| FR-08 | Should | UC-10, UC-11 |
| FR-09 | Must | UC-12, UC-13, UC-16, UC-17, UC-18, UC-19, UC-20 |
| FR-10 | Must | UC-14, UC-15, UC-20 |
| FR-11 | Must | UC-21, UC-22 |
| FR-12 | Should | UC-23 |
| FR-13 | Must | UC-04 (xem), UC-07, UC-10, UC-18 (hệ thống lưu lịch sử khi trận kết thúc) |
| FR-14 | Must | UC-07, UC-10, UC-24 |
| FR-15 | Should | UC-25 |
| FR-16 | Must | UC-26, UC-28 |
| FR-17 | Must | UC-29, UC-30, UC-31, UC-32 |
| FR-18 | Must | UC-33 |
| FR-19 | Should | UC-34 |

## 5. Điểm đã chốt (22/10)

Thái (Product Owner) đã quyết 8 điểm mà SRS và BR chưa nói rõ. Cột cuối là câu bổ sung viết sẵn trong mục "Tài liệu cần cập nhật" của PR #18; các file BR nằm ngoài phạm vi ticket này nên chưa sửa.

| # | Quyết định | Use case đã sửa | BR cần cập nhật |
|---|---|---|---|
| 1 | Chỉ chơi đơn bộ chuẩn ("Xếp hạng") được tính xếp hạng. Chơi bằng bộ lọc tự chọn ("Luyện tập") vẫn có điểm, combo, lưu lịch sử, thống kê, tính huy hiệu, nhưng không lên bảng. Màn chọn chế độ tách hai lựa chọn. | UC-07 | BR-23 |
| 2 | Khách xem được bảng xếp hạng nhưng không có tên trên bảng. Khách có hồ sơ chỉ xem (lịch sử, thống kê, huy hiệu của phiên khách) kèm lời nhắc đăng nhập. Xóa tài khoản chỉ dành cho người đã đăng nhập. | UC-24, UC-04 (tác nhân chính đổi sang Khách), UC-25, mục 1 | BR-20 |
| 3 | Chơi đơn bỏ dở: server giữ trận 30 phút, mở lại thì chơi tiếp đúng vòng và mức nghe, không đổi bài. Quá 30 phút: ABANDONED, lưu các vòng đã xong, vòng dở 0 điểm, không tính xếp hạng kể cả bộ chuẩn. | UC-07 (luồng 4b) | BR-23 |
| 4 | Lượt thử thách ngày thuộc về ngày server cấp bài (lúc bắt đầu lượt), kể cả đoán xong sau 00:00. Trần 5 phút mỗi lượt; hết trần mà chưa đúng là "Chưa đoán ra". | UC-10 | BR-25 |
| 5 | Ghép trận nhanh: số người kết nối dưới 2 khi đang đếm ngược thì dừng; có người thứ 2 thì đếm lại đủ 30 giây. Lúc hết đếm ngược kiểm tra lại, dưới 2 thì không bắt đầu và quay về chờ. | UC-15 (luồng 4b, 5b) | BR-13 |
| 6 | Tin chứa từ cấm bị chặn cả tin, không che bằng ***; chỉ người gửi thấy cảnh báo; dùng chung cơ chế "tin bị chặn" với tin lộ đáp án. Biệt danh chứa từ cấm bị từ chối ngay khi nhập. | UC-21, UC-01 | BR-16, BR-17 (BR-22 đã có) |
| 7 | MVP không có mức cảnh cáo. Admin chọn bỏ qua, khóa (UC-29) hoặc thêm từ cấm (UC-31). Khóa phải nhập lý do, ghi vào nhật ký; mở khóa lại được. Cảnh cáo là hướng mở rộng Phase 2. | UC-30, UC-29 (danh mục) | BR-28 |
| 8 | Tin bị báo do khách gửi: admin khóa theo id khách; id đó không tạo hay vào phòng, không chat được. Giới hạn: khách xóa dữ liệu trình duyệt thì có id mới. | UC-30, UC-29 (tên và mô tả trong danh mục) | BR-28 |

Trong đặc tả, các chỗ BR chưa có câu tương ứng dẫn chiếu "Quyết định PO 22/10 (mục 5, #n)".

## 6. Câu hỏi bảo vệ

1. **Vì sao UC-08 (Đoán bài hát trong vòng) tách riêng mà không nằm trong UC-07?** Cùng một vòng chơi được dùng ở chơi đơn, thử thách ngày và trận nhiều người. Tách ra thì mô tả luật chấm điểm và chống lộ đáp án một lần (mối quan hệ include), các use case còn lại chỉ nêu phần khác biệt như giới hạn giờ hay thưởng tốc độ.
2. **Vì sao Chủ phòng là tác nhân thừa hưởng từ Khách mà không phải từ Người chơi?** BR-20 cho khách chơi mọi chế độ nên khách cũng tạo được phòng riêng. Chủ phòng là vai trò trong một phòng tự tạo, không phải loại tài khoản; phòng ghép trận không có chủ phòng (BR-13).
3. **Vì sao Nhà cung cấp OAuth và Hệ thống API nhạc là tác nhân mà không phải thành phần bên trong?** Cả hai nằm ngoài ranh giới Meloo, có điều khoản và độ tin cậy riêng (ADR-003). Vẽ thành tác nhân phụ cho thấy các điểm Meloo phụ thuộc bên ngoài và chỗ cần xử lý ngoại lệ khi bên ngoài lỗi (UC-02 bước 4a, UC-08 bước 1a).
4. **Vì sao chỉ đặc tả 14 trong 34 use case?** Chọn các use case mang nghiệp vụ của Meloo (luật chơi, phòng, ghép trận, chat, xếp hạng, quản trị kho nhạc và báo cáo). Các use case còn lại (đổi ngôn ngữ, xem nhật ký…) đơn giản, chỉ nêu ở danh mục và truy vết; ticket sau bổ sung khi cần.
5. **Làm sao biết use case không bỏ sót yêu cầu?** Bảng truy vết ở mục 4 phủ cả 19 FR; QA-04 dựa vào bảng này để soạn kịch bản UAT và kiểm xem mỗi FR có kịch bản.
