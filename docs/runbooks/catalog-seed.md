# Runbook: Nhập kho nhạc & Seed dữ liệu (BE-CAT-02)

> Ticket: MEL-35 · Module: `catalog` · Nguồn sự thật: BR-05, BR-27, ADR-003

Tài liệu hướng dẫn chạy lệnh seed kho nhạc khởi tạo (≥ 1.000 bài hát hợp lệ, 8 thể loại, 8 bộ đề tuyển chọn curated) cho môi trường local và chạy một lần trên production.

Ở Sprint 1 chưa có hệ thống tài khoản ADMIN (được xây dựng ở Sprint 4 / BE-ID-02) và trang quản trị (Sprint 5), nên việc nạp dữ liệu ban đầu được thực hiện bằng lệnh chạy một lần thông qua `ApplicationRunner` (`CatalogSeedRunner`), không qua endpoint HTTP công khai.

---

## 1. Dữ liệu seed khởi tạo
Tập dữ liệu seed đặt tại `backend/src/main/resources/seed/catalog-seed.json`, bao gồm:
- **8 thể loại (Genre):** `kpop` (558), `usuk-pop` (384), `vpop` (224), `ballad` (223), `rap-hiphop` (202), `rnb` (159), `rock` (89), `edm` (75).
- **8 bộ đề tuyển chọn (Curated Playlist):**
  - `vpop-hits-2020s`: V-Pop Hits 2020s (418 bài — 100% ca sĩ Việt Nam)
  - `vpop-ballad`: Nhạc Trẻ Ballad Tâm Trạng (191 bài — 100% ca sĩ Việt Nam)
  - `vpop-rap-hiphop`: Rap & Hip-hop Việt (137 bài — 100% rapper/nghệ sĩ Hip-hop Việt Nam)
  - `vpop-nostalgia-2010s`: V-Pop Thập Niên 2010 (156 bài — 100% bài có năm phát hành thật 2010–2019 của ca sĩ Việt Nam)
  - `usuk-billboard-hits`: US-UK Billboard Hits (507 bài — 100% nghệ sĩ Âu Mỹ)
  - `usuk-pop-classics`: US-UK Pop Bất Hủ (509 bài — 100% bài phát hành trước năm 2020 của nghệ sĩ Âu Mỹ)
  - `kpop-top-hits`: K-Pop Đỉnh Cao (558 bài — 100% nghệ sĩ Hàn Quốc)
  - `kpop-girlgroups`: K-Pop Girl Groups (308 bài — 100% nhóm nữ / nữ ca sĩ Hàn Quốc)
- **1.914 bài hát thật từ Deezer:**
  - Lấy từ API công khai của Deezer bằng script tái tạo `backend/src/main/resources/seed/generate-deezer-seed.py`.
  - Được thu thập từ danh bạ 85 nghệ sĩ chuẩn hóa (35 V-Pop, 22 K-Pop, 28 US-UK) ngăn chặn 100% việc lẫn lộn ca sĩ, quốc gia hay thể loại (Beyoncé 100% US / R&B, Taylor Swift 100% US / Pop, Stray Kids 100% KR / K-pop, Coldplay 100% GB / Rock...).
  - Phân bổ quốc gia chuẩn hóa: VN (584), KR (558), US (469), GB (183), CA (85), NO (20), SE (15).
  - 100% bài hát (1.914/1.914) có năm phát hành thật (`releaseYear`) truy vấn trực tiếp từ API `/track/{id}` của Deezer (0 bài bị null).
  - 100% bài hát là bản thu gốc nguyên bản (original track): tự động sàng lọc loại bỏ toàn bộ bản Remix, Live, Acoustic, Version, Cover, Instrumental, Karaoke, Slowed... per BR-05 & BR-09.
  - Khử trùng triệt để: mỗi nghệ sĩ chỉ sở hữu tối đa 1 bài cho mỗi tên bài hát chuẩn hóa `title_normalized` (0 bài trùng tên cùng nghệ sĩ).
  - Tên chuẩn hóa (`title_normalized`) theo quy tắc **BR-05** (bỏ dấu tiếng Việt bao gồm đ/Đ -> d, chữ thường, loại bỏ phần trong ngoặc và hậu tố phiên bản).
  - Ảnh bìa album (`cover_url`) theo **Migration V10** phục vụ màn công bố đáp án.
  - Đảm bảo 100% bài có đoạn preview hợp lệ (`preview_url`), URL đã được lọc bỏ query parameter chứa token ký tạm thời (`hdnea=...`) theo **ADR-003** để không lưu token hết hạn vào repo và giữ URL tài nguyên tĩnh gọn nhẹ.

