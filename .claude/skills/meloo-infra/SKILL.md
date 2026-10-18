---
name: meloo-infra
description: "Quy ước CI/CD và hạ tầng của Meloo: GitHub Actions (lọc theo đường dẫn để tiết kiệm 2.000 phút/tháng, scope-guard, harness-lint, gitleaks), Docker Compose và Caddy trên EC2, RDS, Vercel, Cloudflare DNS, GHCR, secret, runbook và giám sát. Dùng khi làm hoặc review ticket SET-02, SET-05, INF-*, REL-01, hoặc khi sửa .github/, infra/, Dockerfile."
---

# meloo-infra

## GitHub Actions
- Chỉ ticket giữ `hot-ci` được sửa `.github/**`.
- Lọc theo đường dẫn (`paths` hoặc `dorny/paths-filter`): job backend, frontend, contracts, e2e chỉ chạy khi thư mục liên quan đổi. Repo private chỉ có 2.000 phút mỗi tháng.
- `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true }`.
- `permissions:` tối thiểu ở mức workflow (VD `contents: read`), chỉ nâng ở job cần (`packages: write` khi đẩy lên GHCR).
- Ghim version action (tag chính; action bên thứ ba thì cân nhắc ghim SHA). Cache Maven (`actions/setup-java`) và store của pnpm.
- Giữ tên job ổn định, vì Thái đặt required checks theo tên job.
- Job `scope-guard` (SET-05): ghi `github.event.pull_request.body` ra file, chạy `bash .claude/skills/meloo-ticket/scripts/check-scope.sh --pr-body <file> --base origin/${{ github.base_ref }}`. Đỏ khi PR chạm file ngoài phạm vi hoặc file nóng không khai báo. Kích hoạt cả khi PR `edited` và `synchronize`.
- Job `harness`: chạy `bash .claude/skills/meloo-ticket/scripts/harness-lint.sh` khi `.claude/**`, `.agents/**`, `.codex/**`, `AGENTS.md` hoặc `CLAUDE.md` đổi.
- gitleaks cho mọi PR. Dependabot chạy hằng tuần cho Maven, npm, Actions.

## Deploy (INF-02)
- Merge vào main → build image backend (tag `sha-<7 ký tự>` và `latest`; bản phát hành thêm `vX.Y.Z`) → đẩy GHCR → SSH vào EC2 → `docker compose pull && docker compose up -d` → kiểm tra `/actuator/health`, có thử lại. Lỗi thì workflow báo đỏ và quay lại tag trước.
- SSH: khóa riêng cho deploy lưu trong GitHub Secrets; `known_hosts` cố định, không tắt StrictHostKeyChecking.
- EC2 1 GB RAM: JVM `-XX:MaxRAMPercentage=60`, swap 2 GB, giới hạn log Docker (`max-size`, `max-file`).
- Caddy: HTTPS tự động cho `api.<tên miền>`, `reverse_proxy` tới backend (WebSocket đi qua bình thường), thêm security header.
- Vercel: production lấy từ main; preview cho mỗi PR frontend với `VITE_API_MOCK=true`. CORS backend chỉ mở cho tên miền chính và URL preview của dự án.

## Secret
- Không commit `.env`, khóa hay token. Biến mẫu để trong `.env.example`.
- CI dùng GitHub Secrets. Trên EC2, `.env` đặt quyền chmod 600. Lộ thì xoay vòng ngay.
- Agent không đọc `.env` (repo đã chặn sẵn).

## Việc Thái làm tay (INF-01, INF-03, REL-01)
Agent viết runbook và script; Thái thực hiện trên AWS Console và Cloudflare. Runbook đặt ở `docs/runbooks/<tên>.md`, gồm: mục đích · điều kiện trước · các bước (lệnh copy được) · cách kiểm tra thành công · cách quay lại khi lỗi · chi phí (free tier). Không ghi secret thật vào runbook.

## Giám sát (INF-04)
- Sentry cho cả frontend và backend, gắn version phát hành. SDK đã khai báo sẵn ở SET-03 và SET-04.
- UptimeRobot gọi `/actuator/health` mỗi 5 phút.
- Log backend dạng JSON, có correlation id.
- Runbook sự cố: backend chết, DB đầy, chứng chỉ HTTPS hết hạn, API nhạc lỗi.
