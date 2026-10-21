# ADR-007: Trạng thái phòng trong bộ nhớ, mỗi phòng một hàng đợi lệnh tuần tự
- Trạng thái: Chấp nhận
- Ngày: 2026-10-21 · Ticket: MEL-26

## Bối cảnh
Trong một phòng, nhiều sự việc xảy ra gần như cùng lúc: người vào và rời, bấm sẵn sàng, đoán, hết giờ, mất kết nối. Server là trọng tài nên kết quả phải nhất quán: ví dụ "hết giờ" và "trả lời" đến cùng lúc thì chỉ một bên thắng, và hai người không cùng chiếm chỗ cuối. Trạng thái phòng sống ngắn (một ván 10–15 phút), cần đọc ghi rất nhanh. Chỉ có một instance backend (ADR-006).

## Các phương án
| Phương án | Ưu | Nhược |
|---|---|---|
| Trạng thái trong DB, khóa dòng (`SELECT ... FOR UPDATE`) | Bền, đa instance | Chậm với thao tác nhỏ dồn dập; khóa và deadlock; nhiều round-trip |
| Trạng thái trong bộ nhớ, dùng `synchronized`/khóa | Nhanh | Dễ sai thứ tự khóa; hẹn giờ sửa state trực tiếp gây race |
| **Trạng thái trong bộ nhớ, mỗi phòng một hàng đợi lệnh xử lý tuần tự (actor)** | Không race trong phòng, không cần khóa; logic dễ suy luận và test | Mất khi restart; không chạy nhiều instance |

## Quyết định
`RoomRegistry` giữ một `RoomActor` cho mỗi phòng. Mỗi actor có hàng đợi lệnh và xử lý tuần tự trên executor riêng.
- Mọi thay đổi trạng thái là một lệnh `submit(cmd)`: vào, rời, sẵn sàng, bắt đầu, nghe thêm, đoán, hết giờ, mất kết nối. Không có đường ghi state nào khác.
- Bộ hẹn giờ không sửa state; đến giờ nó gửi lệnh `Timeout(roundId)` vào hàng đợi.
- Lệnh mang `roundId`; lệnh đến muộn cho vòng đã đóng thì bỏ qua. Lệnh vào hàng đợi trước thì thắng.
- State trong actor không chia sẻ ra ngoài, bên ngoài chỉ đọc snapshot bất biến. Xử lý xong mỗi lệnh thì phát sự kiện (ADR-004) kèm `serverTime`.
- Kết quả bền (lịch sử trận, điểm) ghi xuống DB khi trận kết thúc, qua sự kiện `MatchFinished`.

## Hệ quả
- Tốt: hết race trong phòng mà không cần khóa; luật "ai nhanh hơn" xác định theo thứ tự hàng đợi; test được bằng `Clock` giả.
- Xấu hoặc rủi ro: restart hoặc deploy làm mất phòng đang chơi (chấp nhận ở MVP, client báo lỗi rõ và về sảnh; giảm bằng deploy ngoài giờ cao điểm). Không mở rộng ngang được: cần định tuyến phòng theo instance hoặc lưu state ngoài (Phase 2). Phòng treo làm rò bộ nhớ: cần dọn phòng rỗng theo thời gian. Một lệnh chậm chặn cả phòng: lệnh không được gọi I/O chậm.

## Câu hỏi bảo vệ
1. Vì sao không khóa DB? — Thao tác nhỏ và dồn dập; hàng đợi tuần tự đơn giản và nhanh hơn, còn dữ liệu bền ghi khi trận kết thúc.
2. "Hết giờ trùng lúc trả lời" xử lý ra sao? — Cả hai là lệnh trong cùng hàng đợi; cái vào trước thắng, cái sau thấy vòng đã đóng và bị bỏ.
3. Mất gì khi restart? — Các phòng đang chơi; lịch sử trận đã xong thì còn trong DB.
4. Khi nào phải đổi thiết kế? — Khi cần nhiều instance: thêm định tuyến phòng hoặc lưu state ngoài như Redis.
