# AGENTS.md — Meloo

Luật chung cho mọi coding agent (Codex, Claude Code) trong repo này. File này được nạp ở mọi phiên nên chỉ chứa luật. Cách làm chi tiết nằm trong skill ở `.claude/skills/`; Codex thấy cùng nội dung qua `.agents/skills/`.

## Dự án trong 30 giây
Meloo là web game đoán tên bài hát. Mỗi vòng mở dần đoạn preview 2 → 5 → 10 → 20 → 30 giây, đoán càng sớm càng nhiều điểm. Có chơi đơn, thử thách ngày và phòng 2–5 người realtime. Đây là đồ án môn học, một người làm cùng hai agent.

Nguyên tắc không được phá:
- Server là trọng tài: đáp án, điểm và đồng hồ do backend quyết. Không API, sự kiện hay log nào lộ đáp án trước khi vòng kết thúc.
- Mỗi phòng xử lý lệnh tuần tự trong một hàng đợi riêng. Mọi thay đổi trạng thái phòng đi qua hàng đợi đó.
- Đoạn nhạc chỉ cấp qua token mờ (HMAC, hết hạn ngắn); URL không chứa id hay tên bài.
- Quy tắc nghiệp vụ BR-01…BR-29 trong `docs/srs/02-business-rules.md` là nguồn sự thật. Chưa rõ thì hỏi Thái, không tự đặt quy tắc mới.

| Thư mục | Nội dung |
|---|---|
| `backend/` | Java 21, Spring Boot 4.x (Jackson 3, Spring Security 7), Spring Modulith, PostgreSQL 17 + Flyway |
| `frontend/` | React + TypeScript strict + Vite, Tailwind + shadcn/ui, TanStack Query, Zustand, Web Audio, STOMP |
| `contracts/` | `openapi.yaml` (REST), `asyncapi.yaml` (STOMP): nguồn sinh code cho cả hai phía |
| `infra/` | Docker Compose, Caddy, script AWS. Frontend deploy lên Vercel, backend lên EC2 + RDS |
| `docs/` | Tài liệu môn học: SRS, UML, ADR, test, quy trình |
| `spikes/` | Code thử nghiệm, không đưa vào production |

## Vai trò
| Ai | Làm | Review |
|---|---|---|
| Thái | Product Owner + Tech Lead. Người duy nhất merge PR và đổi trạng thái Jira | Nghiệm thu cuối |
| Codex | Backend Spring Boot, CI/CD, hạ tầng, E2E | PR của Claude |
| Claude | Frontend, hợp đồng API, nháp tài liệu, vài ticket backend thuần logic | PR của Codex |

Người viết khác người review, luôn luôn.

## Việc gì cũng bắt đầu bằng skill
| Việc | Skill | Cách gọi |
|---|---|---|
| Làm, tiếp tục, sửa theo review hoặc sửa CI đỏ cho một ticket | `meloo-ticket` | Claude `/meloo-ticket MEL-45` · Codex `$meloo-ticket MEL-45` |
| Review PR của agent kia | `meloo-review` | Claude `/meloo-review 12` · Codex `$meloo-review 12` |

`meloo-ticket` tự chọn skill chuyên môn theo lane của ticket: `meloo-backend`, `meloo-frontend`, `meloo-contract`, `meloo-infra`, `meloo-testing`, `meloo-docs`. QA dùng `meloo-qa-check`.

## 10 luật chống xung đột
1. Một ticket = một worktree = một branch = một PR. Không gộp hai ticket. Ngoại lệ duy nhất là ticket fan-out (có cả `agent-claude` và `agent-codex`, VD FE-GAME-01): mỗi agent làm một bản, một PR; Thái giữ một bản và đóng bản kia.
2. Chỉ bắt đầu khi mọi ticket phụ thuộc đã merge vào `main`.
3. Chỉ sửa file trong "Phạm vi file" của ticket; test tương ứng được tính là trong phạm vi. Cần sửa file khác thì dừng, ghi vào PR, không tự sửa.
4. Khóa module: hai ticket không chạy cùng lúc nếu Phạm vi file trùng thư mục (VD cùng `room/lobby`). Cùng module nhưng khác thư mục con (`game/domain` và `game/persistence`) thì được.
5. Khóa file nóng: mỗi label `hot-*` chỉ một ticket In Progress được giữ (bảng dưới).
6. Hợp đồng trước: API chỉ đổi qua ticket `CT-xx`. Sau khi CT merge, backend và frontend của tính năng chạy song song.
7. Khung dựng sẵn: thư mục module, `api/`, `events/`, route, file i18n, file cấu hình module đã có từ S0. Ticket sau chỉ thêm file trong thư mục của mình.
8. Tối đa 2 agent cùng lúc (1 Codex + 1 Claude), kể cả phiên review.
9. Sau khi một PR merge, worktree đang mở rebase lên `main` rồi `git push --force-with-lease` trên branch của mình.
10. Agent không merge, không approve, không đổi Jira. Chỉ Thái bấm merge.

