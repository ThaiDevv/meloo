# Checklist hợp đồng, migration, CI/hạ tầng, tài liệu

## Hợp đồng (CT-xx)
- [ ] Theo quy ước của skill `meloo-contract`: `/api/v1`, camelCase, ISO-8601 UTC, ProblemDetail, phân trang thống nhất.
- [ ] Không schema nào trả đáp án trước ROUND_END; message realtime nào cũng có `serverTime`.
- [ ] Operation nào cũng có `operationId`, ví dụ request/response và mã lỗi; lint xanh.
- [ ] Thay đổi sau khi đã merge chỉ được thêm (additive). Đổi hay xóa field là breaking → PR phải nêu rõ và Thái duyệt.
- [ ] Code sinh ở cả hai phía biên dịch được; file type sinh của frontend (`schema.gen.ts`) đã cập nhật.

## Migration (DB-xx)
- [ ] Đúng số version được cấp trong DES-01; không sửa migration đã merge.
- [ ] Ràng buộc NOT NULL/UNIQUE/FK đúng thiết kế; có index cho truy vấn chính (trigram cho tìm kiếm không dấu).
- [ ] Migrate được cả từ DB trống lẫn từ version trước (Testcontainers).
- [ ] Entity và repository nằm trong package `persistence` của đúng module.

## CI/CD và hạ tầng
- [ ] `permissions:` tối thiểu; action ghim version; secret đọc qua `secrets.*`, không in ra log.
- [ ] Lọc job theo đường dẫn để tiết kiệm phút; có `concurrency` để hủy run cũ.
- [ ] Deploy có bước kiểm tra `/actuator/health` và có cách quay lại bản trước.
- [ ] Không mở port hay CORS rộng hơn cần (CORS không mở cả `*.vercel.app`).

## Tài liệu (DOC, ARC, DES)
- [ ] Đúng mẫu của skill `meloo-docs`; thuật ngữ khớp `docs/srs/03-glossary.md`.
- [ ] Số liệu có nguồn; không bịa số khảo sát hay KPI.
- [ ] Sơ đồ PlantUML có cả file nguồn và PNG, khớp với code hiện tại.