### Tái tạo file seed khi cần
Để làm mới hoặc tái tạo dữ liệu seed:
```bash
python3 backend/src/main/resources/seed/generate-deezer-seed.py
```
Script tự động kiểm tra nghiêm ngặt tính toàn vẹn dữ liệu trước khi xuất file:
- Tổng số bài ≥ 1.000 (thực tế: 1.914)
- 100% bài hát có `release_year` thật (tỷ lệ null = 0.0%)
- 100% bài hát có `popularityRank` ≥ 1 và `providerArtistId` số chuẩn Deezer
- 0 bản thu phiên bản (Remix, Live, Acoustic, Instrumental, Cover...)
- 0 cặp bài trùng `title_normalized` cho cùng một nghệ sĩ
- Đầy đủ 8 thể loại (mỗi thể loại ≥ 75 bài) và 8 bộ đề tuyển chọn (mỗi bộ ≥ 130 bài)
- Không có ca sĩ nào có nhiều hơn một cặp (quốc gia, thể loại)
- Toàn bộ bài trong `vpop-*` thuộc quốc gia VN, `kpop-*` thuộc KR, `usuk-*` thuộc Âu Mỹ
- Bộ `vpop-nostalgia-2010s` chỉ chứa bài 2010–2019; `usuk-pop-classics` chỉ chứa bài trước 2020.

---

## 2. Chạy seed ở môi trường Local

### Cách 1: Tham số dòng lệnh Spring Boot
Từ thư mục `backend/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--meloo.catalog.seed.enabled=true"
```

Tùy chọn chỉ định đường dẫn file seed khác (mặc định: `classpath:seed/catalog-seed.json`):
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--meloo.catalog.seed.enabled=true --meloo.catalog.seed.seed-path=file:/path/to/custom-seed.json"
```

### Cách 2: Biến môi trường
```bash
export MELOO_CATALOG_SEED_ENABLED=true
cd backend
./mvnw spring-boot:run
```

### Dấu hiệu thành công trong log
```text
INFO ... CatalogSeedRunner : Starting one-off catalog seed runner...
INFO ... CatalogSeedService : Catalog seed completed: 8 genres, 8 playlists, 1914 tracks seeded, 2784 playlist links
INFO ... CatalogSeedRunner : One-off catalog seed finished successfully: 8 genres, 8 playlists, 1914 tracks seeded, 2784 playlist links
```

---

## 3. Chạy seed một lần trên Production (EC2 / RDS)

Trên production, kho nhạc chỉ cần seed một lần duy nhất sau khi chạy xong migration database (Flyway V1–V10).

### Lệnh chạy với Docker container
Chạy một container độc lập một lần (one-off container) với cờ cấu hình `--meloo.catalog.seed.enabled=true` và `--meloo.catalog.seed.exit-after-seed=true` kết hợp `--spring.main.web-application-type=none`:

```bash
docker run --rm \
  --network meloo-network \
  -e DB_URL="jdbc:postgresql://${RDS_ENDPOINT}:5432/meloo" \
  -e DB_USERNAME="${RDS_USERNAME}" \
  -e DB_PASSWORD="${RDS_PASSWORD}" \
  -e MELOO_JWT_SECRET="${MELOO_JWT_SECRET}" \
  -e MELOO_AUDIO_HMAC_SECRET="${MELOO_AUDIO_HMAC_SECRET}" \
  ghcr.io/thaidevv/meloo-backend:latest \
  --meloo.catalog.seed.enabled=true \
  --meloo.catalog.seed.exit-after-seed=true \
  --spring.main.web-application-type=none
