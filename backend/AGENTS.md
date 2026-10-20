# AGENTS.md — Backend

Áp dụng cùng luật ở `../AGENTS.md`. Trước khi sửa Java hoặc cấu hình backend, đọc skill
`meloo-backend` trong `.agents/skills/meloo-backend/SKILL.md` (Codex) hoặc
`.claude/skills/meloo-backend/SKILL.md` (Claude).

## Yêu cầu

- Java 21.
- Docker đang chạy cho integration test PostgreSQL 17.
- Không dùng H2.

## Lệnh chính

```bash
# Format rồi chạy toàn bộ compile, unit/integration test, Modulith, JaCoCo
./mvnw spotless:apply verify

# Chỉ kiểm tra format, không sửa file
./mvnw spotless:check

# Chạy một test
./mvnw -Dtest=TenTest test

# Sinh Spring API interfaces/models sau khi contracts/openapi.yaml tồn tại
./mvnw -Pgenerate-api generate-sources
```

Chạy các lệnh từ thư mục `backend/`. File sinh từ OpenAPI không được sửa tay.

## Local database

Từ repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
cd backend
./mvnw spring-boot:run
```

Các biến cấu hình local và lệnh dừng/xóa dữ liệu nằm trong
`../docs/runbooks/local-dev.md`.
