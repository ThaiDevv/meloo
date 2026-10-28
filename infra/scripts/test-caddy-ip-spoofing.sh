#!/usr/bin/env bash
# ==============================================================================
# test-caddy-ip-spoofing.sh — Kiểm thử xác minh MEL-127
#
# Kiểm tra:
# 1. Caddyfile hợp lệ và được adapt thành cấu hình reverse_proxy có header_up
#    ghi đè X-Forwarded-For={remote_host}, không cấu hình trusted_proxies.
# 2. Compose production không expose cổng backend ra host (chỉ expose mạng nội bộ),
#    và cấu hình Tomcat access log để ghi nhận IP client thật ra stdout (dùng %m
#    thay vì %r để không lộ raw audio bearer token theo contracts/openapi.yaml:554).
# 3. [TEST BẮT BUỘC TRÊN COMPOSE PRODUCTION THẬT]
#    - Khởi động stack Compose production (docker compose -f infra/compose/docker-compose.prod.yml)
#    - [Test 2] Probe trực tiếp cổng 8080 của backend từ host -> Bị từ chối kết nối (Connection refused).
#    - [Test 1] Gửi 6 lần POST /api/v1/auth/guest qua Caddy với các IP giả khác nhau:
#      + Lần 1..5: HTTP 201 Created
#      + Lần 6: HTTP 429 Too Many Requests (BR-02 rate limit 5 req/h/IP)
#      + Kiểm tra Tomcat access log: IP socket thật được ghi nhận, không bị giả mạo.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
CADDYFILE="$REPO_ROOT/infra/caddy/Caddyfile"
COMPOSE_PROD="$REPO_ROOT/infra/compose/docker-compose.prod.yml"
COMPOSE_AWS="$REPO_ROOT/infra/aws/docker-compose.prod.yml"

echo "=== [1/3] KIỂM TRA CÚ PHÁP VÀ JSON ADAPT CỦA CADDYFILE ==="
docker run --rm \
  -v "$CADDYFILE":/etc/caddy/Caddyfile:ro,z \
  -e API_DOMAIN=api.meloo.local \
  -e ACME_EMAIL=admin@meloo.local \
  caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
echo "✅ Cú pháp Caddyfile hợp lệ."

ADAPTED_JSON=$(docker run --rm \
  -v "$CADDYFILE":/etc/caddy/Caddyfile:ro,z \
  -e API_DOMAIN=api.meloo.local \
  -e ACME_EMAIL=admin@meloo.local \
  caddy:2-alpine caddy adapt --config /etc/caddy/Caddyfile)

python3 -c '
import json, sys
data = json.loads(sys.argv[1])
servers = data.get("apps", {}).get("http", {}).get("servers", {})
found_header_up = False
found_trusted_proxies = False

for srv_name, srv in servers.items():
    for route in srv.get("routes", []):
        for h in route.get("handle", []):
            for subroute in h.get("routes", []):
                for handler in subroute.get("handle", []):
                    if handler.get("handler") == "reverse_proxy":
                        headers = handler.get("headers", {}).get("request", {}).get("set", {})
                        xff = headers.get("X-Forwarded-For", [])
                        if "{http.request.remote.host}" in xff:
                            found_header_up = True
                        if "trusted_proxies" in handler:
                            found_trusted_proxies = True

if not found_header_up:
    print("❌ Lỗi: reverse_proxy chưa cấu hình ghi đè X-Forwarded-For={remote_host}", file=sys.stderr)
    sys.exit(1)

if found_trusted_proxies:
    print("❌ Lỗi: reverse_proxy không được đặt trusted_proxies ở chế độ kết nối trực tiếp", file=sys.stderr)
    sys.exit(1)

print("✅ Đã kiểm tra JSON: reverse_proxy ghi đè X-Forwarded-For={remote_host} và không có trusted_proxies.")
' "$ADAPTED_JSON"

echo "=== [2/3] KIỂM TRA ĐÓNG CỔNG VÀ ACCESS LOG TRONG COMPOSE FILES ==="
for file in "$COMPOSE_PROD" "$COMPOSE_AWS"; do
  python3 -c '
import sys

filename = sys.argv[1]
with open(filename, "r") as f:
    content = f.read()
    lines = content.splitlines()

in_backend = False
backend_has_ports = False
backend_has_expose = False

