---
name: meloo-ticket
description: "Điều phối trọn vòng một ticket Jira MEL của dự án Meloo, từ đọc ticket tới mở PR: đọc ticket (Jira chỉ đọc hoặc nội dung được dán), kiểm tra Definition of Ready, phụ thuộc và khóa file nóng, lập kế hoạch, gọi skill chuyên môn để làm, tự kiểm tra phạm vi file và test, giao QA và tự review cho subagent, rồi mở PR đúng mẫu. Dùng khi người dùng nói 'làm ticket MEL-45', 'làm BE-ID-01', 'bắt đầu ticket', 'tiếp tục ticket', 'sửa theo review PR', 'CI đỏ', 'rebase', 'làm lại phần…', 'cập nhật PR', hoặc gọi /meloo-ticket, $meloo-ticket. Không dùng để review PR của agent khác (dùng meloo-review)."
---

# meloo-ticket — điều phối một ticket

Skill này thay prompt "Agent Brief" ở Phụ lục B của kế hoạch. Nó giữ cho mọi ticket đi đúng một đường: ticket → branch → code → kiểm tra → PR → review chéo → Thái merge. Bạn (phiên chính trong worktree của Orca) là người làm ticket. QA và review được giao cho subagent có con mắt mới.

## Chế độ chạy: hỗn hợp
| Bước | Ai làm | Vì sao |
|---|---|---|
| 0–6: đọc ticket → code → tự kiểm tra | Phiên chính | Cần giữ ngữ cảnh, và để Thái chen vào được |
| 7: QA | Subagent `meloo-qa`, gọi một lần | Cần mắt mới, chỉ cần lấy kết quả |
| 8: Tự review (chỉ ticket review sâu) | Subagent `meloo-reviewer` + `meloo-verifier`, hoặc workflow `/meloo-deep-review` nếu Thái đã bật | Kiểm chứng độc lập trước khi tốn một lượt review chéo |
| 9–10: PR và báo cáo | Phiên chính | |

Codex không gọi được subagent (`.codex/agents/` chưa nạp) thì tự làm theo skill `meloo-qa-check` và `meloo-review` ngay trong phiên, và ghi rõ "tự kiểm, không có mắt mới" trong PR.

## Bước 0 — Xác định tình huống
Chạy `git status`, `git branch --show-current`, `gh pr view --json number,state,title 2>/dev/null`, xem có `_workspace/MEL-<số>/` chưa.

| Tình huống | Dấu hiệu | Làm gì |
|---|---|---|
| Lần đầu | Chưa có `_workspace/MEL-<số>/`, chưa có PR | Từ bước 1 |
| Tiếp tục | Có `_workspace/MEL-<số>/`, chưa có PR | Đọc `01_plan.md` và `progress.md`, làm tiếp bước còn dở |
| Sửa theo review | Có PR và có nhận xét mới | Mục "Sửa theo review" |
| CI đỏ | `gh pr checks` có job fail | Mục "Sửa CI đỏ" |
| Rebase | Thái báo có PR khác vừa merge | Mục "Rebase" |
| Làm lại một phần | Thái chỉ ra phần cần làm lại | Chỉ chạy lại các bước liên quan, giữ phần còn lại |

## Bước 1 — Lấy ticket
Lấy theo thứ tự:
1. Jira qua MCP, chỉ đọc: `getJiraIssue` với key `MEL-<số>` trên site `tranvanthai20062-1779090911683.atlassian.net`; lấy summary, description, labels, status. Không gọi bất kỳ công cụ ghi nào (sửa, chuyển trạng thái, comment): luật 10 cấm và repo đã chặn sẵn.
2. Mô tả trong PR, khi đang sửa theo review.
3. Không có hai nguồn trên thì nhờ Thái dán nội dung ticket, kèm labels.

Đổi mã nội bộ (VD `CT-01`) sang key bằng `references/ticket-index.tsv`. Đây là bản chụp backlog, gồm các cột key, mã, tên, labels, phụ thuộc, skill, mức review, người làm. Bảng có thể cũ: khác Jira thì tin Jira.

