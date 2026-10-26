# Runbook: Nhập kho nhạc & Seed dữ liệu (BE-CAT-02)

> Ticket: MEL-35 · Module: `catalog` · Nguồn sự thật: BR-05, BR-27, ADR-003

Tài liệu hướng dẫn chạy lệnh seed kho nhạc khởi tạo (≥ 1.000 bài hát hợp lệ, 8 thể loại, 8 bộ đề tuyển chọn curated) cho môi trường local và chạy một lần trên production.

Ở Sprint 1 chưa có hệ thống tài khoản ADMIN (được xây dựng ở Sprint 4 / BE-ID-02) và trang quản trị (Sprint 5), nên việc nạp dữ liệu ban đầu được thực hiện bằng lệnh chạy một lần thông qua `ApplicationRunner` (`CatalogSeedRunner`), không qua endpoint HTTP công khai.

---

## 1. Dữ liệu seed khởi tạo
Tập dữ liệu seed đặt tại `backend/src/main/resources/seed/catalog-seed.json`, bao gồm:
- **8 thể loại (Genre):** `vpop`, `usuk-pop`, `kpop`, `ballad`, `rap-hiphop`, `rock`, `edm`, `rnb` (mỗi thể loại có từ 100 đến 414 bài).
- **8 bộ đề tuyển chọn (Curated Playlist):**
  - `vpop-hits-2020s`: V-Pop Hits 2020s (378 bài)
  - `vpop-ballad`: Nhạc Trẻ Ballad Tâm Trạng (235 bài)
  - `vpop-rap-hiphop`: Rap & Hip-hop Việt (70 bài)
  - `vpop-nostalgia-2010s`: V-Pop Thập Niên 2010 (135 bài)
  - `usuk-billboard-hits`: US-UK Billboard Hits (380 bài)
  - `usuk-pop-classics`: US-UK Pop Bất Hủ (518 bài)
  - `kpop-top-hits`: K-Pop Đỉnh Cao (414 bài)
  - `kpop-girlgroups`: K-Pop Girl Groups (188 bài)
- **1.446 bài hát thật từ Deezer:**
  - Lấy từ API công khai của Deezer bằng script tái tạo `backend/src/main/resources/seed/generate-deezer-seed.py`.
  - Phân bổ quốc gia: VN (479), US (276), GB (172), KR (414), CA (35), SE (35), NO (35).
  - Đầy đủ thông tin: tiêu đề gốc, ca sĩ chính, thể loại, quốc gia, năm phát hành, thứ hạng phổ biến.
  - Tên chuẩn hóa (`title_normalized`) theo quy tắc **BR-05** (bỏ dấu tiếng Việt, chữ thường, loại bỏ phần trong ngoặc và hậu tố phiên bản như Remix, Live, Acoustic, MV để gom phiên bản).
  - Ảnh bìa album (`cover_url`) theo **Migration V10** phục vụ màn công bố đáp án.
  - Đảm bảo 100% bài có đoạn preview hợp lệ (`preview_url`), URL đã được lọc bỏ query parameter chứa token ký tạm thời (`hdnea=...`) theo **ADR-003** để không lưu token hết hạn vào repo và giữ URL tài nguyên tĩnh gọn nhẹ.

### Tái tạo file seed khi cần
Để làm mới hoặc tái tạo dữ liệu seed:
```bash
python3 backend/src/main/resources/seed/generate-deezer-seed.py
```
Script tự động phân bổ hạn mức theo từng nhóm nguồn (V-Pop: 450, US-UK: 550, K-Pop: 400), kiểm tra tính hợp lệ và chỉ ghi file khi thỏa mãn:
- Tổng số bài ≥ 1.000 (thực tế: 1.446)
- Đầy đủ 8 thể loại và 8 bộ đề tuyển chọn (mỗi bộ ≥ 20 bài)
- Đủ bài cho cả VN (≥ 300), US (≥ 150), GB (≥ 50), KR (≥ 200).

---

## 2. Chạy seed ở môi trường Local

### Cách 1: Tham số dòng lệnh Spring Boot
Từ thư mục `backend/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--meloo.catalog.seed.enabled=true"
```

### Cách 2: Biến môi trường
```bash
export MELOO_CATALOG_SEED_ENABLED=true
cd backend
./mvnw spring-boot:run
```

### Cách 3: Chạy trong IDE (IntelliJ IDEA / VS Code)
Thêm tham số vào Run Configuration:
- Program arguments: `--meloo.catalog.seed.enabled=true`
- Hoặc Environment variables: `MELOO_CATALOG_SEED_ENABLED=true`

### Dấu hiệu thành công trong log
```text
INFO ... CatalogSeedRunner : Starting one-off catalog seed runner...
INFO ... CatalogSeedService : Catalog seed completed: 8 genres, 8 playlists, 1446 tracks seeded, 2318 playlist links
INFO ... CatalogSeedRunner : One-off catalog seed finished successfully: 8 genres, 8 playlists, 1446 tracks seeded, 2318 playlist links
```

---

## 3. Chạy seed một lần trên Production (EC2 / RDS)

Trên production, kho nhạc chỉ cần seed một lần duy nhất sau khi chạy xong migration database (Flyway V1–V10).

