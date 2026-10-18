# Test backend

| Loại | Dùng cho | Công cụ |
|---|---|---|
| Unit | Engine luật chơi, logic thuần, mapper | JUnit 5 (`@ParameterizedTest` cho bảng BR), AssertJ |
| Integration | API, repository, migration, listener | `@SpringBootTest` + Testcontainers PostgreSQL 17 (`@ServiceConnection`) |
| Module | Ranh giới module | `ApplicationModules.of(MelooApplication.class).verify()`, `@ApplicationModuleTest` |
| Adapter ngoài | API nhạc | WireMock hoặc MockWebServer: thành công, 5xx, timeout, 429 |
| Realtime | STOMP, đồng thời | `WebSocketStompClient`, Awaitility |

- Tên test nói rõ hành vi: `guessWrongAtLevel5_dropsToLevel10()` hoặc `@DisplayName("Đoán sai ở mức 5s thì xuống mức 10s")`.
- Không dùng H2. Không `Thread.sleep`.
- Dữ liệu test tạo ngay trong test (builder), không phụ thuộc thứ tự chạy.
- Assertion kiểm kết quả nghiệp vụ (điểm, trạng thái, sự kiện đã phát), không chỉ "không ném lỗi".
- Coverage: domain ≥ 80% (engine ≥ 90%), đo bằng JaCoCo trong `./mvnw verify`.
