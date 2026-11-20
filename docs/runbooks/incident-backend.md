# Backend chết hoặc HTTP 5xx

Mục đích: khôi phục backend khi UptimeRobot báo down hoặc Sentry tăng lỗi.
Điều kiện: Thái có SSH EC2; biết tag image tốt gần nhất và thời điểm deploy; không đọc/chia sẻ `.env`.

1. Ghi giờ UTC, HTTP status, release và correlation id (UUID); không ghi request body/audio URL.
2. Trên EC2: `cd /opt/meloo`; chạy `docker compose ps`, `df -h`, `free -m`;
   kiểm tra `docker inspect --format '{{.State.OOMKilled}} {{.State.ExitCode}}' meloo-backend`.
   Đọc log tại máy chủ bằng `docker logs --since 10m meloo-backend`; đối chiếu Sentry theo release/thời gian/id.
3. Nếu container dừng: `docker compose up -d backend`; nếu vừa deploy lỗi:
   `bash deploy-ec2.sh ghcr.io/thaidevv/meloo-backend:sha-<known-good>` (thay tag thật).
   Không rollback schema tự động; nếu migration không tương thích, giữ bản hiện tại và Thái quyết khôi phục DB.
4. Nếu DB lỗi/đầy: chuyển [DB](incident-db-full.md); HTTPS lỗi: [TLS](incident-https.md).
   Thiếu RAM/disk: không restart liên tục hoặc xóa Docker volume; giữ dữ liệu và điều tra dung lượng.
5. Kiểm thành công: `curl --fail --max-time 10 https://api.meloo.id.vn/actuator/health` trả UP,
   đăng nhập và chơi vòng thử bằng tài khoản vận hành, nhận email recovery; theo dõi ít nhất 2 chu kỳ 5 phút.

Restart mất phòng/trận đang giữ trong memory; không tự cộng điểm hay phát lại command.
Chi phí: restart/rollback không thêm tài nguyên; tăng EC2 cần Thái quyết.
Biên bản: UTC phát hiện/khôi phục, release, nguyên nhân, thao tác, correlation id, link issue đã khử dữ liệu.
