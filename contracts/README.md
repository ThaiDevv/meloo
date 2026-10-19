# Hợp đồng API

Meloo làm theo hướng API-first: hợp đồng trong thư mục này là nguồn sinh code cho cả backend lẫn frontend.

| File | Nội dung |
|---|---|
| `openapi.yaml` | REST, OpenAPI 3.1 (từ CT-01) |
| `asyncapi.yaml` | Sự kiện STOMP qua WebSocket, AsyncAPI 3 (từ CT-02) |
| `examples/` | Ví dụ request, response và message, dùng cho mock (MSW) và test |

- Hai file `openapi.yaml` và `asyncapi.yaml` là file nóng (`hot-contract`): chỉ ticket `CT-xx` được sửa.
- Không trường nào được lộ đáp án (tên bài, id bài, URL nhạc) trước khi vòng kết thúc.
- Quy ước viết, lint, sinh code và tương thích ngược: skill `meloo-contract`.
