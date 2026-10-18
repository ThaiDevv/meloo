---
name: meloo-docs
description: "Quy ước tài liệu môn học của Meloo: SRS (FR/NFR/BR), use case, UML PlantUML (use case, lớp, tuần tự, hoạt động, trạng thái, C4, triển khai), ERD và từ điển dữ liệu, ADR, runbook, báo cáo, slide và bộ câu hỏi bảo vệ; văn phong tiếng Việt học thuật, số liệu có nguồn. Dùng khi làm hoặc review ticket DOC-*, ARC-01, DES-01, BIZ-04, LEG-01, DEF-01, hoặc khi viết bất kỳ file nào trong docs/."
---

# meloo-docs

Thầy chấm nghiệp vụ nhiều hơn code. Tài liệu phải chứng minh được bài toán có thật, nghiệp vụ chặt chẽ và thiết kế có lý do. Thái phải tự giải thích được từng dòng khi bảo vệ.

## Thư mục
| Thư mục | Nội dung |
|---|---|
| `docs/business/` | Bài toán, đối thủ, khảo sát, beta, kế hoạch khởi nghiệp |
| `docs/srs/` | `01-srs.md`, `02-business-rules.md`, `03-glossary.md`, `usecases/` |
| `docs/architecture/` | `01-architecture.md`, `adr/ADR-0xx-*.md`, `data/` (ERD, từ điển dữ liệu, sự kiện miền) |
| `docs/uml/` | `usecase/`, `class/`, `sequence/`, `activity/`, `state/`, `c4/`, `deployment/`, gồm file `.puml` và `.png` |
| `docs/test/`, `docs/security/`, `docs/runbooks/`, `docs/manual/`, `docs/legal/` | Theo tên |
| `docs/process/` | Sprint review, quy trình, harness |
| `docs/submissions/` | Bản đã nộp |

## Văn phong
- Tiếng Việt rõ ràng, câu ngắn. Thuật ngữ thống nhất với `docs/srs/03-glossary.md` (vòng, mức nghe, trận xếp hạng, bộ đề…).
- Mã định danh: FR-xx, NFR-xx, BR-xx, UC-xx, ADR-0xx. Tham chiếu chéo bằng mã.
- Số liệu nào (khảo sát, KPI, tải) cũng ghi nguồn và ngày. Không bịa số: chưa có thì ghi `[CẦN SỐ LIỆU: …]` để Thái điền.
- Nội dung nghiệp vụ (BR, use case, khảo sát) là của Thái. Agent chỉ soạn nháp, định dạng và kiểm tra tính nhất quán; chỗ nào chưa chắc thì đánh dấu `[HỎI THÁI]`.

## Mẫu
- ADR, đặc tả use case, runbook: `references/templates.md`.
- Mỗi tài liệu thiết kế lớn (SRS, use case, kiến trúc, ERD, ADR) kết thúc bằng mục "Câu hỏi bảo vệ": 3–5 câu thầy có thể hỏi, kèm câu trả lời ngắn. Đây là nguồn cho DEF-01.

## PlantUML
- Mỗi sơ đồ một file `.puml` có `title`, xuất PNG cùng tên: `plantuml -tpng <file>`, hoặc `docker run --rm -v "$PWD":/w plantuml/plantuml -tpng /w/<file>`.
- C4 dùng thư viện chuẩn: `!include <C4/C4_Container>`.
- Sơ đồ lớp và sơ đồ trạng thái phải khớp code hiện tại; ghi commit đã đối chiếu vào phần chú thích.

## Tài liệu soạn ngoài repo
SRS, BR và use case thường được soạn cùng Claude trong Project Meloo trên claude.ai. Khi đó ticket trong repo chỉ làm mấy việc: đưa nội dung đã duyệt vào đúng file, định dạng, vẽ sơ đồ, mở PR. Không đổi nội dung đã được duyệt.
