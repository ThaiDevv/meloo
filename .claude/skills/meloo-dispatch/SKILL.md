---
name: meloo-dispatch
description: "Coordinator giao việc cho dự án Meloo qua Orca Orchestration: đọc các ticket MEL đang In Progress (hoặc danh sách Thái nhập), mở mỗi ticket một worktree mới, khởi động agent được chỉ định chạy meloo-ticket, giới hạn số agent song song, rồi theo dõi tới khi agent mở PR. Dùng khi người dùng nói 'giao việc', 'mở worktree cho các task in progress', 'dispatch MEL-45 ag', '/meloo-dispatch', '$meloo-dispatch'. Không review (dùng meloo-review-dispatch), không chạy trọn vòng làm + review (dùng meloo-orchestra). Không dùng để tự làm ticket (dùng meloo-ticket)."
---

# meloo-dispatch: mở worktree và giao ticket cho agent

Bạn là **coordinator**. Bạn không làm ticket. Bạn mở worktree, giao việc cho agent khác, ghi trạng thái, rồi báo Thái.

Trước khi làm, đọc 3 file (đường dẫn tính từ gốc repo):
- `.claude/skills/meloo-orchestra/references/state.md`: luật chung, cú pháp đầu vào, `plan.tsv`, cách đọc `worker_done`.
- `.claude/skills/meloo-orchestra/references/orca-commands.md`: lệnh Orca.
- `.claude/skills/meloo-orchestra/references/agents.md`: bảng agent và mẫu spec WORK, CONTINUE.

Làm đúng thứ tự dưới đây. Bước nào gặp điều kiện "dừng" thì dừng, báo Thái, không đoán.

## Bước 1: Môi trường

1. Tìm tên lệnh Orca (`orca-commands.md` mục 0).
2. Chạy kiểm tra ở `orca-commands.md` mục 1. Lỗi thì dừng.
3. Chạy `<ORCA> skills get orchestration --full` và `<ORCA> orchestration worker-start --help`. Đọc lướt để biết cờ hiện hành.
4. Có `_workspace/orchestra/plan.tsv` rồi thì đọc nó. Dòng nào chưa ở trạng thái cuối thì đây là **phiên tiếp tục**: chuyển sang bước 6 với các dòng đó, rồi mới xử lý dòng mới (nếu có).

## Bước 2: Danh sách ticket

- Thái đã nhập dòng `<ticket> <agent làm> [agent review]` → dùng danh sách đó.
- Thái không nhập gì:
  1. Có Jira MCP (chỉ đọc) → JQL `project = MEL AND status = "In Progress" ORDER BY key ASC`, lấy summary và labels.
  2. In bảng `Key | Tên | Labels | Đã có PR?` (kiểm PR như bước 3.2), rồi hỏi Thái: "Nhập mỗi dòng `MEL-<số> <agent làm> <agent review>`". Dừng chờ.
  3. Không có Jira → nhờ Thái nhập danh sách.

## Bước 3: Kiểm tra từng ticket

1. Kiểm tra dòng theo bảng lỗi trong `state.md` (agent hợp lệ, làm ≠ review, In Progress, khóa `hot-*`). Labels lấy từ Jira.
2. Đã có PR chưa: `gh pr list --state open --search "MEL-<số> in:title" --json number,title,headRefName`.
   - Có PR → không giao việc làm. Ghi `pr_open` kèm số PR. Việc review để `meloo-review-dispatch` hoặc `meloo-orchestra` làm.
3. Đã có worktree chưa: `<ORCA> worktree list --repo id:<repoId> --json`, tìm worktree có branch hoặc tên chứa `MEL-<số>` (không phân biệt hoa thường).
   - Có worktree, chưa có PR → giao spec **CONTINUE** vào worktree đó (mở tab, `orca-commands.md` mục 5). Không tạo worktree thứ hai cho cùng ticket (luật 1 ticket = 1 worktree).
   - Không có → giao spec **WORK** ở worktree mới (bước 5).

## Bước 4: Chốt kế hoạch