for line in lines:
    raw = line.rstrip()
    stripped = raw.strip()
    if stripped == "backend:":
        in_backend = True
        continue
    if in_backend and stripped.endswith(":") and not stripped.startswith("#"):
        if line.startswith("  ") and not line.startswith("    "):
            in_backend = False
    if in_backend:
        if stripped == "ports:":
            backend_has_ports = True
        if stripped == "expose:":
            backend_has_expose = True

if backend_has_ports:
    print(f"❌ Lỗi: {filename} cấu hình ports cho backend (vi phạm AC2)", file=sys.stderr)
    sys.exit(1)

if not backend_has_expose:
    print(f"❌ Lỗi: {filename} chưa cấu hình expose cho backend", file=sys.stderr)
    sys.exit(1)

if "SERVER_TOMCAT_ACCESSLOG_ENABLED=true" not in content:
    print(f"❌ Lỗi: {filename} chưa kích hoạt access log Tomcat", file=sys.stderr)
    sys.exit(1)

# Kiểm tra M3: Không dùng %r trong SERVER_TOMCAT_ACCESSLOG_PATTERN để tránh lộ raw audio bearer token
pattern_line = [l for l in lines if "SERVER_TOMCAT_ACCESSLOG_PATTERN=" in l]
if pattern_line and "%r" in pattern_line[0]:
    print(f"❌ Lỗi: {filename} dùng %r trong pattern access log (nguy cơ lộ raw token audio)", file=sys.stderr)
    sys.exit(1)

if not pattern_line or "%m" not in pattern_line[0]:
    print(f"❌ Lỗi: {filename} pattern access log cần dùng %m (HTTP method)", file=sys.stderr)
    sys.exit(1)

print(f"✅ Đã kiểm tra {filename}: backend chỉ expose nội bộ, access log dùng %m (không lộ token).")
' "$file"
done

echo "=== [3/3] THỰC THI 2 TEST BẮT BUỘC TRÊN DOCKER COMPOSE PRODUCTION THẬT ==="
PROJECT_NAME="meloo-test-$$"
TEST_PORT="18088"
TMP_OVERRIDE=$(mktemp /tmp/meloo-compose-override-XXXXXX.yml)

cleanup() {
  echo "Đang dọn dẹp môi trường Compose test..."
  docker compose -p "$PROJECT_NAME" \
    -f "$COMPOSE_PROD" \
    -f "$TMP_OVERRIDE" \
    down -v --remove-orphans >/dev/null 2>&1 || true
  rm -f "$TMP_OVERRIDE"
}
trap cleanup EXIT

# Tạo file override test: bổ sung service postgres và ánh xạ cổng Caddy sang TEST_PORT
# LƯU Ý: Không can thiệp ports của service backend để kiểm tra đúng cấu hình production!
cat << EOF > "$TMP_OVERRIDE"
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: meloo
      POSTGRES_USER: meloo
      POSTGRES_PASSWORD: meloo
    networks:
      - meloo-internal
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U meloo -d meloo"]
      interval: 1s
      timeout: 2s
      retries: 20

  backend:
    depends_on:
      postgres:
        condition: service_healthy

  caddy:
    ports: !override
      - "${TEST_PORT}:80"
    volumes: !override
      - ${CADDYFILE}:/etc/caddy/Caddyfile:ro
EOF

echo "Khởi động stack Compose production (kèm test override)..."
export API_DOMAIN="http://:80"
export ACME_EMAIL="admin@test.local"
export DB_URL="jdbc:postgresql://postgres:5432/meloo"
export DB_USERNAME="meloo"
export DB_PASSWORD="meloo"
export MELOO_CORS_ALLOWED_ORIGINS="*"
export MELOO_JWT_SECRET="supersecretjwtsecretkeyformeloo123456789"

docker compose -p "$PROJECT_NAME" \
  -f "$COMPOSE_PROD" \
  -f "$TMP_OVERRIDE" \
  up -d >/dev/null

# 1. Chờ backend sẵn sàng qua readiness probe
echo "Đang chờ backend Spring Boot sẵn sàng..."
READY=0
for i in $(seq 1 35); do
  if docker compose -p "$PROJECT_NAME" -f "$COMPOSE_PROD" -f "$TMP_OVERRIDE" \
      exec -T backend wget -qO- http://localhost:8080/actuator/health/readiness </dev/null 2>/dev/null | grep -q '"UP"'; then
    READY=1
    echo "✅ Backend đã sẵn sàng (readiness: UP) sau ${i}s."
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "❌ Lỗi: Backend không sẵn sàng sau 35s!" >&2
  exit 1