```

Container sẽ khởi động, chạy Flyway migration, nạp toàn bộ danh mục bài hát vào database PostgreSQL, in thông báo hoàn thành và tự động đóng ApplicationContext và thoát tiến trình sạch sẽ (`SpringApplication.exit(applicationContext, () -> 0)` rồi `System.exit(0)`) với mã 0 (`--rm` tự xóa container).

### Kiểm tra kết quả trong Database
Kết nối vào database bằng `psql`:

```sql
-- Kiểm tra số lượng thể loại
SELECT count(*) FROM genre;
-- Kỳ vọng: 8

-- Kiểm tra số lượng bộ đề tuyển chọn
SELECT code, name_vi, kind FROM playlist WHERE kind = 'CURATED';
-- Kỳ vọng: 8 bộ đề

-- Kiểm tra số lượng bài hát đã nạp
SELECT count(*) FROM track WHERE enabled = true;
-- Kỳ vọng: >= 1000 (thực tế: 1914 bài)

-- Kiểm tra phân bổ quốc gia
SELECT country_code, count(*) FROM track GROUP BY country_code ORDER BY count(*) DESC;
-- Kỳ vọng: VN (584), KR (558), US (469), GB (183), CA (85), NO (20), SE (15)

-- Kiểm tra năm phát hành thật của bộ đề thập niên 2010
SELECT t.title, t.release_year, t.country_code FROM track t
JOIN playlist_track pt ON t.id = pt.track_id
JOIN playlist p ON pt.playlist_id = p.id
WHERE p.code = 'vpop-nostalgia-2010s' LIMIT 5;
-- Kỳ vọng: release_year thuộc [2010, 2019] và country_code = 'VN'
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
- Sau mỗi lần kiểm tra, `last_preview_checked_at` được cập nhật thành thời điểm hiện tại (`markCheckedInTransaction`, áp dụng cho cả bài active lẫn bài của provider khác để không nghẽn hàng đợi).
- Cơ chế này đảm bảo qua các đêm, toàn bộ kho nhạc (2.148+ bài) đều được quét xoay vòng đều đặn, không bị lặp lại chỉ 100 bài đầu tiên.
- Các lỗi mạng tạm thời (HTTP 429 rate limit, 5xx server error, timeout, open circuit breaker) **không làm tắt bài** (`enabled` giữ nguyên `true`). Job ngắt batch sớm khi gặp rate limit hoặc circuit breaker để tránh false positive.

### Cấu hình kích hoạt Job
Mặc định job tắt trên môi trường local. Để bật trên server hoặc production:

Quy tắc Relaxed Binding chuẩn của Spring Boot loại bỏ dấu gạch nối khi map từ biến môi trường:
- Dạng chuẩn khuyến nghị (không dấu gạch):
  ```bash
  export MELOO_CATALOG_HEALTHCHECK_ENABLED=true
  export MELOO_CATALOG_HEALTHCHECK_CRON="0 0 3 * * *"
  export MELOO_CATALOG_HEALTHCHECK_BATCHSIZE=100
  ```
- Dạng mở rộng có dấu gạch dưới (Spring Boot relaxed binding vẫn hỗ trợ tương thích):
  ```bash
  export MELOO_CATALOG_HEALTH_CHECK_ENABLED=true
  export MELOO_CATALOG_HEALTH_CHECK_CRON="0 0 3 * * *"
  export MELOO_CATALOG_HEALTH_CHECK_BATCH_SIZE=100
  ```
Cả hai dạng đã được kiểm chứng tự động trong `CatalogImportPropertiesTest`.
