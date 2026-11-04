# Lệnh Orca dùng trong bộ meloo-orchestra

Nguồn: tài liệu Orca (onorca.dev, trang "Orca CLI reference" và "Orchestration"), đọc ngày 04/11/2026.
Cờ lệnh đổi theo phiên bản Orca. **Lần đầu dùng trong mỗi phiên** chạy:

```bash
<ORCA> skills get orchestration --full
<ORCA> orchestration worker-start --help
<ORCA> orchestration dispatch --help
```

Output của CLI khác file này thì làm theo CLI. **Không tự bịa cờ.** Cờ bị từ chối thì đọc `--help` của đúng lệnh đó, sửa một lần; vẫn lỗi thì dừng và báo Thái nguyên văn lỗi.

## 0. Tên lệnh `<ORCA>`

Chạy `command -v orca-ide || command -v orca`. Linux dùng `orca-ide` (tên `orca` trùng trình đọc màn hình GNOME). Nếu biến `ORCA_CLI_COMMAND` có giá trị thì dùng giá trị đó.
Shell không giữ biến giữa các lần gọi, nên **viết thẳng tên lệnh** (VD `orca-ide`) vào mọi lệnh, đừng dùng `$ORCA`.
Mọi lệnh Orca đều thêm `--json`.

## 1. Kiểm tra môi trường

```bash
<ORCA> status --json                         # phải thành công; lỗi → nhờ Thái mở Orca
<ORCA> repo list --json                      # lấy repoId của repo Meloo
<ORCA> repo show --repo id:<repoId> --json   # base ref phải là origin/main
git fetch origin
```

- Orchestration là tính năng Experimental: Thái phải bật ở **Settings → Experimental**. Lệnh `orchestration` báo chưa bật thì dừng, nhờ Thái bật.
- Base ref không phải `origin/main` thì báo Thái (lệnh sửa: `repo set-base-ref --repo id:<repoId> --ref origin/main`). Không tự chạy, vì đây là đổi cấu hình của Thái.
- Coordinator nên chạy ở bản checkout chính đang ở `main`, đã `git pull --ff-only`. Worktree con có thể lấy gốc từ đây.

## 2. Run (mỗi phiên coordinator một Run)

```bash
<ORCA> orchestration run-create --objective "Meloo orchestra 04/11: MEL-45, MEL-50" --json
```

Ghi `runId` vào `plan.tsv` (dòng `#run`). `plan.tsv` đã có `runId` thì dùng lại, không tạo Run mới.

## 3. Task

```bash
<ORCA> orchestration task-create --task-title "MEL-45 · làm" --spec "<nội dung từ agents.md>" --json
```

Spec dài thì ghi ra `_workspace/orchestra/spec-<tên>.md` rồi truyền `--spec "$(cat _workspace/orchestra/spec-<tên>.md)"`.

## 4. Worker làm ticket, mở worktree mới

```bash
<ORCA> orchestration worker-start --task <taskId> --worktree new-child --name mel-45 --agent <agent> --setup inherit --json
```

- Receipt trả về `dispatchId`, worktree (id, path) và terminal handle. Ghi hết vào `plan.tsv`.
- Tên worktree: `mel-<số>`. Branch do `meloo-ticket` bước 3 tự đổi về `<type>/MEL-<số>-<mô-tả>`.
- `--model <id>` và `--effort <mức>` là tùy chọn, chỉ thêm khi Thái ghi rõ (xem `agents.md`).
- Kiểm tra gốc worktree: `git -C <path> merge-base --is-ancestor origin/main HEAD`. Sai thì ghi chú "gốc cũ" vào cột `note`. Không phải lỗi chặn: `meloo-ticket` bước 9 rebase trước khi mở PR.

## 5. Mở tab agent khác trong worktree có sẵn (dùng cho review, tiếp tục, sửa)

```bash
<ORCA> worktree show --worktree branch:<headRefName> --json        # tìm worktree theo branch
<ORCA> terminal create --worktree id:<worktreeId> --title "review-claude" --command "<lệnh mở agent>" --json
<ORCA> terminal wait --terminal <handle> --for tui-idle --timeout-ms 120000 --json
<ORCA> orchestration dispatch --task <taskId> --to <handle> --inject --json
```

