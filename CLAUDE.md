@AGENTS.md

## Riêng cho Claude Code
- Subagent của harness nằm ở `.claude/agents/`: `meloo-reviewer` (review), `meloo-qa` (QA khớp nối), `meloo-verifier` (kiểm chứng một nhận xét). Các skill tự gọi chúng, không cần gọi tay.
- Gói Pro dùng chung hạn mức với claude.ai. Không tự bật workflow lớn hay ultracode khi Thái không yêu cầu. Workflow `/meloo-deep-review` chỉ dành cho ticket review sâu.
- Thấy harness sai lặp lại (agent hiểu sai, skill thiếu bước) thì ghi vào mục "Góp ý harness" của PR để Thái đưa vào retro (`harness:evolve`).

## Harness: đội agent Meloo

**Mục tiêu:** mọi ticket MEL chạy cùng một quy trình (DoR → code → kiểm tra phạm vi → QA → review chéo có kiểm chứng → PR) cho cả Claude Code lẫn Codex, không xung đột và không bỏ sót AC.

**Khi nào gọi:** yêu cầu làm, tiếp tục, sửa theo review hoặc sửa CI cho một ticket MEL thì dùng skill `meloo-ticket`. Review PR thì dùng `meloo-review`. Câu hỏi đơn giản thì trả lời thẳng, không cần skill.

**Lịch sử thay đổi:**
| Ngày | Thay đổi | Đối tượng | Lý do |
|---|---|---|---|
| 2026-10-21 | Chuẩn hóa `.claude/skills` làm nguồn và `.agents/skills` chỉ chứa symlink; giữ an toàn thư mục thật khi liên kết lại | `orca-cli`, `link-agent-skills.sh` | Orca cài skill vào `.agents/skills`, tạo liên kết ngược khiến `harness-lint.sh` báo lỗi |
| 2026-10-18 | Dựng harness v2 lần đầu | Toàn bộ | Thay 2 prompt chép-dán ở Phụ lục B bằng agent + skill dùng chung cho Claude Code và Codex |
| 2026-10-18 | Chỉ bác bỏ nhận xét khi có dòng code chặn lỗi; thiếu code thì thành câu hỏi; ghi lại nhận xét đã bỏ; thêm mặc định fetch của JPA vào checklist | `meloo-verifier`, `meloo-review`, `checklist-backend.md`, workflow `meloo-deep-review` | Thử review PR mẫu: bản có skill bác nhầm một lỗi N+1 thật vì mapping entity không có trong repo |
| 2026-10-18 | `check-scope.sh` bỏ qua hàng rào code trước khi xóa backtick; hỗ trợ dòng loại trừ `!` | `meloo-ticket` | Thử review phát hiện dòng "```text" bị đọc thành glob; ticket có phạm vi "(trừ X)" cần kiểm được |
| 2026-10-19 | Rà harness theo repo thật (SET-02): `deps-merged.sh` tra PR đã merge qua `gh` khi commit squash mất tiền tố MEL và báo KIỂM TAY cho ticket owner-me; Codex chặn thêm `git push -u/--force-with-lease origin main`; luật giữ tiền tố MEL khi squash; `meloo-ticket` chỉ ghi `_workspace/` sau khi DoR đạt | `meloo-ticket`, `.codex/rules/`, `AGENTS.md`, `docs/process/harness.md` | Commit squash của MEL-18 mất tiền tố nên `deps-merged.sh` báo nhầm là chưa merge; MEL-16 không có PR nên luôn báo CHƯA; AC của SET-02 yêu cầu dừng ở DoR mà không tạo file |
| 2026-10-19 | Cấm dòng ghi công agent (`Co-Authored-By`, "Generated with …") trong commit, PR, comment; tắt `attribution` của Claude Code và `commit_attribution` của Codex | `AGENTS.md`, `.claude/settings.json`, `.codex/config.toml` | Thái yêu cầu: tác giả là Thái, agent đã được ghi ở mục Ticket của PR |
