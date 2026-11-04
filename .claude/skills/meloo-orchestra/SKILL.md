---
name: meloo-orchestra
description: "Coordinator chạy trọn vòng cho nhiều ticket MEL của dự án Meloo qua Orca Orchestration: mở worktree và giao agent làm (meloo-ticket), khi có PR thì mở tab agent review (meloo-review), PR bị chặn thì giao agent làm sửa theo review, lặp tới khi sạch hoặc hết số vòng, rồi báo Thái. Thái chỉ nhập mỗi ticket một dòng 'MEL-45 <agent làm> <agent review>'. Dùng khi người dùng nói 'chạy orchestra', 'làm và review luôn', 'điều phối các task in progress', '/meloo-orchestra', '$meloo-orchestra'. Chỉ giao việc làm thì dùng meloo-dispatch; chỉ giao review thì dùng meloo-review-dispatch."
---

# meloo-orchestra: làm → review → sửa, tự chuyển tiếp

Bạn là **coordinator**. Skill này ghép `meloo-dispatch` và `meloo-review-dispatch` thành một vòng lặp, thêm bước tự giao vòng sửa. Bạn không code, không review, không merge.

Đọc trước:
- `.claude/skills/meloo-orchestra/references/state.md`: luật chung, cú pháp, `plan.tsv`, bảng chuyển trạng thái.
- `.claude/skills/meloo-orchestra/references/orca-commands.md`
- `.claude/skills/meloo-orchestra/references/agents.md`: mẫu spec WORK, CONTINUE, REVIEW, FIX.
- `.claude/skills/meloo-dispatch/SKILL.md` và `.claude/skills/meloo-review-dispatch/SKILL.md`: skill này dùng lại các bước của chúng.

## Đầu vào

```text
/meloo-orchestra
MEL-45 ag cl
MEL-50 cl ag
song song: 2
vòng sửa: 2
```

Mỗi ticket bắt buộc có cả agent làm và agent review. Thiếu agent review thì bỏ dòng, nhắc Thái (hoặc gợi ý dùng `meloo-dispatch`). Không nhập gì thì làm như `meloo-dispatch` bước 2: liệt kê ticket In Progress và hỏi.

Mặc định: `song song: 2`, `vòng sửa: 2` (giống luật "qua 2 vòng còn Blocker/Major thì Thái quyết" của `meloo-ticket`), `review: tab`.

## Bước 1: Chuẩn bị

1. `meloo-dispatch` bước 1 (môi trường; đọc `plan.tsv` nếu có thì làm tiếp phiên cũ).
2. `meloo-dispatch` bước 2–4 (danh sách, kiểm tra, ghi `plan.tsv`). Ticket đã có PR thì vào thẳng `pr_open`.
3. Tạo Run nếu chưa có.

## Bước 2: Vòng điều phối

Lặp cho tới khi **mọi dòng ở trạng thái cuối** (`clean`, `blocked`, `stopped`):

### 2a. Lấp slot trống

Đếm số dòng `working` + `reviewing` + `fixing`. Còn nhỏ hơn `song song` thì chọn việc kế tiếp theo **thứ tự ưu tiên** dưới đây, mỗi lần một việc, rồi đếm lại:

1. Dòng `need_fix` → **giao FIX** (2c).
2. Dòng `pr_open` → **giao REVIEW** như `meloo-review-dispatch` bước 3–4.
3. Dòng `queued` không vướng khóa `hot-*` → **giao WORK hoặc CONTINUE** như `meloo-dispatch` bước 5.

Vì sao ưu tiên như vậy: ticket gần xong được đẩy tới tay Thái sớm, và không giữ nhiều PR dở dang cùng lúc.

### 2b. Chờ và xử lý tin