## File nóng
| Label | File | Ghi chú |
|---|---|---|
| `hot-contract` | `contracts/openapi.yaml`, `contracts/asyncapi.yaml` | CT-01, CT-02, CT-03 |
| `hot-migration` | `backend/src/main/resources/db/migration/**` | Số version cấp sẵn trong DES-01 |
| `hot-pom` | `backend/pom.xml` | Thư viện đã khai báo sẵn ở SET-03 |
| `hot-fe-deps` | `frontend/package.json`, `pnpm-lock.yaml` | Thư viện đã cài sẵn ở SET-04 |
| `hot-fe-app` | `frontend/src/app/**` | Router, provider, layout |
| `hot-shared` | `backend/src/main/java/com/meloo/shared/**`, `backend/src/main/resources/application.yml` | |
| `hot-ci` | `.github/**` | |
| (harness) | `AGENTS.md`, `CLAUDE.md`, `.claude/**`, `.agents/**`, `.codex/**`, `.mcp.json` | Chỉ sửa khi ticket ghi rõ |

## Git và PR
- Branch: `<type>/MEL-<số>-<mô-tả-ngắn>`, với type thuộc `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `ci`, `spike`.
- Commit theo Conventional Commits, nhỏ, commit nào cũng build được.
- Tiêu đề PR (cũng là commit sau squash): `MEL-<số> <type>(<module>): <mô tả tiếng Việt>`, ví dụ `MEL-55 feat(room): sảnh chờ và nút sẵn sàng`.
- PR khoảng ≤ 400 dòng thay đổi, không tính code sinh tự động. Điền đủ template, nhất là khối `meloo:scope` và bảng bằng chứng AC.
- Không bao giờ `--force` trần. Chỉ dùng `--force-with-lease` trên branch của ticket.

## Definition of Done (bản ngắn)
- Đạt toàn bộ Acceptance Criteria, PR có bằng chứng cho từng AC.
- Có test: unit cho logic, integration (Testcontainers) cho API/DB, E2E cho luồng chính; coverage domain ≥ 80%.
- CI xanh: build, format, lint, typecheck, test, Modulith, gitleaks.
- Không sửa ngoài phạm vi; không còn TODO, log debug hay secret.
- Agent kia đã review, không còn Blocker/Major; Thái đã nghiệm thu.
- Đủ i18n vi + en (frontend). Tài liệu cần cập nhật được ghi trong PR.

## Cấm
- `gh pr merge`, `gh pr review --approve`, push lên `main`, `git push --force`. Repo đã chặn sẵn bằng `.claude/settings.json` và `.codex/rules/`.
- Ghi vào Jira dưới mọi hình thức: đổi trạng thái, sửa, comment, tạo ticket. Jira chỉ để đọc; cần ticket mới thì soạn nội dung để Thái tạo.
- Đọc hoặc commit secret (`.env`, khóa, token). Biến môi trường mẫu để ở `.env.example`.
- Tắt test, hoặc hạ ngưỡng lint/coverage cho CI xanh.
- Thêm thư viện khi ticket không giữ `hot-pom` hoặc `hot-fe-deps`.

## Lệnh build/test
Lệnh chính xác nằm trong `backend/AGENTS.md` và `frontend/AGENTS.md` (tạo ở SET-03, SET-04). Đọc file đó trước khi chạy. Mặc định:
- Backend: `cd backend && ./mvnw spotless:apply verify`
- Frontend: `cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build`

## Giao tiếp
- Trả lời Thái bằng tiếng Việt, giữ thuật ngữ kỹ thuật tiếng Anh. Code, tên biến và comment trong code viết bằng tiếng Anh.
- Khi đề xuất, giải thích vì sao. Có nhiều cách làm thì so sánh ngắn rồi chọn một.
- Không tự đổi kiến trúc hay làm thay đổi lớn ngoài ticket: đề xuất trước, chờ Thái quyết.
- Thiếu thông tin để kết luận thì nói rõ thiếu gì, không đoán.
