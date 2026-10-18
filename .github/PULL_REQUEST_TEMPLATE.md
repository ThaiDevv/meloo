## Ticket
MEL-___ · <mã nội bộ> · <tên ticket>
Tác giả: <Codex | Claude> · Reviewer: <agent còn lại> · Mức review: <chuẩn | sâu>

## Phạm vi file
<!-- Mỗi dòng một glob, chép từ ticket (đã thêm test tương ứng). Dòng bắt đầu bằng ! là loại trừ. Reviewer và job scope-guard đọc khối này. -->
<!-- meloo:scope -->
```text
```
<!-- /meloo:scope -->
File nóng đã giữ: <không | hot-...>

## Thay đổi
- 

## Bằng chứng Acceptance Criteria
| # | AC | Bằng chứng (test, lệnh, ảnh) | Kết quả |
|---|---|---|---|
| 1 | | | |

## Cách kiểm tra
1. 

## QA và tự review
- Kiểm tra phạm vi: 
- QA (`meloo-qa`): 
- Tự review: 

## Ảnh hoặc video (bắt buộc với frontend)

## Tài liệu cần cập nhật
<!-- Chỉ ghi lại. Không tự sửa docs ngoài phạm vi. -->

## Ticket bổ sung
<!-- API hoặc file cần sửa nằm ngoài phạm vi: mô tả để Thái tạo ticket. -->

## Góp ý harness
<!-- Chỗ skill hoặc agent làm sai, làm thiếu, để đưa vào retro. Để trống nếu không có. -->

## Checklist (Definition of Done)
- [ ] Đạt toàn bộ Acceptance Criteria, có bằng chứng ở bảng trên
- [ ] Chỉ sửa file trong phạm vi (`check-scope.sh` xanh)
- [ ] Đã thêm hoặc cập nhật test, CI xanh
- [ ] Đủ i18n vi/en (frontend)
- [ ] Không còn secret, TODO, log debug
