# ADR-002: API-first với OpenAPI và AsyncAPI
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Backend (chủ yếu Codex) và frontend (Claude) làm song song bằng hai agent khác nhau. Nếu hai bên tự đoán hình dạng API thì sai khác chỉ lộ ra lúc ghép, tốn một vòng sửa lớn. Meloo có hai loại giao tiếp: REST (đăng nhập, kho nhạc, chơi đơn, thử thách ngày, quản trị) và realtime STOMP (phòng, chat, tín hiệu voice, xem ADR-004). Hợp đồng còn phải bảo đảm đáp án không lộ trước khi vòng kết thúc.

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Code-first (backend viết trước, sinh tài liệu) | Nhanh cho backend | Frontend phải chờ; hợp đồng trôi theo code |
| Thỏa thuận miệng/Markdown | Không cần công cụ | Không kiểm máy được; dễ lệch |
| **API-first: OpenAPI 3.1 (REST) + AsyncAPI 3 (STOMP), sinh code hai phía** | Hai bên làm song song sau khi hợp đồng merge; lint và sinh code bắt lệch sớm; làm nguồn cho MSW mock | Thêm bước viết hợp đồng; sửa hợp đồng phải qua ticket riêng |

## Quyết định
Hợp đồng là nguồn sự thật, đặt ở `contracts/openapi.yaml` và `contracts/asyncapi.yaml`:
- REST: OpenAPI 3.1. Backend sinh interface và model, controller `implements` interface đó; frontend sinh `schema.gen.ts` từ cùng file.
- Realtime: AsyncAPI 3 mô tả destination STOMP và message. Record Java và type TypeScript của message viết tay nhưng khớp từng field, nằm trong cùng PR hợp đồng.
- Chỉ ticket `CT-xx` (giữ `hot-contract`) được sửa hợp đồng. Sau khi CT merge, backend và frontend của tính năng chạy song song; hợp đồng thiếu thì ghi "Ticket bổ sung" thay vì tự sửa.
- Sau CT merge chỉ được thêm; đổi tên, xóa hay đổi kiểu là breaking và do Thái quyết.
- Schema vòng đang chơi không có trường đáp án; đáp án chỉ ở `RoundResult` và sự kiện `ROUND_END`.

## Hệ quả
- Tốt: song song hóa được hai agent; lệch hợp đồng bị CI bắt; frontend mock bằng MSW từ hợp đồng; kiểm chống lộ đáp án ngay ở mức schema.
- Xấu hoặc rủi ro: hợp đồng sai thì lan ra cả hai phía, nên ticket CT luôn có Thái duyệt. AsyncAPI chưa sinh code đầy đủ như OpenAPI nên phần message phải viết tay và được QA đối chiếu. File hợp đồng là điểm nóng xung đột, giảm bằng khóa `hot-contract`.

## Câu hỏi bảo vệ
1. API-first khác code-first thế nào? — Viết và duyệt hợp đồng trước, hai phía sinh code từ nó; code-first thì hợp đồng đi theo code.
2. Vì sao cần cả AsyncAPI? — OpenAPI không mô tả được destination và message STOMP.
3. Đổi API sau khi đã merge thì sao? — Chỉ thêm tương thích; breaking phải ghi rõ tác động và Thái quyết.
4. Hợp đồng giúp bảo mật gì? — Schema vòng đang chơi không có trường đáp án nên không thể lộ qua API.
