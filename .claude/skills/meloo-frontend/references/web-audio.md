# Phát đoạn nhạc chính xác bằng Web Audio

- Tải file qua `GET /api/v1/audio/{token}` → `ArrayBuffer` → `audioContext.decodeAudioData()`, mỗi vòng giải mã một lần rồi giữ `AudioBuffer`.
- Mỗi lần phát tạo một `AudioBufferSourceNode` mới và gọi `source.start(when, 0, levelSeconds)`. Dừng nguồn trước đó bằng `stop()`, vì một source chỉ start được một lần.
- Độ dài các mức (2/5/10/20/30) lấy từ server, không viết cứng ở nhiều nơi.
- Mở khóa âm thanh: tạo hoặc `resume()` AudioContext ngay trong handler của thao tác người dùng (click, tap), vì Safari/iOS chặn tự phát. Khi `state === 'suspended'` thì hiện nút "Chạm để nghe".
- Tải trước file của vòng kế. Trong chế độ nhiều người, chỉ báo "sẵn sàng" (PREPARE) sau khi đã giải mã xong.
- Đo sai lệch bằng `audioContext.currentTime` lúc bắt đầu và kết thúc (mục tiêu < 50 ms); trong Vitest dùng AudioContext giả.
- Tải hoặc giải mã lỗi: hiện ErrorState kèm nút thử lại; không để vòng chơi treo.
- Không dùng `<audio src>` trỏ thẳng URL của nhà cung cấp nhạc, vì như vậy lộ bài.