### Lệnh chạy với Docker container
Chạy một container độc lập một lần (one-off container) với cờ `--meloo.catalog.seed.exit-after-seed=true` và `--spring.main.web-application-type=none` để ứng dụng đóng Spring ApplicationContext và tự động thoát (`--rm`) ngay sau khi hoàn thành seed:

```bash
docker run --rm \
  --network meloo-network \
  -e DB_URL="jdbc:postgresql://${RDS_ENDPOINT}:5432/meloo" \
  -e DB_USERNAME="${RDS_USERNAME}" \
  -e DB_PASSWORD="${RDS_PASSWORD}" \
  -e MELOO_JWT_SECRET="${MELOO_JWT_SECRET}" \
  -e MELOO_CATALOG_SEED_ENABLED=true \
  -e MELOO_CATALOG_SEED_EXIT_AFTER_SEED=true \
  ghcr.io/thaidevv/meloo-backend:latest \
  --meloo.catalog.seed.enabled=true \
  --meloo.catalog.seed.exit-after-seed=true \
  --spring.main.web-application-type=none
```

Container sẽ khởi động, chạy Flyway migration, nạp toàn bộ danh mục bài hát vào database PostgreSQL, in thông báo hoàn thành và tự động thoát với mã 0 (`--rm` tự xóa container).

### Kiểm tra kết quả trong Database
Kết nối vào database bằng `psql`:

```sql
-- Kiểm tra số lượng thể loại
SELECT count(*) FROM genre;
-- Kỳ vọng: >= 8

-- Kiểm tra số lượng bộ đề tuyển chọn
SELECT code, name_vi, kind FROM playlist WHERE kind = 'CURATED';
-- Kỳ vọng: 8 bộ đề

-- Kiểm tra số lượng bài hát đã nạp
SELECT count(*) FROM track WHERE enabled = true;
-- Kỳ vọng: >= 1000 (thực tế: 1446 bài)

-- Kiểm tra phân bổ quốc gia
SELECT country_code, count(*) FROM track GROUP BY country_code ORDER BY count(*) DESC;
-- Kỳ vọng: VN (479), KR (414), US (276), GB (172)...

-- Kiểm tra cột cover_url của Migration V10
SELECT title, cover_url FROM track WHERE cover_url IS NOT NULL LIMIT 5;
```

---

## 4. Tính chất Idempotent (An toàn khi chạy lặp lại)
Cơ chế import và seed được thiết kế đảm bảo **idempotent**:
1. **Chống trùng bài hát:** Dựa trên ràng buộc `(provider, provider_track_id)`. Nếu bài đã tồn tại trong DB, hệ thống giữ nguyên bài cũ, chỉ bổ sung `cover_url` nếu bản ghi trước đó để rỗng.
2. **Chống trùng nghệ sĩ:** Dựa trên `(provider, provider_artist_id)`.
3. **Chống trùng liên kết bộ đề:** Kiểm tra `PlaylistTrackId(playlistId, trackId)` trước khi thêm. Vị trí `position` được tính toán tiếp nối từ `max(position) + 1` của playlist hiện tại nên không bao giờ bị đụng độ.
4. Chạy lại lệnh seed nhiều lần sẽ **không** sinh thêm bản ghi rác hay gây lỗi ràng buộc `unique`.

---

## 5. Job định kỳ kiểm tra Preview & Vô hiệu hóa bài lỗi (BR-27)
Theo quy tắc nghiệp vụ **BR-27**, các bài hát có đoạn preview bị hỏng hoặc nhà cung cấp gỡ bỏ sẽ tự động bị vô hiệu hóa để không xuất hiện trong các trận đấu mới.

### Cơ chế quét xoay vòng (Round-robin)
Job quét các bài hát theo thứ tự `ORDER BY last_preview_checked_at NULLS FIRST, created_at ASC` với giới hạn `batchSize` (mặc định 100 bài/lần quét):
- Các bài chưa từng được kiểm tra (`last_preview_checked_at IS NULL`) sẽ được ưu tiên kiểm tra trước.
- Sau mỗi lần kiểm tra, `last_preview_checked_at` được cập nhật thành thời điểm hiện tại.
- Cơ chế này đảm bảo qua các đêm, toàn bộ kho nhạc (1.446+ bài) đều được quét xoay vòng đều đặn, không bị lặp lại chỉ 100 bài đầu tiên.
- Các lỗi mạng tạm thời (HTTP 429 rate limit, 5xx server error, timeout, open circuit breaker) **không làm tắt bài** (`enabled` giữ nguyên `true`). Job ngắt batch sớm khi gặp rate limit hoặc circuit breaker để tránh false positive.

### Cấu hình kích hoạt Job
Mặc định job tắt trên môi trường local. Để bật trên server hoặc production:

```bash
# Biến môi trường bật job
export MELOO_CATALOG_IMPORT_HEALTH_CHECK_ENABLED=true

# Cấu hình chu kỳ cron (mặc định: 3h sáng hàng ngày)
export MELOO_CATALOG_IMPORT_HEALTH_CHECK_CRON="0 0 3 * * *"

# Cấu hình kích thước batch mỗi lần chạy (mặc định: 100)
export MELOO_CATALOG_IMPORT_HEALTH_CHECK_BATCH_SIZE=100
```