Ghi `plan.tsv` (tạo mới nếu chưa có), mỗi ticket hợp lệ một dòng, status `queued`. In bảng ngắn cho Thái gồm ticket, agent làm, agent review, cách giao (worktree mới / tiếp tục / bỏ qua vì đã có PR) và các dòng bị bỏ kèm lý do.

Thái đã chỉ định agent cho từng ticket nên **không chờ duyệt**, chạy tiếp ngay. Chỉ dừng hỏi khi có dòng bị bỏ mà Thái có thể muốn sửa (VD làm trùng review); khi đó vẫn chạy các dòng hợp lệ.

## Bước 5: Giao việc

Tạo Run nếu `plan.tsv` chưa có (`orca-commands.md` mục 2). Sau đó, với từng dòng `queued`, lần lượt theo thứ tự nhập, chừng nào số dòng `working`/`reviewing`/`fixing` còn nhỏ hơn `song song`:

1. Bỏ qua nếu ticket giữ label `hot-*` trùng với một dòng đang chạy.
2. Điền mẫu WORK (hoặc CONTINUE) trong `agents.md`, ghi ra `_workspace/orchestra/spec-MEL-<số>-work.md`.
3. `task-create` với title `MEL-<số> · làm`.
4. WORK: `worker-start ... --worktree new-child --name mel-<số> --agent <agent>` (`orca-commands.md` mục 4).
   CONTINUE: mở tab bằng lệnh của agent làm rồi `dispatch --inject` (mục 5).
5. Ghi `task`, `dispatch`, `worktree_id`, `worktree_path`, `doer_term` vào `plan.tsv`, status `working`.
6. `worktree set --comment "MEL-<số> · <agent> đang làm"`.

Lệnh lỗi ở một ticket: ghi `note`, chuyển `blocked`, làm tiếp ticket sau. Lỗi xác thực hoặc hạn mức của một agent: mọi dòng còn `queued` của agent đó chuyển `blocked`, note "agent hết hạn mức/không đăng nhập được".

## Bước 6: Theo dõi

Có `không chờ` thì bỏ qua bước này, sang bước 7.

Lặp:
1. `check --wait` (`orca-commands.md` mục 7).
2. Xử lý từng message theo `state.md`, mục "Đọc `worker_done`". Skill này chỉ xử lý chuyển `working` → `pr_open` / `stopped` / `blocked`. Message `question` thì chuyển cho Thái (mục 9 của `orca-commands.md`).
3. `--ack` delivery. Ghi `plan.tsv`.
4. Có slot trống và còn dòng `queued` → quay lại bước 5 cho các dòng đó.
5. Hết giờ chờ mà không có tin → `worker-show` các dispatch đang `working`. Worker đã chết (terminal đóng, process thoát) → `blocked`, note "worker dừng không báo", kèm 20 dòng cuối của `worker-read`.

Thoát vòng lặp khi không còn dòng `queued` hay `working`.

## Bước 7: Báo cáo

In bảng theo mẫu ở `state.md`. Với mỗi ticket `pr_open`, nhắc Thái:
- Kéo ticket sang In Review trên Jira.
- Chạy review: `meloo-review-dispatch` (hoặc tự mở phiên reviewer `/meloo-review <PR>`).

Không đóng terminal của agent làm: vòng sửa theo review sẽ dùng lại.

## Kịch bản kiểm thử

**Bình thường:** Thái nhập `MEL-45 ag cl` và `MEL-50 cl ag`. Cả hai In Progress, không trùng `hot-*`, chưa có worktree → tạo Run, 2 task, 2 `worker-start` (antigravity ở `mel-45`, claude ở `mel-50`) → chờ → `worker_done` "PR: #23" của MEL-45, kiểm `gh pr view 23` khớp → `pr_open` → MEL-50 gửi "STOP: MEL-44 chưa merge" → `stopped` → báo cáo 2 dòng, nhắc Thái review PR #23.

**Lỗi:** Thái nhập `MEL-45 cl cl` → bỏ dòng, báo "agent làm trùng agent review", không tạo worktree nào cho MEL-45.
