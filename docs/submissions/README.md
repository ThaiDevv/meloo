# Hồ sơ Thuyết minh Chi tiết Thiết kế Hệ thống Meloo (Mốc 2)

> **Mã tài liệu:** DOC-05 · **Mã ticket Jira:** MEL-62 · **Mốc bàn giao:** Mốc 2 (Sprint S3)  
> **Sinh viên thực hiện:** Trần Văn Thái  
> **Hạn nộp chính thức:** 16/11/2026, 09:07  
> **Trạng thái:** Hoàn tất hồ sơ, sẵn sàng nộp (Ready for Submission)

---

## 1. Danh mục các tệp bàn giao trong thư mục

Thư mục `docs/submissions/` chứa trọn bộ hồ sơ thiết kế kỹ thuật phục vụ đánh giá Mốc 2 của học phần:

| Tên tệp tin | Định dạng | Dung lượng | Mục đích sử dụng |
|---|---|---|---|
| [`thuyet-minh-thiet-ke-chi-tiet.md`](thuyet-minh-thiet-ke-chi-tiet.md) | Markdown | ~74 KB | Bản nguồn văn bản phục vụ quản lý phiên bản Git, đối chiếu diff và review chéo giữa các agent. |
| [`MELOO-Thuyet-minh-thiet-ke-chi-tiet.docx`](MELOO-Thuyet-minh-thiet-ke-chi-tiet.docx) | Microsoft Word (.docx) | ~3,1 MB | Bản văn bản định dạng chuẩn, có mục lục, bảng biểu và nhúng toàn bộ 25 hình ảnh sơ đồ kỹ thuật phân giải cao. |
| [`MELOO-Thuyet-minh-thiet-ke-chi-tiet.pdf`](MELOO-Thuyet-minh-thiet-ke-chi-tiet.pdf) | Adobe PDF (.pdf) | ~4,4 MB | Bản tài liệu xuất chuẩn từ Word sẵn sàng tải lên hệ thống nộp bài của nhà trường. |
| [`build_submission.py`](build_submission.py) | Python 3 | ~28 KB | Công cụ tự động hóa biên soạn Markdown, xây dựng Word (.docx) và xuất PDF qua LibreOffice. |

---

## 2. Cấu trúc nội dung thuyết minh

Hồ sơ thuyết minh được biên soạn theo đúng chuẩn cấu trúc học thuật tiếng Việt, bao gồm 7 phần chính:
1. **Phần I: Giới thiệu tổng quan và phát biểu bài toán** (Kế thừa BIZ-02, BIZ-04, BIZ-05; phân tích thị trường, 4 giả thuyết H1–H4, 4 personas P1–P4 và so sánh đối thủ).
2. **Phần II: Đặc tả yêu cầu phần mềm (SRS) và quy tắc nghiệp vụ** (Kế thừa DOC-01 / MEL-28; 19 yêu cầu chức năng FR-01..19, 15 yêu cầu phi chức năng NFR-01..15, 29 quy tắc nghiệp vụ BR-01..29 và từ điển khái niệm).
3. **Phần III: Thiết kế Use Case và đặc tả chi tiết** (Kế thừa DOC-02 / MEL-29; 6 tác nhân, 34 use case, 6 sơ đồ use case phân hệ, ma trận truy vết FR $\leftrightarrow$ UC và đặc tả chi tiết 10 use case trọng tâm).
4. **Phần IV: Thiết kế kiến trúc hệ thống C4 Model và Triển khai** (Kế thừa DOC-04 / MEL-52; 4 cấp độ C4 Context, Container, Component và Deployment AWS; tổng hợp 8 quyết định ADR-001..008; chiến thuật NFR).
5. **Phần V: Thiết kế chi tiết phần mềm (UML chi tiết)** (Kế thừa DOC-03 / MEL-51; sơ đồ lớp miền tổng quát, chi tiết 4 module cốt lõi `identity`, `catalog`, `game`, `room`; 5 sơ đồ tuần tự Sequence; 3 sơ đồ máy trạng thái State Machine; 1 sơ đồ hoạt động Activity).
6. **Phần VI: Thiết kế cơ sở dữ liệu và tổ chức lưu trữ** (Kế thừa DES-01 / MEL-30; nguyên tắc phân quyền module Spring Modulith; mô hình quan hệ thực thể ERD; từ điển dữ liệu chi tiết 21 bảng đồng bộ 100% với `02-tu-dien-du-lieu.md`; kế hoạch di trú Flyway V1..V9).
7. **Phần VII: Bộ câu hỏi bảo vệ đồ án và hướng dẫn giải thích** (8 câu hỏi trọng tâm của Hội đồng phản biện về kiến trúc, bảo mật chống gian lận, xử lý realtime và luật chơi kèm gợi ý giải trình chuẩn xác).
8. **Phụ lục: Danh mục tài liệu tham chiếu** (10 nguồn số liệu và tài liệu kỹ thuật liên quan).

---

## 3. Quy trình tái sinh tệp (Re-generation Guide)

Khi có sự điều chỉnh trong tài liệu nguồn, người dùng hoặc lập trình viên có thể tái sinh toàn bộ tệp `.docx` và `.pdf` một cách tự động thông qua script được theo dõi phiên bản trong thư mục:
```bash
python3 docs/submissions/build_submission.py
```
*(Yêu cầu môi trường có cài đặt `python-docx` và `libreoffice`)*.
