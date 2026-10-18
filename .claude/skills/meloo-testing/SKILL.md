---
name: meloo-testing
description: "Chiến lược và cách viết test cấp hệ thống của Meloo: test plan, E2E Playwright (chơi đơn; nhiều người với nhiều browser context; chạy với docker compose và dữ liệu seed cố định), kiểm thử tải k6 theo mục tiêu phi chức năng, UAT và báo cáo kiểm thử. Dùng khi làm hoặc review ticket QA-*, DOC-06, BUG-00 (viết test tái hiện lỗi), hoặc khi cần viết E2E hay kịch bản k6."
---

# meloo-testing

## Mức test và ai viết
| Mức | Ai viết | Đặt ở đâu |
|---|---|---|
| Unit, integration | Người làm ticket BE/FE | Cạnh code (xem meloo-backend, meloo-frontend) |
| E2E luồng chính | Ticket QA (Codex) | `frontend/e2e/**` |
| Tải | QA-03 | `perf/**` |
| UAT | Thái + người dùng beta (QA-04) | `docs/test/04-uat.md` |

Agent không viết frontend sẽ viết E2E cho frontend (Codex viết E2E cho giao diện của Claude) để kiểm chứng độc lập.

## E2E Playwright
- Chạy với `docker compose` (backend + PostgreSQL) và bản build frontend, dùng bộ dữ liệu seed nhỏ cố định.
- Không gọi API nhạc thật trong CI: backend chạy profile `e2e` với `FakeMusicProvider` (BE-CAT-01) và vài file mp3 mẫu ngắn.
- Chọn phần tử theo vai trò và nhãn (`getByRole`, `getByLabel`), không theo class CSS. Chỉ thêm `data-testid` khi không còn cách khác.
- Nhiều người chơi: mỗi người một `browser.newContext()`. Đồng bộ bằng cách chờ thứ hiển thị trên UI (`expect(...).toBeVisible()`), không dùng `waitForTimeout`.
- Hết giờ: profile e2e cho phép đặt thời lượng vòng ngắn, không phải chờ 45 giây thật.
- Khi lỗi thì giữ trace và video (`trace: 'retain-on-failure'`, `video: 'retain-on-failure'`).
- Test hỏng thất thường (flaky): sửa nguyên nhân, hoặc đánh dấu rồi tạo ticket. Không thêm retry để che lỗi.

## k6 (QA-03)
- Kịch bản: 20 phòng × 5 người × 10 vòng, cả REST lẫn WebSocket.
- Ngưỡng theo NFR: `http_req_duration` p(95) < 300 ms, độ trễ sự kiện trận < 200 ms, tỉ lệ lỗi < 1%.
- Chạy vào giờ thấp điểm, báo Thái trước; ghi lại RAM/CPU của EC2.
- Báo cáo ở `docs/test/03-load-test.md`, so với mục tiêu; chỗ nào vượt ngưỡng thì đề xuất thành ticket.

## Tài liệu test
- Test plan (`docs/test/01-test-plan.md`): phạm vi, các mức test, môi trường, dữ liệu, tiêu chí đạt và tiêu chí dừng.
- Báo cáo (`docs/test/02-test-report.md`): số test theo từng mức, tỉ lệ đạt, coverage (JaCoCo, Vitest), kết quả tải, UAT, bảo mật, các lỗi còn tồn và lý do chấp nhận. Số liệu lấy từ lần chạy thật, ghi rõ ngày và commit.

## Sửa lỗi (BUG-00)
Viết test tái hiện lỗi trước (đỏ), rồi mới sửa (xanh). PR ghi link tới test đó.
