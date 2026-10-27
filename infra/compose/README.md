# Docker Compose Services

Thư mục này chứa cấu hình Compose cho các môi trường triển khai của Meloo.

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