1. `check --wait` (`orca-commands.md` mục 7).
2. Mỗi message xử lý theo bảng trong `state.md`:
   - `worker_done` của WORK hoặc CONTINUE: `PR: #n` (đã kiểm `gh pr view`) → `pr_open`. `STOP:` → `stopped`.
   - `worker_done` của REVIEW: release terminal review, `round` + 1. `SẠCH` → `clean`. `CHẶN` mà `round` ≤ `vòng sửa` → `need_fix` (2a sẽ giao FIX khi có slot). `CHẶN` mà `round` > `vòng sửa` → `blocked`, note "hết <vòng sửa> vòng, còn <kết luận>". `KHÔNG REVIEW` → `blocked`.
   - `worker_done` của FIX: `FIXED: #n` → `pr_open` (vòng review tiếp theo). `STOP:` → `blocked`.
   - `question`: chuyển nguyên văn cho Thái, chờ trả lời, gửi lại (`orca-commands.md` mục 9).
   - `escalation`: `blocked`, in cho Thái.
3. `--ack`, ghi `plan.tsv`, cập nhật `worktree set --comment` theo trạng thái mới.
4. Hết giờ chờ mà không có tin: `worker-show` từng dispatch đang chạy. Worker chết → `blocked` kèm 20 dòng cuối của `worker-read`.

### 2c. Giao FIX

1. Điền mẫu FIX (vòng `k` = `round`), ghi `_workspace/orchestra/spec-MEL-<số>-fix<k>.md`. `task-create` với title `MEL-<số> · sửa vòng <k>`.
2. Giao vào terminal của agent làm (`doer_term`): `dispatch --task <id> --to <doer_term> --inject`. Agent vẫn giữ ngữ cảnh lúc làm ticket nên sửa nhanh hơn.
3. Handle lỗi: `terminal list --worktree id:<worktree_id>` để tìm lại. Không còn terminal của agent làm thì mở tab mới bằng lệnh của agent làm (`orca-commands.md` mục 5). Vẫn không được thì `blocked`.
4. Ghi `task`, `dispatch`, status `fixing`.

Không bao giờ giao FIX cho agent review, và không giao REVIEW cho agent làm.

## Bước 3: Báo cáo

Khi mọi dòng đã ở trạng thái cuối, in bảng theo mẫu ở `state.md`, thêm cột "Vòng". Kèm việc Thái cần làm:
- `clean`: kéo ticket sang In Review (nếu chưa), xem bản preview Vercel với ticket frontend, nghiệm thu, merge. Sau khi merge, đóng terminal và xóa worktree trong Orca.
- `blocked`: lý do và câu hỏi cần Thái quyết.
- `stopped`: lý do worker dừng (thường là phụ thuộc chưa merge, hoặc thiếu Definition of Ready).

Thái merge một PR mà còn PR khác đang mở: nhắc Thái dặn agent làm của PR kia chạy `meloo-ticket`, tình huống "Rebase". Coordinator không tự rebase.

## Dừng giữa chừng

- Thái nói "dừng", "tạm dừng": không giao việc mới, chờ các worker đang chạy báo xong rồi báo cáo. Không `worker-stop` nếu Thái không nói rõ "dừng ngay".
- Coordinator bị tắt: worker vẫn chạy trong Orca. Phiên sau chạy lại `/meloo-orchestra` không kèm dòng nào; skill đọc `plan.tsv` và làm tiếp.

## Kịch bản kiểm thử

**Bình thường:** `MEL-45 ag cl`, `MEL-50 cl ag`, `song song: 2`.
Giao WORK cho cả hai → MEL-45 báo `PR: #23` → giao REVIEW cho Claude ở tab trong `mel-45` → `CHẶN 1B 0M`, round 1 ≤ 2 → giao FIX vào terminal antigravity → `FIXED: #23` → REVIEW vòng 2 → `SẠCH` → `clean`.
Song song: MEL-50 báo `STOP: MEL-44 chưa merge` → `stopped`.
Báo cáo: MEL-45 clean (vòng 2), MEL-50 stopped.

**Lỗi:** PR #23 vẫn `CHẶN` sau vòng 2 → `blocked`, note "hết 2 vòng, còn CHẶN 1B 0M". Không giao FIX lần 3; báo Thái quyết.