Khi bước 2 (DoR) đạt thì ghi vào `_workspace/MEL-<số>/` (bước 2 chỉ cần nội dung ticket, chưa cần các file này):
- `00_ticket.md`: nguyên văn ticket kèm labels.
- `scope.txt`: Phạm vi file đã chuẩn hóa, mỗi dòng một glob (quy tắc ngay dưới).
- `labels.txt`: labels, mỗi dòng một label.

Chuẩn hóa Phạm vi file:
- "test tương ứng" của `backend/src/main/java/<đường dẫn>/**` thì thêm `backend/src/test/java/<đường dẫn>/**`.
- Đường dẫn rút gọn bắt đầu bằng tên module backend (VD `game/web/**`, `room/api/**`) nghĩa là `backend/src/main/java/com/meloo/<module>/...`.
- `config/<module>.yml` nghĩa là `backend/src/main/resources/config/<module>.yml`.
- Phần "(trừ X)" ghi thành một dòng `!X` (đường dẫn đầy đủ). `check-scope.sh` coi file khớp dòng `!` là ngoài phạm vi.
- Frontend: test nằm cạnh code trong thư mục feature, nên đã nằm trong glob.
- Không tự thêm thư mục khác. Thiếu thì hỏi Thái.

## Bước 2 — Definition of Ready (cổng chặn)
Chỉ bắt đầu code khi đạt đủ 5 điểm. Thiếu điểm nào thì DỪNG, báo Thái thiếu gì, không code "tạm", không tạo branch hay file nào (kể cả `_workspace/`).
1. Có mục tiêu và Acceptance Criteria kiểm chứng được.
2. Có Phạm vi file.
3. Phụ thuộc đã merge: `bash .claude/skills/meloo-ticket/scripts/deps-merged.sh MEL-31 MEL-38`. Script tìm commit squash trên `origin/main`. Phụ thuộc không có PR (việc của Thái như SET-01, BIZ-…, PM-…, QA-04; ticket theo dõi như BUG-00) thì hỏi Thái hoặc xem trạng thái Done trên Jira.
4. Khóa: có Jira MCP thì chạy JQL `project = MEL AND status = "In Progress"` để biết ticket nào đang chạy. Không được trùng thư mục trong Phạm vi file, không được trùng label `hot-*`. Không có Jira thì hỏi Thái.
5. Vừa sức một phiên: khoảng ≤ 1 ngày, PR ≤ ~400 dòng. Lớn hơn thì đề xuất cách tách rồi chờ Thái.

## Bước 3 — Branch
Tên đúng là `<type>/MEL-<số>-<mô-tả-ngắn>` (ticket thường ghi sẵn). Orca có thể đặt tên branch khác: đổi bằng `git branch -m <tên đúng>` trước commit đầu tiên. Ticket fan-out (có cả `agent-claude` và `agent-codex`) thì thêm hậu tố `-claude` hoặc `-codex`, và không đọc worktree của agent kia.

## Bước 4 — Kế hoạch
Viết `_workspace/MEL-<số>/01_plan.md`, ngắn gọn:
- File sẽ tạo hoặc sửa. File nào cũng phải nằm trong `scope.txt`.
- Bảng AC → test (tên test; unit, integration hay E2E) hoặc bằng chứng khác.
- Rủi ro và quy tắc nghiệp vụ (BR) liên quan. BR nào chưa rõ thì hỏi Thái trước khi code phần đó.

Thái không cần duyệt kế hoạch, trừ khi phải hỏi về nghiệp vụ hoặc phạm vi.

## Bước 5 — Làm
Nạp skill chuyên môn theo lane (labels hoặc mã ticket):

