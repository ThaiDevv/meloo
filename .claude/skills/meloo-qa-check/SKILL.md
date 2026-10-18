---
name: meloo-qa-check
description: "Quy trình QA khớp nối (integration coherence) của dự án Meloo: đối chiếu hai đầu của mỗi ranh giới — OpenAPI ↔ controller ↔ client frontend, AsyncAPI ↔ destination STOMP ↔ store, máy trạng thái vòng/phòng ↔ code, i18n vi ↔ en, route ↔ link, migration ↔ entity ↔ DTO — và lập ma trận bằng chứng cho từng Acceptance Criteria. Dùng khi QA một ticket trước khi mở PR, khi người dùng hỏi 'QA giúp', 'kiểm tra khớp hợp đồng', 'kiểm tra i18n', hoặc khi agent meloo-qa được gọi."
---

# meloo-qa-check — QA khớp nối

Lỗi tốn kém nhất ở Meloo thường không nằm trong một file mà ở chỗ hai phần gặp nhau. Ví dụ: backend trả `{ items: [...] }` còn frontend đọc thẳng mảng; AsyncAPI ghi `/topic/rooms/{id}/round` còn code gửi `/topic/room/{id}/round`. Xét riêng thì bên nào cũng "đúng". Vì vậy mọi kiểm tra dưới đây đều đọc CẢ HAI phía cùng lúc.

## Chọn kiểm tra theo diff
| Diff chạm | Kiểm tra |
|---|---|
| `contracts/openapi.yaml` hoặc controller | A |
| `frontend/src/**/api`, hook gọi API | A, B |
| `contracts/asyncapi.yaml`, `room/realtime`, STOMP ở frontend | C |
| Engine, phòng, trận | D |
| `frontend/src/locales/**`, chữ trên giao diện | E |
| Route, link, điều hướng | F |
| Migration, entity, DTO | G |

Mục H (ma trận AC) luôn làm.

## A. OpenAPI ↔ backend
- Operation mới hoặc vừa đổi: controller `implements` interface sinh từ OpenAPI, không tự khai báo mapping khác; kiểu trả về là model sinh ra; mã HTTP và lỗi ProblemDetail đúng như hợp đồng.
- Field trong response khớp tên (camelCase) và kiểu; không có field ngoài hợp đồng, nhất là field lộ đáp án.

## B. OpenAPI ↔ frontend
- Gọi qua `src/shared/api` với path có kiểu; hook bóc đúng lớp bọc; xử lý optional/null.
- Grep `as ` và `any` trong code xử lý dữ liệu API. Ép kiểu dữ liệu từ server là FAIL.
- MSW handler dùng dữ liệu trong `contracts/examples/**`.

## C. AsyncAPI ↔ STOMP
- `python3 .claude/skills/meloo-qa-check/scripts/stomp-destinations.py` liệt kê destination trong hợp đồng và trong code (backend + frontend), đánh dấu chỗ lệch. Script dựa trên mẫu chữ, nên đọc lại bằng mắt từng mục nó báo.
- Payload: so từng field giữa record Java, type TypeScript và schema AsyncAPI; phải có `serverTime`; không có field đáp án trước ROUND_END.

## D. Máy trạng thái
- Vòng: CHỜ → ĐANG_CHƠI → ĐÚNG | HẾT_LƯỢT | HẾT_GIỜ (BE-GAME-01). Phòng và trận: theo sơ đồ ở DOC-03.
- Liệt kê mọi chỗ gán trạng thái trong code, rồi kiểm cả hai chiều:
  - Mọi chuyển đổi trong code đều có trong sơ đồ (không có chuyển đổi lạ).
  - Mọi chuyển đổi trong sơ đồ đều có code thực hiện (không thiếu nhánh, nhất là nhánh kết thúc).
- Nhánh `if (status === …)` ở frontend chỉ dùng trạng thái có thật.

## E. i18n
- `python3 .claude/skills/meloo-qa-check/scripts/check-i18n-parity.py frontend/src/locales` báo key thiếu ở vi hoặc en, giá trị rỗng, biến `{{…}}` lệch nhau.
- Grep chữ tiếng Việt có dấu trong file `.tsx` ngoài thư mục locales: đó là chuỗi cứng.

## F. Route ↔ link
- Lấy danh sách route từ `src/features/*/routes.tsx`. Mọi `navigate(`, `<Link to=`, `href=` phải trỏ tới route có thật, tham số động đúng tên.

## G. Migration ↔ entity ↔ API
- Cột trong migration ↔ field entity ↔ DTO: tên, kiểu, nullable thống nhất; enum cùng giá trị.

## H. Ma trận bằng chứng AC
Mỗi AC một dòng: AC → bằng chứng (tên test + file, lệnh + kết quả, hoặc ảnh) → PASS, FAIL hay CHƯA KIỂM. Chạy test thật và ghi lại lệnh đã chạy.

## Báo cáo QA (đầu ra)
```markdown
## QA MEL-<số> — <PASS | FAIL>
| Kiểm tra | Kết quả | Bằng chứng / file:dòng |
|---|---|---|
| A OpenAPI ↔ backend | PASS | … |

### Ma trận AC
| # | AC | Bằng chứng | Kết quả |
|---|---|---|---|

### Cần sửa (FAIL)
1. `file:dòng` — vấn đề — đề xuất sửa

### Chưa kiểm (lý do, lệnh để Thái chạy)
```
Không ghi PASS cho mục chưa chạy hoặc chưa đọc. Kết luận chung là FAIL nếu có ít nhất một mục FAIL.
