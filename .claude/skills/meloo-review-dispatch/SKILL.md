---
name: meloo-review-dispatch
description: "Coordinator giao review cho dự án Meloo qua Orca Orchestration: với mỗi PR của ticket MEL, mở tab agent reviewer (khác agent tác giả) trong worktree của PR, cho nó chạy meloo-review, chờ kết luận rồi báo Thái. Dùng khi người dùng nói 'giao review', 'cho Claude review PR 23', 'review các PR đang mở', 'MEL-45 review cl', '/meloo-review-dispatch', '$meloo-review-dispatch'. Không tự review (dùng meloo-review), không tự sửa theo review (meloo-ticket), không chạy trọn vòng làm + review (meloo-orchestra)."
---

# meloo-review-dispatch: mở tab reviewer và giao review

Bạn là **coordinator**. Bạn không review, không đọc diff để tự đánh giá. Bạn mở tab cho agent reviewer, giao việc, chờ kết luận, rồi báo Thái.

Trước khi làm, đọc 3 file:
- `.claude/skills/meloo-orchestra/references/state.md`
- `.claude/skills/meloo-orchestra/references/orca-commands.md`
- `.claude/skills/meloo-orchestra/references/agents.md` (mẫu spec REVIEW)

## Bước 1: Môi trường

Như `meloo-dispatch` bước 1: tên lệnh Orca, kiểm tra môi trường, đọc `skills get orchestration --full` và `orchestration dispatch --help`, đọc `plan.tsv` nếu có.

## Bước 2: Danh sách PR cần review

Lấy theo thứ tự ưu tiên:
1. Thái nhập dòng `MEL-<số> <agent review>` hoặc `PR <số> <agent review>`.
2. Thái không nhập gì → các dòng `pr_open` trong `plan.tsv` đã có cột `reviewer`.
3. Vẫn trống → `gh pr list --state open --search "MEL- in:title" --json number,title,headRefName,author`. In bảng, hỏi Thái agent review cho từng PR, dừng chờ.

## Bước 3: Kiểm tra từng PR

1. `gh pr view <số> --json number,title,state,isDraft,headRefName,body`.
   - State không phải OPEN → bỏ, note "PR đã đóng hoặc đã merge".
   - Draft → bỏ, note "PR còn draft".
   - Title không có `MEL-<số>` → hỏi Thái PR này thuộc ticket nào.
2. Tác giả: tìm dòng có chữ `Tác giả:` trong body PR. Không có thì lấy cột `doer` trong `plan.tsv`. Vẫn không có thì hỏi Thái.
3. Reviewer phải khác tác giả. Trùng thì bỏ PR đó: "Agent review trùng tác giả, chọn agent khác."
4. Đang có reviewer chạy cho PR này (dòng `reviewing` trong `plan.tsv`) → không giao thêm.
5. Vòng review `k` = cột `round` + 1 (PR mới: `k` = 1).

## Bước 4: Giao review

Với từng PR hợp lệ, chừng nào số worker đang chạy còn nhỏ hơn `song song`:

1. Điền mẫu REVIEW trong `agents.md`, ghi ra `_workspace/orchestra/spec-MEL-<số>-review<k>.md`. `task-create` với title `MEL-<số> · review vòng <k>`.
2. Chọn nơi chạy:
   - **Mặc định (`review: tab`)**: `worktree show --worktree branch:<headRefName>`. Tìm thấy worktree và agent review có "Lệnh mở tab" đã kiểm chứng trong `agents.md` → `terminal create` trong worktree đó bằng lệnh của reviewer, `terminal wait --for tui-idle`, rồi `dispatch --inject` (`orca-commands.md` mục 5).
   - **Dự phòng**, hoặc khi Thái ghi `review: worktree`: `worker-start ... --worktree new-child --name review-pr-<số> --agent <reviewer>`.
   - Vì sao mặc định là tab: worktree của tác giả đã có sẵn branch, thư viện đã cài và bản build, nên reviewer chạy test nhanh hơn. Agent tác giả lúc này đang rảnh vì đã mở PR xong.
3. Ghi `task`, `dispatch`, `review_term`, status `reviewing` vào `plan.tsv`. Chưa có dòng của ticket thì tạo dòng mới.
4. `worktree set --comment "MEL-<số> · <reviewer> đang review vòng <k>"`.

## Bước 5: Chờ kết luận

Có `không chờ` thì sang bước 6.

Lặp `check --wait` như `meloo-dispatch` bước 6. Với `worker_done` của dispatch review, đọc dòng đầu theo `state.md`:
- `KẾT LUẬN: SẠCH` → `clean`.
- `KẾT LUẬN: CHẶN ...` → `blocked`, note "chờ sửa theo review". Skill này **không** tự giao vòng sửa; `meloo-orchestra` mới làm việc đó.
- `KẾT LUẬN: KHÔNG REVIEW: ...` → `blocked`, chép lý do.

Mỗi review xong: `round` + 1, rồi `worker-release --dispatch <dispatchId>` để đóng terminal review và giải phóng slot. Đường dự phòng (worktree riêng) cũng release, nhưng không xóa worktree.

## Bước 6: Báo cáo

In bảng theo mẫu ở `state.md`, thêm cột kết luận (VD "CHẶN 1B 2M"). Kèm việc Thái cần làm:
- PR sạch: nghiệm thu, merge.
- PR bị chặn: chạy `meloo-orchestra` để tự giao vòng sửa, hoặc mở phiên agent tác giả với yêu cầu "sửa theo review PR <số>".

## Kịch bản kiểm thử

**Bình thường:** Thái nhập `MEL-45 cl`. PR #23 OPEN, body ghi "Tác giả: Antigravity". Worktree `mel-45` còn → mở tab `claude` trong worktree đó → `dispatch --inject` spec REVIEW vòng 1 → `worker_done` "KẾT LUẬN: CHẶN 1B 0M" → `blocked` (chờ sửa), `round` = 1, release tab review → báo Thái kèm gợi ý chạy `meloo-orchestra`.

**Lỗi:** Thái nhập `PR 23 ag` nhưng PR ghi "Tác giả: Antigravity" → bỏ, báo "agent review trùng tác giả", không mở tab nào.
