# Docker Compose Services & Scripts

Thư mục này chứa cấu hình Compose và kịch bản deploy cho các môi trường triển khai của Meloo.

## Môi trường Production (`docker-compose.prod.yml`)

File cấu hình chính được workflow CD (`deploy-backend.yml`) đồng bộ và sử dụng trên EC2 (`/opt/meloo/docker-compose.yml`):

- **Backend (`meloo-backend`)**:
  - Image từ GHCR: `${BACKEND_IMAGE:-ghcr.io/thaidevv/meloo-backend:latest}`
  - JVM tối ưu cho EC2 t3.micro (1 GB RAM): `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`
  - Healthcheck tự động gọi `/actuator/health/readiness`
  - Giới hạn log daemon: `max-size: 10m`, `max-file: 3`
  - Mạng nội bộ cách ly `meloo-internal`, chỉ expose port `8080` cho reverse proxy (tuyệt đối không mở `ports:` ra host để ngăn bypass proxy và giả mạo IP - MEL-127)
- **Reverse Proxy Caddy (`meloo-caddy`)**:
  - Tự động cấp và gia hạn SSL/TLS cho `$API_DOMAIN`
  - Reverse proxy tới backend cổng 8080 (hỗ trợ WebSocket/STOMP)
  - Ghi đè `header_up X-Forwarded-For {remote_host}` và không đặt `trusted_proxies` để đảm bảo backend nhận đúng IP kết nối thực tế, bảo vệ giới hạn tạo khách 5 lần/giờ/IP (MEL-127)
  - Security headers: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy
  - Mount volume `caddy_data` và `caddy_config` để lưu trữ chứng chỉ SSL bền vững

## Kịch bản Deploy & Rollback (`deploy-ec2.sh`)

Kịch bản bash được đồng bộ lên EC2 (`/opt/meloo/deploy-ec2.sh`) để điều phối việc cập nhật backend:
- Lưu trữ image tag hiện tại (`PREV_IMAGE`).
- Cập nhật `.env` và thực hiện `docker compose pull backend`.
- Khởi động lại service và thăm dò `/actuator/health` trong tối đa 150 giây (30 lần x 5s).
- Nếu gặp sự cố ở bất kỳ bước nào, tự động phục hồi `.env` về `PREV_IMAGE`, khởi động lại bản cũ và thoát lỗi (`exit 1`) để báo đỏ workflow.

## Bật đăng nhập Google trên production (MEL-113)

Backend đã hỗ trợ Google OAuth, nhưng mặc định tắt. Khi tắt, request tới
`/api/v1/auth/oauth2/google` đi vào endpoint mặc định sinh từ hợp đồng và trả HTTP 501.
Compose phải truyền các biến OAuth vào container; chỉ khai báo trong `.env` chưa đủ.

1. Tạo OAuth client loại **Web application** trong Google Cloud Console. Khai báo
   redirect URI khớp code hiện tại:
   `https://api.meloo.id.vn/api/v1/auth/oauth2/google/callback`.
   Nếu dùng domain khác, thay origin backend tương ứng. Local dùng
   `http://localhost:8080/api/v1/auth/oauth2/google/callback`.
   Đường dẫn `/login/oauth2/code/google` trong mô tả Jira cũ không còn đúng.
2. Điền trực tiếp vào `/opt/meloo/.env` trên EC2 (không gửi credentials qua chat):

   ```dotenv
   MELOO_GOOGLE_OAUTH_ENABLED=true
   MELOO_GOOGLE_CLIENT_ID=<Google web client ID>
   MELOO_GOOGLE_CLIENT_SECRET=<Google web client secret>
   MELOO_SPA_ORIGIN=https://meloo.id.vn
   ```

   `MELOO_SPA_ORIGIN` là origin frontend, không có path. Cấu hình
   `MELOO_CORS_ALLOWED_ORIGINS` cũng phải chứa origin này. Đặt quyền file bằng
   `chmod 600 /opt/meloo/.env`. Khi bật OAuth mà thiếu credentials, backend từ chối
   khởi động thay vì chạy với cấu hình không hợp lệ.
3. Sau khi deploy Compose mới, chạy trên EC2:

   ```sh
   cd /opt/meloo
   docker compose up -d backend
   ```

   Compose đặt `SERVER_FORWARD_HEADERS_STRATEGY=native` để Spring dùng HTTPS
   và host công khai từ `X-Forwarded-Proto`/`X-Forwarded-Host` do Caddy thiết lập
   khi tạo callback. Native Tomcat không dùng header `Forwarded` do client tự gửi.
   Backend chỉ được truy cập qua
   Caddy trong mạng Docker nội bộ; không mở cổng 8080 ra Internet.
4. Kiểm tra bước chuyển sang Google, không cần đăng nhập:

   ```sh
   curl -sS -D - -o /dev/null 'https://api.meloo.id.vn/api/v1/auth/oauth2/google?mode=login&returnTo=%2Fquick-match'
   ```

   Mong đợi HTTP 302 tới `https://accounts.google.com/…`, trong `Location` có
   `redirect_uri=https%3A%2F%2Fapi.meloo.id.vn%2Fapi%2Fv1%2Fauth%2Foauth2%2Fgoogle%2Fcallback`.
   Không chia sẻ giá trị cookie/state từ response.
5. Đăng nhập từ frontend và kiểm tra quay về `/quick-match`, có session người dùng.
   Trước beta công khai, hoàn tất consent screen **In production** theo MEL-113,
   thử với hai tài khoản ngoài danh sách test. PR cấu hình không thay thế bước nghiệm thu này.

Nếu cần tắt lại, đặt `MELOO_GOOGLE_OAUTH_ENABLED=false` rồi chạy
`docker compose up -d backend`; đăng nhập Google sẽ trở lại HTTP 501.
