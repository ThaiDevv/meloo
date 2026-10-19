# Hạ tầng

| Thành phần | Nơi chạy |
|---|---|
| Backend | Docker Compose trên EC2, Caddy làm reverse proxy và cấp HTTPS |
| Cơ sở dữ liệu | PostgreSQL 17 trên RDS |
| Frontend | Vercel (production lấy từ `main`, preview cho mỗi PR) |
| Image | GHCR |
| DNS | Cloudflare |

- Thư mục này chứa file Compose, cấu hình Caddy và script AWS (từ INF-01, INF-02). Runbook nằm ở `docs/runbooks/`.
- Không commit secret. Biến môi trường mẫu để trong `.env.example`; giá trị thật nằm ở GitHub Secrets và file `.env` trên EC2.
- Quy ước CI/CD, deploy và giám sát: skill `meloo-infra`.
