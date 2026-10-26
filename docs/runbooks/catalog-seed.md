# Runbook: Nhập kho nhạc & Seed dữ liệu (BE-CAT-02)

> Ticket: MEL-35 · Module: `catalog` · Nguồn sự thật: BR-05, BR-27, ADR-003

Tài liệu hướng dẫn chạy lệnh seed kho nhạc khởi tạo (≥ 1.000 bài hát hợp lệ, 8 thể loại, 8 bộ đề tuyển chọn curated) cho môi trường local và chạy một lần trên production.

Ở Sprint 1 chưa có hệ thống tài khoản ADMIN (được xây dựng ở Sprint 4 / BE-ID-02) và trang quản trị (Sprint 5), nên việc nạp dữ liệu ban đầu được thực hiện bằng lệnh chạy một lần thông qua `ApplicationRunner` (`CatalogSeedRunner`), không qua endpoint HTTP công khai.

---

## 1. Dữ liệu seed khởi tạo
Tập dữ liệu seed đặt tại `backend/src/main/resources/seed/catalog-seed.json`, bao gồm:
- **8 thể loại (Genre):** `vpop`, `usuk-pop`, `kpop`, `ballad`, `rap-hiphop`, `rock`, `edm`, `rnb`.
- **8 bộ đề tuyển chọn (Curated Playlist):**
  - `vpop-hits-2020s`: V-Pop Hits 2020s
  - `vpop-ballad`: Nhạc Trẻ Ballad Tâm Trạng
  - `vpop-rap-hiphop`: Rap & Hip-hop Việt
  - `vpop-nostalgia-2010s`: V-Pop Thập Niên 2010
  - `usuk-billboard-hits`: US-UK Billboard Hits
  - `usuk-pop-classics`: US-UK Pop Bất Hủ
  - `kpop-top-hits`: K-Pop Đỉnh Cao
  - `kpop-girlgroups`: K-Pop Girl Groups
- **≥ 1.000 bài hát (Track):**
  - Đầy đủ thông tin: tiêu đề gốc, ca sĩ chính, thể loại, quốc gia (VN, US, KR, GB...), năm phát hành, thứ hạng phổ biến.
  - Tên chuẩn hóa (`title_normalized`) theo quy tắc **BR-05** (bỏ dấu, viết thường, loại bỏ phần trong ngoặc và hậu tố phiên bản như Remix, Live, Acoustic, MV để gom phiên bản).
  - Ảnh bìa album (`cover_url`) theo **Migration V10** phục vụ màn công bố đáp án.
  - Đảm bảo 100% bài có đoạn preview hợp lệ (`preview_url`), bài thiếu preview bị tự động bỏ qua.
  - Không lưu trực tiếp `preview_url` vào cơ sở dữ liệu theo **ADR-003** do URL Deezer hết hạn sau ~15 phút.

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
# hoặc MELOO_CATALOG_IMPORT_SEED_ENABLED=true
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
INFO ... CatalogSeedService : Catalog seed completed: 8 genres, 8 playlists, 1202 tracks seeded, ... playlist links
INFO ... CatalogSeedRunner : One-off catalog seed finished successfully: 8 genres, 8 playlists, 1202 tracks seeded, ... playlist links
```

---

## 3. Chạy seed một lần trên Production (EC2 / RDS)

Trên production, kho nhạc chỉ cần seed một lần duy nhất sau khi chạy xong migration database (Flyway V1–V10).

### Lệnh chạy với Docker container
Chạy một container độc lập một lần (one-off container):

```bash
docker run --rm \
  --network meloo-network \
  -e DB_URL="jdbc:postgresql://${RDS_ENDPOINT}:5432/meloo" \
  -e DB_USERNAME="${RDS_USERNAME}" \
  -e DB_PASSWORD="${RDS_PASSWORD}" \
  -e MELOO_JWT_SECRET="${MELOO_JWT_SECRET}" \
  -e MELOO_CATALOG_SEED_ENABLED=true \
  ghcr.io/thaidevv/meloo-backend:latest \
  --meloo.catalog.seed.enabled=true
```

Container sẽ khởi động, chạy Flyway migration, nạp toàn bộ danh mục bài hát vào database PostgreSQL, và tự động thoát (`--rm`).

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
-- Kỳ vọng: >= 1000 (thực tế: 1202 bài)

-- Kiểm tra cột cover_url của Migration V10
SELECT title, cover_url FROM track WHERE cover_url IS NOT NULL LIMIT 5;
```

---

## 4. Tính chất Idempotent (An toàn khi chạy lặp lại)
Cơ chế import và seed được thiết kế đảm bảo **idempotent**:
1. **Chống trùng bài hát:** Dựa trên ràng buộc `(provider, provider_track_id)`. Nếu bài đã tồn tại trong DB, hệ thống giữ nguyên bài cũ, chỉ bổ sung `cover_url` nếu bản ghi trước đó để rỗng.
2. **Chống trùng nghệ sĩ:** Dựa trên `(provider, provider_artist_id)`.
3. **Chống trùng liên kết bộ đề:** Kiểm tra `PlaylistTrackId(playlistId, trackId)` trước khi thêm.
4. Chạy lại lệnh seed nhiều lần sẽ **không** sinh thêm bản ghi rác hay gây lỗi ràng buộc `unique`.

---

## 5. Job định kỳ kiểm tra Preview & Vô hiệu hóa bài lỗi (BR-27)
Theo quy tắc nghiệp vụ **BR-27**, các bài hát có đoạn preview bị hỏng hoặc nhà cung cấp gỡ bỏ sẽ tự động bị vô hiệu hóa để không xuất hiện trong các trận đấu mới.

### Cấu hình kích hoạt Job
Mặc định job tắt trên môi trường local. Để bật trên server hoặc production:

```bash
# Biến môi trường
export MELOO_CATALOG_IMPORT_HEALTH_CHECK_ENABLED=true

# Cấu hình chu kỳ cron (mặc định: 3h sáng hàng ngày)
export MELOO_CATALOG_IMPORT_HEALTH_CHECK_CRON="0 0 3 * * *"
```

Khi phát hiện bài lỗi:
- `track.enabled` đổi thành `false`
- `track.disabled_reason` ghi rõ lý do (ví dụ: `Preview unavailable: missing from provider or broken link`)
- Dữ liệu lịch sử trận đấu cũ không bị ảnh hưởng vì track không bị xóa vật lý.
