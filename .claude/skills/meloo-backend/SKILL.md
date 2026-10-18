---
name: meloo-backend
description: "Quy ước code backend của Meloo (Java 21, Spring Boot 4.x, Spring Modulith, PostgreSQL 17, Flyway, STOMP): cấu trúc module, API và sự kiện giữa các module, bảo mật qua ModuleSecurityRules, transaction, chống N+1, hàng đợi lệnh của phòng, migration, test Testcontainers, và những thay đổi của Spring Boot 4 (Jackson 3, Security 7) để không dùng API cũ. Dùng khi làm hoặc review ticket backend (BE-*, DB-*, SET-03, SPK-01) hay khi viết code Java trong backend/."
---

# meloo-backend

## Đọc trước khi code
1. `backend/AGENTS.md`: lệnh build/test chính xác (tạo ở SET-03).
2. Phiên bản thật trong `backend/pom.xml`. Spring Boot 4 đổi nhiều API so với 2.x/3.x. Không chắc API nào thì đọc `references/spring-boot-4.md` và tài liệu chính thức ở docs.spring.io đúng phiên bản, đừng viết theo trí nhớ về Boot 3.
3. Quy tắc nghiệp vụ liên quan trong `docs/srs/02-business-rules.md`.

## Cấu trúc module (Spring Modulith)
Package gốc `com.meloo`, mỗi module một package: shared, identity, catalog, audio, game, room, chat, voice, progression, daily, admin.
```
com.meloo.<module>/
  package-info.java   # @ApplicationModule (khi cần khai báo phụ thuộc)
  api/                # @NamedInterface("api"): interface và record cho module khác gọi
  events/             # @NamedInterface("events"): record sự kiện mà module này phát
  web/                # controller implements interface sinh từ OpenAPI
  <tính năng>/        # VD solo/, lobby/, match/, search/: logic, service
  persistence/        # entity, repository: nội bộ, không lộ ra ngoài
```
- Module khác chỉ được dùng `api/`, `events/` (và kiểu ở package gốc nếu có). `ApplicationModules.verify()` làm đỏ CI khi vi phạm; đừng "chữa" bằng cách mở rộng NamedInterface tùy tiện.
- Cần một API mà module khác chưa có thì không sửa module đó. Ghi vào mục "Ticket bổ sung" của PR và soạn nội dung ticket.
- Danh sách sự kiện, API giữa các module và cách viết listener idempotent: `references/modulith-and-events.md`.

## Bảo mật
- Mỗi module khai báo quy tắc qua bean `ModuleSecurityRules` (tạo ở SET-03). Không sửa `SecurityConfig` trong `shared`.
- Server là trọng tài: client không bao giờ gửi điểm. Không dữ liệu nào lộ đáp án trước ROUND_END (REST, STOMP, log, thông báo lỗi).
- JWT, refresh token, audio token, OAuth: `references/security.md`.

## Web và lỗi
- Controller implements interface sinh từ `contracts/openapi.yaml`, không tự đặt path khác hợp đồng. Hợp đồng thiếu field hay endpoint thì cần ticket CT, không tự sửa hợp đồng.
- Lỗi: ném exception miền; handler chung trong `shared` đổi sang ProblemDetail. Không trả stack trace.
- Validate DTO bằng Bean Validation, giới hạn theo BR.

## Transaction và dữ liệu
- `@Transactional` đặt ở service ứng dụng: một use case là một transaction. Query dùng `readOnly = true`.
- Không gọi HTTP ra ngoài trong transaction: gọi adapter nhạc trước, xong rồi mới ghi DB.
- Chống N+1 bằng fetch join, `@EntityGraph` hoặc projection record. Danh sách lớn phải phân trang.
- Entity chỉ nằm trong `persistence/`; ra khỏi module thì dùng record.
- Migration: `references/migrations.md` (ticket DB-* bắt buộc đọc).

## Realtime và đồng thời (room, chat, voice)
`references/realtime-room.md`: hàng đợi lệnh cho mỗi phòng (actor), hẹn giờ, xác thực STOMP, `serverTime`, test đồng thời.

## Cấu hình
Đặt trong `backend/src/main/resources/config/<module>.yml`, đọc bằng `@ConfigurationProperties` (record, `@Validated`). Không thêm vào `application.yml` (file đó là hot-shared).

## Test
`references/testing.md`. Tối thiểu cần: unit cho logic; integration Testcontainers PostgreSQL cho API/DB; đủ mục "Test bắt buộc" của ticket.

## Spike (SPK-*)
Code đặt trong `spikes/<tên>/`, được phép viết nhanh, nhưng kết quả đo phải tái hiện được (có script và số liệu). Đầu ra chính là ADR (skill `meloo-docs`).
