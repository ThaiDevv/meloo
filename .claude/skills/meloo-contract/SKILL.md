---
name: meloo-contract
description: "Quy ước hợp đồng API-first của Meloo: viết và sửa contracts/openapi.yaml (REST, OpenAPI 3.1) và contracts/asyncapi.yaml (STOMP, AsyncAPI 3), ví dụ trong contracts/examples, lint, sinh code cho backend và frontend, quy tắc tương thích và chống lộ đáp án. Dùng khi làm hoặc review ticket CT-*, hoặc khi cần biết hợp đồng quy định gì về một endpoint hay sự kiện."
---

# meloo-contract

Hợp đồng là thứ duy nhất cho phép backend (Codex) và frontend (Claude) làm song song mà không phải chờ nhau. Sai ở đây thì lan ra cả hai phía, nên ticket CT luôn có Thái duyệt.

## Ai được sửa
Chỉ ticket CT-xx (giữ `hot-contract`) được sửa `contracts/openapi.yaml` và `contracts/asyncapi.yaml`. Ticket backend hay frontend thấy hợp đồng thiếu thì ghi "Ticket bổ sung" trong PR, không tự sửa.

## Quy ước REST (OpenAPI 3.1)
- Tiền tố `/api/v1`. Tag theo module: `identity`, `catalog`, `game`, `room`, `progression`, `daily`, `admin`.
- `operationId` dạng `verbNoun` tiếng Anh (`createGuest`, `searchTracks`). Operation nào cũng có summary, ví dụ request/response và các mã lỗi.
- Field camelCase; id kiểu string (UUID); thời gian là `string` với `format: date-time` (ISO-8601 UTC); enum viết UPPER_SNAKE.
- Lỗi: `application/problem+json` (ProblemDetail) dùng chung schema `Problem`.
- Phân trang: query `page`, `size`; response dạng `{ items: [...], page, size, totalItems, totalPages }`, khai báo một schema trang chung rồi dùng lại (allOf).
- Bảo mật: mặc định `bearerAuth` (JWT); endpoint công khai ghi `security: []`; refresh dùng cookie.
- Đáp án (tên bài, ca sĩ, trackId, URL) chỉ được xuất hiện trong schema kết quả vòng (`RoundResult`, sự kiện `ROUND_END`), không có trong schema của vòng đang chơi.

## Quy ước realtime (AsyncAPI 3)
- Lệnh gửi lên: `/app/rooms/{roomId}/{command}`.
- Sự kiện phòng: `/topic/rooms/{roomId}/{lobby|round|scoreboard|chat|rtc}`.
- Tin riêng: `/user/queue/{errors|private}`.
- Message nào cũng có `type` (UPPER_SNAKE) và `serverTime`; payload có ví dụ.
- Không message nào chứa đáp án trước `ROUND_END`.
- Kênh `rtc.*` (offer, answer, ice) để dành cho voice.
- Record Java (`backend/src/main/java/com/meloo/room/api/message/`) và type TypeScript (`frontend/src/shared/realtime/messages.ts`) viết trong cùng PR CT, khớp từng field với schema.

## Sinh code và kiểm tra
- Lint: theo lệnh trong `contracts/README.md` (Redocly cho OpenAPI, AsyncAPI CLI cho AsyncAPI).
- Backend: generator khai báo trong pom (SET-03) sinh interface và model; lệnh sinh code trong `backend/AGENTS.md` phải chạy được.
- Frontend: `pnpm gen:api` ghi ra `frontend/src/shared/api/schema.gen.ts`. Commit file này cùng PR và không sửa tay.

## Tương thích
- Sau khi CT đã merge: chỉ được thêm (field optional mới, endpoint mới). Đổi tên, xóa hay đổi kiểu là breaking: PR phải ghi rõ, liệt kê những chỗ bị ảnh hưởng, Thái quyết.
- Field sắp bỏ: đánh `deprecated: true` trước, xóa ở CT sau.

## Checklist PR hợp đồng
- [ ] Lint xanh; code sinh ở cả hai phía biên dịch được; `schema.gen.ts` đã cập nhật.
- [ ] Operation và message nào cũng có ví dụ trong `contracts/examples/**`.
- [ ] Không schema nào lộ đáp án trước ROUND_END; message nào cũng có `serverTime`.
- [ ] Mô tả operation ghi rõ ai được gọi.
