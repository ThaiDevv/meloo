# ADR-001: Modular monolith với Spring Modulith
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Meloo có nhiều miền nghiệp vụ (identity, catalog, audio, game, room, chat, voice, progression, daily, admin) nhưng chỉ một người làm cùng hai agent, thời gian vài tuần và hạ tầng free tier (một EC2 1 GB RAM, xem ADR-006). Hai agent làm song song nên cần ranh giới module rõ để không sửa chồng file (luật 3 và 4 trong `AGENTS.md`). Hệ thống cũng cần bảo vệ nguyên tắc "server là trọng tài": đáp án chỉ nằm trong module được phép biết.

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Monolith một khối, không ràng buộc package | Nhanh lúc đầu, ít file | Dễ rối phụ thuộc vòng; agent sửa lẫn nhau; khó giải thích khi bảo vệ |
| Microservices | Tách triển khai, mở rộng độc lập | Cần nhiều máy, mạng, tracing; không vừa 1 GB RAM; vượt sức một người |
| **Modular monolith + Spring Modulith** | Một tiến trình, một DB; ranh giới module được kiểm bằng `ApplicationModules.verify()` trong CI; có thể tách dịch vụ sau | Phải giữ kỷ luật package; module vẫn chung DB nên cần quy ước không đọc bảng của nhau |

## Quyết định
Chọn modular monolith, mỗi module là một package dưới `com.meloo`. Quy ước trong mỗi module:
- `api/` (`@NamedInterface("api")`): interface và record để module khác gọi.
- `events/` (`@NamedInterface("events")`): record sự kiện miền do **module phát** sở hữu. Không đặt sự kiện vào `shared`, để ticket phát sự kiện không phải tranh khóa file nóng `hot-shared`.
- `web/`, `persistence/` và package tính năng là nội bộ; entity không ra khỏi module (ra ngoài dùng record).
- Module giao tiếp bằng gọi `api/` (đồng bộ) hoặc sự kiện miền (`@ApplicationModuleListener`, sau commit, listener idempotent).

Lý do: vừa ràng buộc sức người và RAM (một tiến trình), vừa cho CI làm đỏ khi vi phạm ranh giới, nên agent không "lách" được.

## Hệ quả
- Tốt: build và deploy đơn giản; ranh giới kiểm được tự động; khóa module theo thư mục khớp luật chống xung đột; ticket phát sự kiện không đụng `shared`.
- Xấu hoặc rủi ro: lỗi ở một module (OOM, vòng lặp) kéo cả tiến trình. Giảm bằng giới hạn JVM, health check và ADR-007 giữ trạng thái phòng gọn. Một DB chung dễ bị đọc chéo bảng: giảm bằng quy ước "chỉ repository trong `persistence/` của chính module được chạm bảng của nó" và review.
- Sự kiện đồng bộ trong một tiến trình không bền nếu tiến trình chết giữa chừng: giảm bằng Event Publication Registry.

## Câu hỏi bảo vệ
1. Vì sao không dùng microservices? — Một người làm, một EC2 1 GB; chi phí vận hành và độ trễ mạng lớn hơn lợi ích. Modulith giữ đường lui để tách sau.
2. Spring Modulith kiểm ranh giới thế nào? — `ApplicationModules.verify()` chạy trong test, đỏ khi module dùng package nội bộ của module khác.
3. Vì sao sự kiện nằm ở `events/` của module phát chứ không ở `shared`? — Mỗi ticket chỉ sửa thư mục của mình, không tranh khóa `hot-shared`; chủ sở hữu sự kiện rõ ràng.
4. Hai module cần dữ liệu của nhau thì sao? — Gọi qua `api/` hoặc nghe sự kiện, không đọc bảng của nhau.
