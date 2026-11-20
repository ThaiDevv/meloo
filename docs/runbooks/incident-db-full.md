# RDS đầy hoặc kết nối DB lỗi

Mục đích: phục hồi ghi dữ liệu mà không xóa dữ liệu người chơi.
Điều kiện: Thái có AWS Console đúng account/region, RDS instance ID và quyền xem CloudWatch/modify storage.

1. Ghi UTC, kiểm RDS Events/status, CloudWatch `FreeStorageSpace`, `DatabaseConnections`, `FreeableMemory`;
   phân biệt hết dung lượng với SG/network/password lỗi. Không gửi connection string hay credentials vào chat.
2. Nếu `storage_full`/dung lượng cạn: tăng allocated storage hoặc bật storage autoscaling với trần ngân sách
   trong RDS Modify; xem thay đổi pending và chọn thời điểm áp dụng phù hợp. Không DROP/TRUNCATE bảng,
   không chạy VACUUM FULL khi đĩa đã đầy. Thay đổi storage có thể mất thời gian và không giảm lại trực tiếp được.
3. Kiểm automated backup/latest restorable time: RDS có thể không backup khi storage_full;
   sau phục hồi dung lượng, xác nhận backup tiếp tục. Xem [backup/restore](backup-restore.md).
4. Kiểm `/actuator/health` trả UP, thử đăng nhập và lưu kết quả vòng thử; theo dõi FreeStorageSpace
   và Sentry thêm 10 phút. Nếu DB hỏng, dùng restore sang instance mới, không ghi đè DB gốc.

Quay lại: lỗi kết nối do thay cấu hình thì khôi phục cấu hình trước đó trong phiên riêng của Thái;
không giảm storage đã tăng và không đổi endpoint tùy tiện khi còn writer.
Chi phí: storage/autoscaling tăng hóa đơn; Thái xem ước tính trước khi áp dụng.
Biên bản: UTC, đồ thị dung lượng trước/sau, allocated/max storage, backup time, health; không đính dữ liệu hàng.
