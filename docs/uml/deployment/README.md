# Sơ đồ triển khai hạ tầng (Deployment Diagram)

Thư mục này chứa sơ đồ triển khai hạ tầng thực tế của Meloo, phản ánh các thành phần điện toán đám mây và kết nối mạng đã thiết lập trong INF-01, ADR-006 và các script cấu hình AWS.

## Danh sách sơ đồ

| Tên sơ đồ | File nguồn | Ảnh xuất | Mô tả |
|---|---|---|---|
| **Sơ đồ triển khai hạ tầng** | [`deployment.puml`](deployment.puml) | ![Deployment Diagram](deployment.png) | Triển khai phân tán: Vercel (Edge CDN Static Hosting), Cloudflare (DNS), AWS EC2 Singapore (Docker Compose với Caddy Reverse Proxy & Spring Boot Backend), AWS RDS PostgreSQL 17 (Private Subnet), và TURN Service ngoài. |

## Các thành phần chính trong hạ tầng

1. **Vercel Edge Network:**
   - Hosting giao diện SPA tĩnh (React, TypeScript, Vite).
   - Tích hợp CI/CD tự động deploy nhánh `main` cho môi trường Production và tạo bản xem trước (Preview) cho mỗi Pull Request.
   - Trỏ tên miền chính qua CNAME DNS Cloudflare.

2. **Cloudflare DNS:**
   - Quản lý DNS và phân giải tên miền chính và tên miền phụ API (`api.<domain>`).
   - Cung cấp lớp bảo vệ DDoS lớp mạng cơ bản.

3. **AWS EC2 (Region `ap-southeast-1` - Singapore):**
   - Loại máy: `t3.micro` (1 vCPU, 1 GB RAM vật lý kèm 2 GB swapfile chống OOM, 20 GB gp3 SSD).
   - Security Group `meloo-ec2-sg`: Mở cổng công khai 80 (HTTP) và 443 (HTTPS); cổng 22 (SSH) được giới hạn nghiêm ngặt chỉ từ IP quản trị viên.
   - Chạy Docker Compose gồm 2 container trên mạng bridge nội bộ `meloo-internal`:
     - `meloo-caddy`: Gateway proxy, tự động quản lý chứng chỉ SSL Let's Encrypt, chuyển tiếp yêu cầu HTTP REST và nâng cấp kết nối WebSocket STOMP (WSS).
     - `meloo-backend`: Tiến trình Java 21 Spring Boot 4.x đóng kín trong mạng nội bộ, chỉ expose cổng 8080 cho Caddy, cấu hình `-XX:MaxRAMPercentage=60` để kiểm soát heap RAM.

4. **AWS RDS (PostgreSQL 17):**
   - Đặt trong Private DB Subnet cách ly hoàn toàn khỏi Internet (Publicly Accessible: No).
   - Security Group `meloo-rds-sg`: Chỉ chấp nhận kết nối TCP cổng 5432 bắt nguồn từ `meloo-ec2-sg`.
   - Cơ chế sao lưu tự động (Automated Backups) hàng ngày đảm bảo RPO ≤ 24 giờ (NFR-15).

5. **Dịch vụ WebRTC STUN/TURN ngoài (Metered / Xirsys):**
   - Hỗ trợ thiết lập kênh thoại WebRTC Mesh P2P giữa các trình duyệt.
   - Chuyển tiếp luồng âm thanh thoại qua TURN Relay (UDP/TCP 3478, TURNS 5349) khi người chơi nằm sau mạng tường lửa hoặc NAT đối xứng (Symmetric NAT).

## Cách biên dịch lại sơ đồ
Sử dụng Docker image `plantuml/plantuml`:

```bash
docker run --rm -v "$PWD":/w:z plantuml/plantuml -tpng /w/docs/uml/deployment/*.puml
```
