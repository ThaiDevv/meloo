# Meloo

Web game đoán tên bài hát. Mỗi vòng mở dần đoạn preview 2 → 5 → 10 → 20 → 30 giây, đoán càng sớm càng nhiều điểm. Có chơi đơn, thử thách ngày và phòng 2–5 người chơi realtime.

Đồ án môn học: một người (Thái) làm cùng hai coding agent (Claude Code và Codex).

## Cấu trúc repo
| Thư mục | Nội dung |
|---|---|
| `backend/` | Java 21, Spring Boot 4.x, Spring Modulith, PostgreSQL 17 + Flyway (dựng ở SET-03) |
| `frontend/` | React + TypeScript + Vite, Tailwind + shadcn/ui (dựng ở SET-04) |
| `contracts/` | `openapi.yaml` (REST) và `asyncapi.yaml` (STOMP), nguồn sinh code cho cả hai phía |
| `infra/` | Docker Compose, Caddy, script AWS |
| `docs/` | Tài liệu môn học: nghiệp vụ, SRS, kiến trúc, UML, test, quy trình, runbook |
| `spikes/` | Code thử nghiệm, không đưa vào production |

## Làm việc với agent
- Luật chung cho mọi agent: [`AGENTS.md`](AGENTS.md). Claude Code nạp qua [`CLAUDE.md`](CLAUDE.md).
- Hướng dẫn dùng harness (skill, agent, chặn cứng, một ngày làm việc): [`docs/process/harness.md`](docs/process/harness.md).
- Mỗi ticket Jira `MEL-<số>` là một worktree, một branch, một PR. Agent làm ticket bằng skill `meloo-ticket` và review chéo bằng `meloo-review`; chỉ Thái merge.

## Chạy dự án
Lệnh build và test của từng phía nằm trong `backend/AGENTS.md` và `frontend/AGENTS.md` (có từ SET-03, SET-04).
