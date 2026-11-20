# HTTPS hết hạn hoặc gia hạn thất bại

Mục đích: khôi phục TLS hợp lệ cho API; điều kiện: SSH EC2, DNS/Cloudflare và quyền xem Caddy.

1. Ghi UTC, lỗi browser/UptimeRobot; kiểm expiry/issuer từ máy ngoài:
   `openssl s_client -connect api.meloo.id.vn:443 -servername api.meloo.id.vn </dev/null 2>/dev/null | openssl x509 -noout -dates -issuer`.
2. Trên EC2: `cd /opt/meloo`; `docker compose ps caddy`; `docker logs --since 30m meloo-caddy`.
   Kiểm DNS A/AAAA đúng host, clock (`timedatectl status`), cổng 80/443 và rule SG/firewall.
   Nếu qua Cloudflare, kiểm cả edge certificate và origin; giữ Full (strict).
3. Khôi phục DNS/firewall đúng cấu hình [AWS](aws-setup.md), kiểm API_DOMAIN/ACME_EMAIL trong phiên riêng;
   `docker compose up -d caddy`. Caddy tự gia hạn; giữ volume `caddy_data`/`caddy_config`,
   không xóa certificate để thử lại liên tục vì có rate limit ACME.
4. Kiểm `curl --fail --max-time 10 https://api.meloo.id.vn/actuator/health`, issuer/expiry mới và email recovery.
   Không dùng `curl -k` làm bằng chứng, không tắt TLS để phục hồi tạm.

Quay lại: hoàn tác thay đổi DNS/Caddy gần nhất nếu sai; giữ volume và cấu hình domain cũ hợp lệ.
Chi phí: Caddy/ACME không phí chứng chỉ; không tự mua certificate/nâng gói.
Biên bản: UTC, expiry/issuer trước/sau, thay đổi DNS/SG, health (không đính private key).
