# Chạy Meloo backend ở máy local

## Yêu cầu

- Java 21
- Docker với Compose v2

## Khởi động PostgreSQL 17

Từ thư mục gốc repository:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml ps
```

Giá trị local mặc định là database/user/password `meloo`, cổng `5432`. Có thể đổi
bằng `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`.

## Chạy backend

```bash
cd backend
./mvnw spotless:apply verify
./mvnw spring-boot:run
```

Backend cần các biến môi trường cấu hình (tham khảo mẫu tại `.env.example`):

```text
DB_URL=jdbc:postgresql://localhost:5432/meloo
DB_USERNAME=meloo
DB_PASSWORD=meloo
MELOO_CORS_ALLOWED_ORIGINS=http://localhost:5173
MELOO_JWT_SECRET=meloo-local-development-secret-key-at-least-256-bits-length
```

> **Lưu ý về `MELOO_JWT_SECRET`:** Bắt buộc phải có độ dài tối thiểu 32 byte (256-bit) để ký HMAC-SHA256. Không được dùng chuỗi ngắn hơn hoặc để trống.

`MELOO_CORS_ALLOWED_ORIGINS` nhận danh sách origin phân cách bằng dấu phẩy.

Health probes:

- `http://localhost:8080/actuator/health/liveness`
- `http://localhost:8080/actuator/health/readiness`

## Dừng hoặc làm sạch dữ liệu

```bash
docker compose -f infra/docker-compose.yml down
```

Chỉ khi chủ động muốn xóa toàn bộ dữ liệu PostgreSQL local:

```bash
docker compose -f infra/docker-compose.yml down --volumes
```