fi

# 2. [TEST BẮT BUỘC 2] Kiểm tra đóng cổng backend từ host ngoài Docker
echo ">> [Test bắt buộc 2] Kiểm tra kết nối trực tiếp tới cổng backend 8080 từ host..."
MAPPED_PORT=$(docker compose -p "$PROJECT_NAME" -f "$COMPOSE_PROD" -f "$TMP_OVERRIDE" port backend 8080 2>/dev/null || true)
if [ -n "$MAPPED_PORT" ] && [ "$MAPPED_PORT" != ":0" ]; then
  echo "❌ Lỗi: docker compose port backend 8080 phát hiện cổng đã bị ánh xạ ra host: $MAPPED_PORT" >&2
  exit 1
fi

DIRECT_EXIT=0
curl -s --connect-timeout 2 "http://127.0.0.1:8080/actuator/health" >/dev/null 2>&1 || DIRECT_EXIT=$?
if [ "$DIRECT_EXIT" -eq 0 ]; then
  echo "❌ Lỗi: Cổng 8080 của backend vẫn kết nối được từ ngoài host!" >&2
  exit 1
fi
echo "✅ Test bắt buộc 2 ĐẠT: Backend không publish cổng 8080 ra host, kết nối trực tiếp bị từ chối (exit code $DIRECT_EXIT - Connection refused)."

# 3. [TEST BẮT BUỘC 1] Gửi 6 lần POST /api/v1/auth/guest qua Caddy với IP giả khác nhau
echo ">> [Test bắt buộc 1] Gửi 6 lần POST /api/v1/auth/guest qua Caddy với các header X-Forwarded-For giả khác nhau..."
for i in $(seq 1 6); do
  FAKE_IP="10.0.0.$((10 + i))"
  # Sửa M4: Dùng -o /dev/null, tuyệt đối không lưu body response (chứa accessToken) ra file tạm hoặc in ra terminal
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Forwarded-For: $FAKE_IP" \
    -X POST "http://127.0.0.1:${TEST_PORT}/api/v1/auth/guest" \
    -H "Content-Type: application/json" \
    -d "{\"nickname\":\"Tester${i}\"}")

  echo "   [Request $i] Header gửi: X-Forwarded-For: $FAKE_IP -> HTTP Status: $STATUS"

  if [ "$i" -le 5 ]; then
    if [ "$STATUS" -ne 201 ]; then
      echo "❌ Lỗi: Request $i mong đợi HTTP 201 nhưng nhận được $STATUS" >&2
      exit 1
    fi
  else
    if [ "$STATUS" -ne 429 ]; then
      echo "❌ Lỗi: Request 6 mong đợi HTTP 429 nhưng nhận được $STATUS" >&2
      exit 1
    fi
  fi
done
echo "✅ Test bắt buộc 1 ĐẠT: 5 lần đầu trả về HTTP 201, lần thứ 6 trả về HTTP 429 (Too Many Requests)."

# 4. Kiểm tra access log của backend (đảm bảo không chứa IP giả và không lộ token)
echo ">> Kiểm tra Tomcat access log của backend container..."
BACKEND_LOGS=$(docker compose -p "$PROJECT_NAME" -f "$COMPOSE_PROD" -f "$TMP_OVERRIDE" logs backend 2>&1 | grep '"POST" 201\|"POST" 429' | tail -n 6)
echo "$BACKEND_LOGS"

if echo "$BACKEND_LOGS" | grep -qE "10\.0\.0\."; then
  echo "❌ Lỗi: Log backend vẫn chứa IP giả mạo 10.0.0.x!" >&2
  exit 1
fi
echo "✅ Access log ĐẠT: Trường IP client ghi nhận đúng IP socket thật, không bị giả mạo."

echo ""
echo "🎉 TẤT CẢ CÁC BƯỚC KIỂM THỬ VÀ TEST BẮT BUỘC TRÊN COMPOSE PRODUCTION ĐÃ THÀNH CÔNG RỰC RỠ!"
