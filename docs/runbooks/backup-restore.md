# Backup hằng ngày và diễn tập restore RDS (NFR-15)

**CHƯA KIỂM trên AWS thật.** Thái thực hiện và điền biên bản, không suy ra RTO/RPO từ test local.
Mục tiêu: khôi phục dịch vụ ≤60 phút; dữ liệu mất ≤24 giờ. Thời gian đo gồm restore, đổi kết nối và smoke test.

## Điều kiện và bật backup

- Thái đăng nhập đúng AWS account/region, có quyền RDS/EC2, DB gốc PostgreSQL 17 và cửa sổ bảo trì.
  Chuẩn bị subnet group, SG chỉ cho EC2, parameter group/extensions và version tương thích; không public DB.
- RDS Console → Databases → DB gốc → Modify → Backup retention **7 ngày**,
  chọn backup window hằng ngày **18:00–18:30 UTC** (01:00–01:30 UTC+7, điều chỉnh nếu trùng tải cao).
  Xem pending changes rồi chọn áp dụng; đổi retention từ 0 có thể gián đoạn, thực hiện trong cửa sổ đã thông báo.
- Sau ít nhất một window: kiểm trạng thái available, Automated backups và latest/earliest restorable time;
  timestamp backup mới nhất không quá 24 giờ. RDS storage_full không đảm bảo backup, xem [DB](incident-db-full.md).
  Lưu ảnh retention/window và thời điểm backup, không chụp endpoint/secret. Không tắt backup để giảm phí.

## Diễn tập không ghi đè DB gốc

1. Thống nhất thời gian bảo trì, tên instance riêng `meloo-restore-drill-<date>`, ngân sách và rollback;
   ghi `T0` UTC bắt đầu mô phỏng sự cố, điểm dữ liệu cuối `Tlast` (metadata/timestamp, không lưu nội dung hàng).
   Chọn điểm phục hồi `Trestore` trong retention; `Tlast - Trestore` phải ≤24 giờ.
2. RDS → DB gốc → Actions → Restore to point in time → chọn `Trestore` (hoặc latest restorable time),
   tạo instance riêng. Chọn cùng VPC/subnet, đúng SG/private access, instance class tương đương,
   parameter group, encryption/KMS, engine version và storage. Kiểm từng mục; không dùng mặc định mạng công khai.
3. Chờ instance available, ghi `Tavailable`; kiểm connectivity từ EC2, schema/Flyway version và số lượng/timestamp
   một số bản ghi đã chọn. Dùng client DB trong phiên riêng của Thái; không copy bảng/log/credentials lên PR.
4. Để đo phục hồi **dịch vụ**, giới hạn ingress về IP vận hành trong cửa sổ bảo trì, dừng writer (`cd /opt/meloo && docker compose stop backend`),
   lưu cấu hình kết nối cũ trong nơi riêng của Thái (không chat/repo), đổi `DB_URL` trong `/opt/meloo/.env`
   sang endpoint bản phục hồi, giữ TLS/credentials hợp lệ và quyền file 600.
   `docker compose up -d backend`; kiểm `/actuator/health`, đăng nhập, đọc lịch sử và ghi một vòng thử.
   Ghi `Tservice` khi toàn bộ smoke test thành công; không dừng bấm giờ ở trạng thái RDS available.
5. Tính `RTO = Tservice - T0` và `RPO = Tlast - Trestore`; ghi PASS chỉ khi **RTO ≤60 phút và RPO ≤24 giờ**,
   timestamp/count khớp điểm phục hồi và thao tác đọc/ghi thật thành công. Nếu quá mục tiêu, ghi FAIL và hành động cải thiện.
6. Kết thúc drill: dừng backend, khôi phục DB_URL cũ, khởi động backend và smoke test rồi khôi phục ingress;
   dữ liệu thử trên bản restore không chuyển về DB gốc. Với sự cố thật, giữ endpoint mới và reconciliation do Thái quyết,
   không quay lại DB cũ sau khi có ghi mới nếu chưa có kế hoạch hợp nhất dữ liệu.
7. Sau xác nhận rollback drill thành công, Thái xóa **chỉ instance drill**, kiểm tên hai lần;
   xem có cần giữ snapshot trước khi xóa và kiểm tài nguyên/snapshot còn tính phí. Không xóa DB gốc hay backup production.

## Khi lỗi và chi phí

Restore lỗi: giữ DB gốc, kiểm RDS Events, quota, KMS, subnet/SG và parameter group; không thử xóa backup.
Nếu đổi endpoint thất bại, dừng writer rồi phục hồi cấu hình cũ; kiểm health + ghi thử trước khi mở lại.
Backup storage vượt allowance và instance/storage của bản restore có tính phí; Thái xem ước tính trong AWS,
không coi diễn tập là miễn phí. [AWS backups](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html)
và [PITR](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html), kiểm ngày 20/11/2026.

## Biên bản cần Thái điền

| Mục | Giá trị thật / link bằng chứng đã khử dữ liệu |
|---|---|
| Người thực hiện, ngày UTC, region, DB gốc/drill (bí danh) | CHƯA KIỂM |
| Retention/window và backup/latest restorable time | CHƯA KIỂM |
| T0 / Tlast / Trestore / Tavailable / Tservice | CHƯA KIỂM |
| Schema/count/timestamp, health, đăng nhập, đọc/ghi thử | CHƯA KIỂM |
| RTO phút / RPO giờ và PASS hoặc FAIL | CHƯA KIỂM |
| Rollback drill, smoke test DB gốc, cleanup và chi phí | CHƯA KIỂM |