| Label hoặc mã | Skill |
|---|---|
| `be`, `db`, `SPK-01` | `meloo-backend` (ticket DB đọc thêm `.claude/skills/meloo-backend/references/migrations.md`) |
| `fe` | `meloo-frontend` |
| `contract` | `meloo-contract` |
| `infra`, `hot-ci` | `meloo-infra` |
| `qa` (E2E, tải) | `meloo-testing` |
| `doc`, `ARC-*`, `DES-*` | `meloo-docs` |
| `SEC-01` | `meloo-review`, chế độ audit |

Ticket chạm nhiều lane (VD `LEG-01`: fe + doc) thì nạp đủ các skill tương ứng.

Làm từng bước nhỏ và chắc:
- Viết test cho AC trước hoặc cùng lúc với code.
- Commit nhỏ theo Conventional Commits.
- Ghi tiến độ vào `_workspace/MEL-<số>/progress.md` (đã xong gì, còn gì) để phiên sau làm tiếp được.

## Bước 6 — Tự kiểm tra cứng
Chạy rồi sửa đến khi xanh, tối đa 3 vòng. Vẫn đỏ thì dừng và báo nguyên nhân.
1. Phạm vi: `bash .claude/skills/meloo-ticket/scripts/check-scope.sh --scope-file _workspace/MEL-<số>/scope.txt --labels "$(paste -sd, _workspace/MEL-<số>/labels.txt)"`. Có file ngoài phạm vi thì hoàn tác nó (`git checkout origin/main -- <file>`, hoặc xóa file mới tạo), hoặc dừng lại nếu thật sự cần sửa file đó.
2. Build và test của lane, theo lệnh trong `backend/AGENTS.md` hoặc `frontend/AGENTS.md`.
3. Quét diff (`git diff origin/main...HEAD`): không còn `TODO`, `console.log`, `System.out.println`, secret.

## Bước 7 — QA (subagent)
Gọi subagent `meloo-qa` một lần, không đặt `name`. Truyền đường dẫn `_workspace/MEL-<số>/00_ticket.md`, `scope.txt` và khoảng diff `origin/main...HEAD`. Lưu kết quả vào `_workspace/MEL-<số>/02_qa.md`.
- FAIL: sửa rồi gọi QA lại một lần. Vẫn FAIL thì ghi vào PR và báo Thái.
- CHƯA KIỂM: ghi vào PR kèm lệnh để Thái chạy.

## Bước 8 — Tự review (chỉ ticket review sâu)
Mức review ghi ở dòng "Harness" của ticket hoặc trong `ticket-index.tsv`. Nếu không ghi, mặc định là sâu khi ticket chạm module identity, audio, room, chat, voice, admin, hoặc giữ `hot-shared`, `hot-ci`.
- Có workflow (Claude Code, Thái đã bật Dynamic workflows trong `/config`): chạy `/meloo-deep-review` với `{ "range": "origin/main...HEAD", "ticket": "_workspace/MEL-<số>/00_ticket.md" }`.
- Không có workflow: gọi `meloo-reviewer` (truyền diff và ticket). Với mỗi nhận xét Blocker/Major, gọi một `meloo-verifier`, gửi tất cả trong cùng một lượt để chạy song song. Chỉ sửa nhận xét `confirmed`. Nhận xét `uncertain` ghi vào mục "Câu hỏi cho reviewer" của PR.
- Lưu kết quả vào `_workspace/MEL-<số>/03_self_review.md`.

Ticket review chuẩn bỏ qua bước này: review chéo của agent kia là đủ, đỡ tốn hạn mức hai lần.

## Bước 9 — Mở PR
1. `git fetch origin && git rebase origin/main`. Xung đột ở file ngoài phạm vi thì dừng, báo Thái.
2. `git push -u origin HEAD`. Những lần push sau khi rebase: `git push --force-with-lease`.
3. Điền `.github/PULL_REQUEST_TEMPLATE.md` vào `_workspace/MEL-<số>/pr-body.md`:
   - Khối `meloo:scope` chép từ `scope.txt`.
   - Bảng bằng chứng AC lấy từ `02_qa.md`.
   - Ghi tác giả, reviewer, mức review, file nóng đã giữ.
