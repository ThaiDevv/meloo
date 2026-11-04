# Agent và mẫu giao việc

## Bảng agent

Thái sửa bảng này khi thêm agent hoặc khi kiểm chứng xong một ô "chưa kiểm".

| Tên | Viết tắt | `--agent` (worker-start) | Lệnh mở tab (`terminal create --command`) | Ghi chú |
|---|---|---|---|---|
| claude | `cl` | `claude` | `claude` | Claude Code |
| codex | `cx` | `codex` | `codex` | Gói Codex có thể đã hết hạn: worker-start lỗi xác thực thì dừng ticket đó, báo Thái |
| antigravity | `ag` | `antigravity` | `agy` (**chưa kiểm**) | Có trong tài liệu Orchestration. Lệnh mở tab chưa kiểm thì dùng đường dự phòng (worktree riêng) |
| gemini | `gm` | `gemini` (**chưa kiểm**) | `gemini` (**chưa kiểm**) | Orca có nhóm địa chỉ `@gemini`, nhưng tài liệu worker-start không liệt kê |

Ô **chưa kiểm**: lần đầu dùng thì đọc `<ORCA> orchestration worker-start --help`. Không có agent đó trong danh sách thì báo Thái, không đoán.

Tùy chọn model: Thái ghi `ag:<model-id>` hoặc `cl:<model-id>` thì truyền `--model <model-id>`. Có thêm `@high` (VD `cl:<model-id>@high`) thì thêm `--effort high`. Không ghi thì không truyền, agent dùng mặc định.

## Vì sao spec viết bằng lời, không dùng `/meloo-ticket`

`dispatch --inject` và `worker-start` chèn một đoạn hướng dẫn của Orca lên trước spec. Lệnh slash như `/meloo-ticket` chỉ chạy khi nằm ở đầu tin nhắn, nên sẽ không chạy. Vì vậy spec gọi skill bằng tên, kèm đường dẫn file để agent nào cũng làm theo được, kể cả agent không nạp skill tự động.

Ba dòng mở đầu cố định (`PR:`, `KẾT LUẬN:`, `FIXED:`) giúp coordinator đọc kết quả mà không phải phán đoán.

## Mẫu spec

Thay `<...>`. Tên agent ghi hoa chữ đầu trong PR: Claude, Codex, Antigravity, Gemini.

### WORK: làm ticket

```text
Bạn là agent làm ticket <MEL-số> của dự án Meloo, tên trong PR: <Doer>. Reviewer sẽ là <Reviewer>.

Dùng skill meloo-ticket để làm ticket <MEL-số> từ đầu tới khi mở PR. Không gọi được skill theo tên thì đọc file .claude/skills/meloo-ticket/SKILL.md và làm đúng theo đó. Trong PR ghi "Tác giả: <Doer>".

Cần Thái quyết (nghiệp vụ, phạm vi file, phụ thuộc, thêm thư viện) thì hỏi coordinator bằng lệnh ask của Orca theo hướng dẫn liên lạc ở trên, không hỏi trong khung chat rồi chờ.

Kết thúc (mở được PR, hoặc phải dừng ở bất kỳ bước nào) thì gửi worker_done đúng một lần. Dòng ĐẦU TIÊN của body là đúng một trong hai dạng:
PR: #<số PR>
STOP: <lý do một câu>
Các dòng sau: tóm tắt ngắn, AC nào chưa có bằng chứng.
```

### CONTINUE: tiếp tục ticket ở worktree có sẵn

Như WORK, thay câu thứ hai bằng: `Dùng skill meloo-ticket, tình huống "Tiếp tục", cho ticket <MEL-số>: đọc _workspace/<MEL-số>/ rồi làm tiếp bước còn dở.`

### REVIEW: review PR

```text
Bạn là reviewer của dự án Meloo, tên: <Reviewer>. Tác giả PR là <Doer>.

Dùng skill meloo-review để review PR #<số PR> (ticket <MEL-số>), vòng <k>. Không gọi được skill theo tên thì đọc file .claude/skills/meloo-review/SKILL.md và làm đúng theo đó. Bạn đang ở worktree có sẵn branch của PR; vẫn chạy gh pr checkout <số PR> như skill hướng dẫn.

Chỉ comment lên PR. Không approve, không sửa code, không push.

Xong thì gửi worker_done đúng một lần. Dòng ĐẦU TIÊN của body là đúng một trong ba dạng:
KẾT LUẬN: CHẶN <n>B <m>M
KẾT LUẬN: SẠCH
KẾT LUẬN: KHÔNG REVIEW: <lý do một câu>
```

### FIX: sửa theo review

```text
Dùng skill meloo-ticket, tình huống "Sửa theo review", cho PR #<số PR> (ticket <MEL-số>), vòng sửa <k>. Không gọi được skill theo tên thì đọc .claude/skills/meloo-ticket/SKILL.md, mục "Sửa theo review".

Xong thì gửi worker_done đúng một lần. Dòng ĐẦU TIÊN của body là:
FIXED: #<số PR>
hoặc
STOP: <lý do một câu>
```
