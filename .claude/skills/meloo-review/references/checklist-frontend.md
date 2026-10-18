# Checklist frontend (React + TypeScript strict)

## Hợp đồng và dữ liệu
- [ ] Gọi API qua client chung `src/shared/api` (type sinh từ OpenAPI). Không `fetch` tay, không `any`, không `as` ép kiểu dữ liệu từ server.
- [ ] Hook bóc đúng lớp bọc (VD `{ items, page }` của phân trang); xử lý field optional/null đúng như hợp đồng.
- [ ] Query key nhất quán; mutation invalidate đúng key; không fetch trùng.
- [ ] Sự kiện STOMP dùng type khớp AsyncAPI; store Zustand cập nhật bất biến; đồng hồ tính theo `serverTime` (bù lệch giờ client).
- [ ] Mock MSW lấy ví dụ từ `contracts/examples/**`, không tự chế dữ liệu khác hợp đồng.

## Bảo mật phía client
- [ ] Access token chỉ ở bộ nhớ, không localStorage/sessionStorage; refresh qua cookie HttpOnly.
- [ ] Nhiều request 401 cùng lúc chỉ gọi refresh một lần.
- [ ] Không `dangerouslySetInnerHTML`; chat hiển thị văn bản thuần.
- [ ] Không đưa đáp án vào DOM hay state trước khi server công bố.
- [ ] Không log token, không gửi dữ liệu cá nhân lên Sentry.

## UI, a11y, i18n
- [ ] Màn nào cũng có trạng thái loading, lỗi, rỗng.
- [ ] Dùng component trong `src/shared/ui` (FE-01); không tự chế nút hay input mới.
- [ ] Dùng được bằng bàn phím: focus thấy rõ, Enter/Escape hoạt động, thứ tự tab hợp lý; nút chỉ có icon thì có `aria-label`.
- [ ] Không có chuỗi cứng: mọi chữ đi qua `t()`, đủ key ở `vi` và `en` (`check-i18n-parity.py`).
- [ ] Responsive từ 360px.

## Âm thanh và realtime
- [ ] Web Audio phát đúng độ dài mức (sai lệch < 50 ms), dừng nguồn cũ trước khi phát lại, mở khóa AudioContext bằng thao tác người dùng (Safari/iOS).
- [ ] Hủy subscribe và timer khi unmount; không rò listener khi kết nối lại.

## Test
- [ ] Test component cho hành vi trong AC (Testing Library, user-event); không dùng snapshot vô nghĩa.
- [ ] Luồng chính có Playwright (ticket QA), hoặc ít nhất có test tích hợp với MSW.