4. `gh pr create --base main --title "MEL-<số> <type>(<module>): <mô tả>" --body-file _workspace/MEL-<số>/pr-body.md`.
5. Không merge, không approve, không đụng Jira.

## Bước 10 — Báo cáo cho Thái
Ngắn gọn, bằng tiếng Việt:
- Link PR, tóm tắt thay đổi, AC nào đã có bằng chứng, AC nào chưa.
- Việc Thái cần làm:
  - Kéo ticket sang In Review.
  - Mở phiên review cho agent còn lại: Claude `/meloo-review <số PR>` hoặc Codex `$meloo-review <số PR>`.
  - Với frontend: mở bản preview Vercel để xem.
- Câu hỏi còn treo, và nội dung soạn sẵn cho ticket bổ sung nếu cần.

## Sửa theo review
1. Đọc nhận xét: `gh pr view <số> --comments`. Nhận xét của reviewer có tiêu đề bắt đầu bằng "Review MEL-".
2. Lập bảng trong `_workspace/MEL-<số>/04_fix_round<k>.md`: mỗi nhận xét → sửa gì, hoặc vì sao không sửa.
3. Sửa hết Blocker và Major; Minor thì sửa nếu rẻ. Không đồng ý với nhận xét nào thì phản hồi bằng bằng chứng, không lặng lẽ bỏ qua.
4. Chạy lại bước 6–7, push, rồi `gh pr comment <số> --body-file <file>` tóm tắt đã sửa những gì.
5. Qua 2 vòng mà vẫn còn Blocker/Major thì dừng, để Thái quyết.

## Sửa CI đỏ
Xem `gh pr checks <số>` và `gh run view <run-id> --log-failed`. Chỉ sửa trong phạm vi. Lỗi do môi trường CI hoặc do file ngoài phạm vi thì báo Thái; không sửa `.github/**` khi ticket không giữ `hot-ci`.

## Rebase
`git fetch origin && git rebase origin/main`, chạy lại bước 6, rồi `git push --force-with-lease`. Xung đột ở file ngoài phạm vi thì dừng, báo Thái.

## Khi nào dừng và hỏi Thái
- Cần sửa file ngoài phạm vi, sửa file nóng mà không giữ label, hoặc thêm thư viện.
- Quy tắc nghiệp vụ mơ hồ hoặc mâu thuẫn với BR.
- Phụ thuộc chưa merge, hoặc khóa đang bị ticket khác giữ.
- Lỗi xác thực, hết hạn mức, bị từ chối quyền: KHÔNG thử lại (thử lại chỉ tốn hạn mức). Ghi trạng thái vào `progress.md`, báo Thái lúc nào hạn mức mở lại nếu biết.

## Thư mục `_workspace/`
Đã có trong `.gitignore`, không commit.
```
_workspace/MEL-<số>/
  00_ticket.md  scope.txt  labels.txt  01_plan.md  progress.md
  02_qa.md  03_self_review.md  04_fix_round<k>.md  pr-body.md
```
Worktree bị xóa thì `_workspace/` mất theo. Dấu vết lâu dài nằm trên PR (mô tả và comment).

## Kịch bản kiểm thử
**Bình thường:** "/meloo-ticket MEL-45" → đọc ticket qua Jira → `deps-merged.sh` báo MEL-37, MEL-36, MEL-44, MEL-31 đã merge → kế hoạch cho 5 AC → code + test → `check-scope.sh` xanh → QA PASS 5/5 → ticket review sâu nên chạy tự review, sửa 1 Major được xác nhận → mở PR "MEL-45 feat(game): API chơi đơn" → báo Thái chuyển In Review và nhờ Claude chạy `/meloo-review`.

**Lỗi:** "/meloo-ticket MEL-56" khi MEL-55 chưa merge → dừng ở bước 2: "Chưa làm được: phụ thuộc BE-ROOM-01 (MEL-55) chưa merge vào main. Gợi ý: làm MEL-57 trước." Không tạo branch, không tạo file, không code.
