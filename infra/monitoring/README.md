# Giám sát beta — MEL-91

Trạng thái 20/11/2026: **CHƯA KIỂM production**; Thái thực hiện Sentry, UptimeRobot, RDS theo hướng dẫn.
Không ghi DSN thật, token, cookie, đáp án hay URL audio vào bằng chứng/chụp màn hình.

## Sentry và release (AC1)

1. Trong Sentry chọn gói Developer miễn phí, tạo project React và Spring Boot.
   Đặt cảnh báo email cho lỗi mới ở production.
2. Trên EC2 đặt `SENTRY_DSN` của backend trực tiếp trong `/opt/meloo/.env` (quyền 600).
   Compose lấy `SENTRY_RELEASE` từ `BACKEND_IMAGE` (SDK thay `/` bằng `_`): deploy bằng tag bất biến `sha-<commit>`;
   không dùng `latest` để nghiệm thu release. Recreate backend bằng `docker compose up -d backend`.
3. Vercel Production: đặt `VITE_SENTRY_DSN` của React và `VITE_SENTRY_RELEASE=meloo@<full-git-sha>`
   ứng với commit deploy, rồi rebuild. Không đặt auth token Sentry trong biến `VITE_*`.
   Preview mock/local không khởi tạo SDK; thiếu DSN/release frontend cũng không khởi tạo.
4. Trên staging, ném lỗi tổng hợp từ DevTools: `setTimeout(() => { throw new Error('monitoring-smoke') })`;
   Trên production, kiểm issue thực tế đầu tiên ở mỗi phía, release/email và event JSON đã khử dữ liệu;
   chưa có issue thì giữ CHƯA KIỂM, không gây lỗi cho request người chơi hay mở endpoint debug.
5. Lưu link issue FE/BE, release/commit, UTC, email và event JSON đã khử dữ liệu; hoàn tác bằng cách bỏ DSN và recreate/rebuild.

SDK chỉ gửi lỗi: tắt tracing, replay, session và breadcrumbs; event được dựng lại theo allowlist.
Backend giữ class/method/line; frontend giữ vị trí trong `/assets/*.js` (không upload sourcemap trong ticket này).
Log JSON giữ timestamp, level, logger, exception type, correlation id và một số mẫu sự kiện tĩnh đã allowlist;
không nội suy arguments, không giữ message tùy ý/stack để tránh lộ đáp án từ thư viện hoặc SQL. Khi điều tra, dùng logger + thời điểm + Sentry code location;
không bật debug/request body/SQL parameter logging trên production.
Tomcat access log JSON chỉ giữ method/status/id; Docker xoay vòng **10 MB × 3/service** (backend + Caddy khoảng 60 MB).
Không bật thêm file log ứng dụng. Kiểm tra `docker inspect --format '{{json .HostConfig.LogConfig}}' meloo-backend`
và `docker logs --since 5m meloo-backend` trên máy chủ; không gửi log thô ra chat.

## UptimeRobot (AC2, NFR-14)

1. Tạo HTTP(S) monitor `https://api.meloo.id.vn/actuator/health`, interval **5 phút**, HTTPS verification bật;
   không dùng readiness vì probe đó không thay cho kiểm tra DB. Endpoint công khai chỉ trả status, không details.
2. Chọn email contact đã xác minh, bật thông báo down và recovery; dùng chức năng gửi test notification.
   Trong cửa sổ bảo trì đã báo trước, dừng backend ngắn có kiểm soát, chờ email down, khởi động lại và nhận recovery.
   Ghi giờ bắt đầu/kết thúc, email và incident; thời gian phát hiện có thể hơn 5 phút do xác nhận lỗi.
3. Sau 29/11 xuất lịch sử/ảnh báo cáo UptimeRobot cho **16/11/2026 00:00 đến 30/11/2026 00:00 Asia/Ho_Chi_Minh**
   (tương đương **15/11 17:00 đến 29/11 17:00 UTC**), lưu monitor ID, timezone, thời gian quan sát và downtime.
   `availability = 100 × (observed_seconds - downtime_seconds) / observed_seconds`; mục tiêu **≥99%**.
   Monitor lập sau 16/11 thiếu dữ liệu đầu kỳ: ghi đúng khoảng thiếu, không suy ra 99% cho cả đợt beta.
4. Hoàn tác monitor sai: sửa URL/contact, không tắt xác minh TLS; ghi lại khoảng mất quan sát.

## Bằng chứng còn thiếu

**CHƯA KIỂM:** AC1 issue/email/release FE+BE; AC2 monitor/contact, test down/up và report beta;
AC3 JSON/LogConfig EC2; AC4 retention/window/latest restore time và biên bản RTO/RPO theo [backup](../../docs/runbooks/backup-restore.md).

Chi phí: dùng Sentry Developer/UptimeRobot Free trong quota; không tự nâng gói.
Nguồn kiểm ngày 20/11/2026: [Sentry Spring](https://docs.sentry.io/platforms/java/guides/spring-boot/),
[Sentry React](https://docs.sentry.io/platforms/javascript/guides/react/configuration/options/),
[UptimeRobot plans](https://uptimerobot.com/pricing/).
