# Harness agent của Meloo — hướng dẫn cho Thái

Harness là bộ agent và skill nằm ngay trong repo. Claude Code và Codex tự nạp nó, nên ticket nào cũng chạy theo cùng một quy trình mà bạn không phải chép-dán prompt.

| Thành phần | Vị trí | Dùng cho |
|---|---|---|
| Luật chung | `AGENTS.md` (Codex đọc trực tiếp; Claude đọc qua `CLAUDE.md`) | Mọi phiên |
| Skill | `.claude/skills/` (Codex thấy qua symlink `.agents/skills/`) | `meloo-ticket`, `meloo-review` và 7 skill chuyên môn |
| Agent Claude | `.claude/agents/` | `meloo-reviewer` (opus), `meloo-qa` (sonnet), `meloo-verifier` (sonnet) |
| Agent Codex | `.codex/agents/` | Ba agent tương đương |
| Chặn cứng | `.claude/settings.json`, `.codex/rules/meloo.rules` | Cấm merge, force push, push lên main, ghi Jira, đọc `.env` |
| Jira chỉ đọc | `.mcp.json` (Claude), `.codex/config.toml` (Codex) | Agent tự đọc ticket |
| Review sâu | `.claude/workflows/meloo-deep-review.js` | Ticket rủi ro cao (tùy chọn) |

Hai skill `meloo-ticket` và `meloo-review` thay cho hai prompt Agent Brief và Review ở Phụ lục B của kế hoạch, nên repo không lưu prompt riêng (không có `docs/process/prompts/`). Muốn đổi cách agent làm việc thì sửa skill, rồi chạy `bash .claude/skills/meloo-ticket/scripts/harness-lint.sh`.

## Cài một lần (SET-01)
1. Cài và đăng nhập `gh`, Claude Code, Codex CLI; mở repo trong Orca.
2. Jira chỉ đọc cho agent:
   - Claude Code: mở phiên trong repo, đồng ý dùng server `atlassian` của `.mcp.json`, gõ `/mcp` rồi đăng nhập Atlassian.
   - Codex: khi được hỏi có tin cậy (trust) thư mục dự án không thì chọn Yes, rồi chạy `codex mcp login atlassian`.
   - Đừng thêm server Atlassian ở cấp người dùng (user scope): bản đó không bị chặn công cụ ghi.
3. Kiểm tra: trong Claude gõ `/` phải thấy `meloo-ticket`, `meloo-review`; trong Codex gõ `/skills` phải thấy các skill `meloo-*`.
4. Tùy chọn: trong Claude Code vào `/config`, bật Dynamic workflows để review sâu chạy bằng `/meloo-deep-review`. Không bật thì skill tự chạy bằng subagent.
5. Thiếu symlink `.agents/skills/` (VD sau khi chép tay) thì chạy `bash .claude/skills/meloo-ticket/scripts/link-agent-skills.sh`.

## Một ngày làm việc
| Bước | Bạn làm | Gõ trong Orca |
|---|---|---|
| 1 | Chọn tối đa 2 ticket đạt DoR trên board | |
| 2 | Mỗi ticket một worktree; kéo ticket sang In Progress | Codex `$meloo-ticket MEL-45` · Claude `/meloo-ticket MEL-47` |
| 3 | Agent báo đã mở PR → kéo ticket sang In Review | |
| 4 | Mở phiên review bằng agent còn lại, trong worktree của PR | Claude `/meloo-review 12` · Codex `$meloo-review 13` |
| 5 | Còn Blocker/Major → quay lại phiên của tác giả | `/meloo-ticket MEL-47 sửa theo review` |
| 6 | Hết Blocker/Major → bạn nghiệm thu (backend: đọc code, chạy test; frontend: mở preview Vercel), squash merge (giữ tiêu đề PR làm tiêu đề commit, có tiền tố `MEL-<số>`), chuyển Done | |
| 7 | Các worktree còn mở rebase lên main | `/meloo-ticket MEL-45 rebase` |

## Khi gặp vấn đề
| Hiện tượng | Nên làm |
|---|---|
| Agent đòi sửa file ngoài phạm vi | Đúng luật. Bạn quyết: mở rộng phạm vi (sửa ticket) hoặc tạo ticket mới |
| Agent hỏi về quy tắc nghiệp vụ | Trả lời; nếu là quy tắc mới thì cập nhật BR trong SRS |
| Review báo lỗi bạn thấy vô lý | Bảo reviewer "chứng minh B1 bằng test". Verifier đã lọc, nhưng vẫn có thể sai |
| Hết hạn mức | Giao ticket kế tiếp cho agent còn lại; không đổi agent giữa một ticket đang dở |
| Harness làm sai lặp lại | Ghi vào mục "Góp ý harness" của PR; cuối sprint chạy `harness:evolve` trong Claude Code |
| `deps-merged.sh` báo phụ thuộc CHƯA dù PR đã merge | Tiêu đề commit squash bị mất tiền tố `MEL-<số>`. Script tự tra PR đã merge qua `gh`; không có `gh` thì kiểm trên GitHub. Ticket không có PR (việc của bạn) được báo KIỂM TAY: xem Done trên Jira |
| CI báo `scope-guard` đỏ | Xem file nào ngoài phạm vi trong log; sửa khối `meloo:scope` nếu ticket thật sự cho phép |
