# Docker Compose Services & Scripts

Thư mục này chứa cấu hình Compose và kịch bản deploy cho các môi trường triển khai của Meloo.

## Môi trường Production (`docker-compose.prod.yml`)

File cấu hình chính được workflow CD (`deploy-backend.yml`) đồng bộ và sử dụng trên EC2 (`/opt/meloo/docker-compose.yml`):

- **Backend (`meloo-backend`)**:
  - Image từ GHCR: `${BACKEND_IMAGE:-ghcr.io/thaidevv/meloo-backend:latest}`
  - JVM tối ưu cho EC2 t3.micro (1 GB RAM): `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`
  - Healthcheck tự động gọi `/actuator/health/readiness`
  - Giới hạn log daemon: `max-size: 10m`, `max-file: 3`
  - Mạng nội bộ cách ly `meloo-internal`, chỉ expose port `8080` cho reverse proxy
- **Reverse Proxy Caddy (`meloo-caddy`)**:
  - Tự động cấp và gia hạn SSL/TLS cho `$API_DOMAIN`
  - Reverse proxy tới backend cổng 8080 (hỗ trợ WebSocket/STOMP)
  - Security headers: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy
  - Mount volume `caddy_data` và `caddy_config` để lưu trữ chứng chỉ SSL bền vững

## Kịch bản Deploy & Rollback (`deploy-ec2.sh`)

Kịch bản bash được đồng bộ lên EC2 (`/opt/meloo/deploy-ec2.sh`) để điều phối việc cập nhật backend:
- Lưu trữ image tag hiện tại (`PREV_IMAGE`).
- Cập nhật `.env` và thực hiện `docker compose pull backend`.
- Khởi động lại service và thăm dò `/actuator/health` trong tối đa 150 giây (30 lần x 5s).
- Nếu gặp sự cố ở bất kỳ bước nào, tự động phục hồi `.env` về `PREV_IMAGE`, khởi động lại bản cũ và thoát lỗi (`exit 1`) để báo đỏ workflow.