`<lệnh mở agent>` lấy ở cột "Lệnh mở tab" trong `agents.md`.

**Dự phòng** khi worktree không còn, `terminal create` lỗi, hoặc agent chưa có lệnh mở tab đã kiểm chứng: mở worktree riêng.

```bash
<ORCA> orchestration worker-start --task <taskId> --worktree new-child --name review-pr-<số> --agent <agent> --setup inherit --json
```

## 6. Giao việc tiếp cho terminal đang mở (vòng sửa)

```bash
<ORCA> orchestration task-create --task-title "MEL-45 · sửa vòng 1" --spec "..." --json
<ORCA> orchestration dispatch --task <taskIdMới> --to <handle> --inject --json
```

Terminal đã đóng hoặc handle lỗi: chạy `terminal list --worktree id:<worktreeId> --json` để lấy lại handle. Không còn terminal của agent đó thì làm như mục 5 (mở tab mới bằng lệnh của agent làm ticket).

## 7. Chờ tin

```bash
<ORCA> orchestration check --wait --types worker_done,escalation,question --timeout-ms 540000 --json
<ORCA> orchestration check --ack <deliveryId> --wait --types worker_done,escalation,question --timeout-ms 540000 --json
```

- Đặt timeout của công cụ shell ≥ 600000 ms. Claude Code giới hạn một lệnh tối đa 10 phút, nên dùng `--timeout-ms 540000` (9 phút).
- Một delivery có thể chứa nhiều message: **xử lý hết rồi mới `--ack`**. Chưa ack thì lần `check` sau trả lại đúng delivery đó.
- Hết giờ mà không có tin: không phải lỗi. Chạy `worker-show` cho từng dispatch đang chạy (mục 8).

## 8. Xem và thu dọn

```bash
<ORCA> orchestration worker-show --dispatch <dispatchId> --json         # còn sống không
<ORCA> orchestration worker-read --dispatch <dispatchId> --limit 50 --json
<ORCA> orchestration worker-release --dispatch <dispatchId> --json      # sau khi review xong
<ORCA> orchestration task-list --json
<ORCA> orchestration task-update --id <taskId> --status blocked --result '{"reason":"..."}' --json
<ORCA> worktree set --worktree id:<worktreeId> --comment "MEL-45 · đang review (claude)" --json
```

- `worker-release` chỉ dùng cho terminal **review** khi review xong. Terminal của agent làm ticket thì giữ lại để giao vòng sửa; Thái tự đóng sau khi merge.
- `worker-release` trả về `release_pending` hoặc `release_unknown` thì làm theo hướng dẫn trong receipt, không thay bằng `terminal close`.
- `worker-stop` chỉ khi Thái bảo, hoặc worker treo quá 2 lần `check` liên tiếp và Thái đồng ý.

## 9. Câu hỏi từ worker

Worker hỏi qua `orchestration ask`. Coordinator nhận message loại `question`.

- **Không tự trả lời** câu hỏi về nghiệp vụ, phạm vi file, thêm thư viện, phụ thuộc. In nguyên văn câu hỏi và các lựa chọn cho Thái, chờ Thái trả lời rồi chuyển lại.
- Lệnh trả lời: xem trong `skills get orchestration --full` (mục reply/answer). Không có trong guide thì dùng `orchestration send --to dispatch:<dispatchId> --subject "Trả lời" --body "<câu trả lời của Thái>" --json`.

## 10. Lệnh cấm

| Lệnh | Vì sao |
|---|---|
| `orchestration reset ...` | Xóa trạng thái orchestration của cả runtime, kể cả của phiên khác |
| `worktree rm`, `terminal close` hàng loạt | Mất `_workspace/` và phiên của agent; Thái tự dọn sau khi merge |
| `orchestration run`, `coordinator-start` | Lệnh cũ, đã ngừng, không có tác dụng |
| `git push`, `gh pr merge`, `gh pr review --approve`, công cụ ghi Jira | Coordinator chỉ điều phối (luật 10) |
