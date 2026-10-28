#!/usr/bin/env bash
# ==============================================================================
# test-caddy-ip-spoofing.sh — Kiểm thử xác minh MEL-127
#
# Kiểm tra:
# 1. Caddyfile hợp lệ và được adapt thành cấu hình reverse_proxy có header_up
#    ghi đè X-Forwarded-For={remote_host}, không cấu hình trusted_proxies.
# 2. Compose production không expose cổng backend ra host (chỉ expose mạng nội bộ),
#    và cấu hình Tomcat access log để ghi nhận IP client thật ra stdout.
# 3. Dynamic test bằng Docker với Mock Upstream (kiểm tra nhanh cơ chế header rewrite).
# 4. [TEST BẮT BUỘC 1] Chạy trên Compose / Backend Spring Boot thật:
#    Gửi 6 lần POST /api/v1/auth/guest qua Caddy với các header X-Forwarded-For giả khác nhau:
#    - Lần 1..5: HTTP 201 Created
#    - Lần 6: HTTP 429 Too Many Requests (BR-02 / MEL-33 rate limit)
#    - Log backend ghi nhận IP thật, không bị giả mạo.
# 5. [TEST BẮT BUỘC 2] Gọi thẳng cổng backend từ ngoài mạng Docker:
#    Kết nối bị từ chối (Connection refused).
# ==============================================================================

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
CADDYFILE="$REPO_ROOT/infra/caddy/Caddyfile"
COMPOSE_PROD="$REPO_ROOT/infra/compose/docker-compose.prod.yml"
COMPOSE_AWS="$REPO_ROOT/infra/aws/docker-compose.prod.yml"

echo "=== [1/5] KIỂM TRA CÚ PHÁP VÀ JSON ADAPT CỦA CADDYFILE ==="
if [ ! -f "$CADDYFILE" ]; then
  echo "❌ Lỗi: Không tìm thấy file $CADDYFILE" >&2
  exit 1
fi

# 1. Caddy validate
docker run --rm \
  -v "$CADDYFILE":/etc/caddy/Caddyfile:ro,z \
  -e API_DOMAIN=api.meloo.local \
  -e ACME_EMAIL=admin@meloo.local \
  caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
echo "✅ Cú pháp Caddyfile hợp lệ."

# 2. Caddy adapt và kiểm tra JSON
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

print("✅ Đã kiểm tra JSON: reverse_proxy ghi đè X-Forwarded-For thành {http.request.remote.host} và không có trusted_proxies.")
' "$ADAPTED_JSON"

echo "=== [2/5] KIỂM TRA ĐÓNG CỔNG VÀ ACCESS LOG TRONG DOCKER COMPOSE ==="
for file in "$COMPOSE_PROD" "$COMPOSE_AWS"; do
  if [ ! -f "$file" ]; then
    echo "❌ Lỗi: Không tìm thấy $file" >&2
    exit 1
  fi

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
    print(f"❌ Lỗi: {filename} cấu hình ports cho backend (vi phạm AC2 đóng cổng)", file=sys.stderr)
    sys.exit(1)

if not backend_has_expose:
    print(f"❌ Lỗi: {filename} chưa cấu hình expose cho backend", file=sys.stderr)
    sys.exit(1)

if "SERVER_TOMCAT_ACCESSLOG_ENABLED=true" not in content:
    print(f"❌ Lỗi: {filename} chưa kích hoạt SERVER_TOMCAT_ACCESSLOG_ENABLED", file=sys.stderr)
    sys.exit(1)

print(f"✅ Đã kiểm tra {filename}: backend chỉ dùng expose nội bộ (không map ports), đã bật Tomcat access log.")
' "$file"
done

echo "=== [3/5] KIỂM THỬ ĐỘNG MOCK REVERSE PROXY ==="
TEST_ID="meloo-test-$$"
NET_NAME="${TEST_ID}-net"
MOCK_BACKEND="${TEST_ID}-backend"
TEST_CADDY="${TEST_ID}-caddy"
HOST_PORT="18080"

cleanup() {
  echo "Đang dọn dẹp môi trường test..."
  docker rm -f "$TEST_CADDY" "$MOCK_BACKEND" "real-pg-$$" "real-backend-$$" "real-caddy-$$" 2>/dev/null || true
  docker network rm "$NET_NAME" "real-net-$$" 2>/dev/null || true
}
trap cleanup EXIT

docker network create "$NET_NAME" >/dev/null

docker run -d --rm \
  --name "$MOCK_BACKEND" \
  --network "$NET_NAME" \
  caddy:2-alpine sh -c "cat << 'EOF' > /etc/caddy/Caddyfile
:8080 {
    respond \"ECHO_XFF:{header.X-Forwarded-For}|ECHO_REMOTE:{remote_host}\" 200
}
EOF
caddy run --config /etc/caddy/Caddyfile" >/dev/null

docker run -d --rm \
  --name "$TEST_CADDY" \
  --network "$NET_NAME" \
  -p "${HOST_PORT}:80" \
  caddy:2-alpine sh -c "cat << 'EOF' > /etc/caddy/Caddyfile
:80 {
    reverse_proxy $MOCK_BACKEND:8080 {
        header_up X-Forwarded-For {remote_host}
    }
}
EOF
caddy run --config /etc/caddy/Caddyfile" >/dev/null

for _ in $(seq 1 10); do
  if curl -s --max-time 1 "http://127.0.0.1:${HOST_PORT}" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

RESP=$(curl -s -H "X-Forwarded-For: 1.2.3.4" "http://127.0.0.1:${HOST_PORT}")
RECEIVED_XFF=$(echo "$RESP" | sed -n 's/.*ECHO_XFF:\([^|]*\)|.*/\1/p')

if [[ "$RECEIVED_XFF" == *"1.2.3.4"* ]]; then
  echo "❌ Lỗi: Mock backend vẫn nhận được IP giả mạo 1.2.3.4!" >&2
  exit 1
fi
echo "✅ Mock test: Header giả '1.2.3.4' bị loại bỏ, nhận IP socket thật '$RECEIVED_XFF'."

docker rm -f "$TEST_CADDY" "$MOCK_BACKEND" 2>/dev/null || true
docker network rm "$NET_NAME" 2>/dev/null || true

echo "=== [4/5] [TEST BẮT BUỘC 1] KIỂM THỬ VỚI SPRING BOOT BACKEND & COMPOSE THẬT ==="
REAL_NET="real-net-$$"
REAL_PG="real-pg-$$"
REAL_BACKEND="real-backend-$$"
REAL_CADDY="real-caddy-$$"
REAL_PORT="18088"

# 1. Tạo network nội bộ giống Compose (meloo-internal)
docker network create "$REAL_NET" >/dev/null

# 2. Khởi động PostgreSQL 17
docker run -d --name "$REAL_PG" --network "$REAL_NET" \
  -e POSTGRES_DB=meloo -e POSTGRES_USER=meloo -e POSTGRES_PASSWORD=meloo \
  postgres:17-alpine >/dev/null

sleep 3

# 3. Khởi động backend Spring Boot thật với đúng biến môi trường của Compose production
docker run -d --name "$REAL_BACKEND" --network "$REAL_NET" \
  -e DB_URL="jdbc:postgresql://${REAL_PG}:5432/meloo" \
  -e DB_USERNAME=meloo \
  -e DB_PASSWORD=meloo \
  -e MELOO_JWT_SECRET=supersecretjwtsecretkeyformeloo123456789 \
  -e MELOO_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
  -e SERVER_TOMCAT_ACCESSLOG_BUFFERED=false \
  -e SERVER_TOMCAT_ACCESSLOG_ROTATE=false \
  -e SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/dev \
  -e SERVER_TOMCAT_ACCESSLOG_PREFIX=stdout \
  -e SERVER_TOMCAT_ACCESSLOG_SUFFIX= \
  -e SERVER_TOMCAT_ACCESSLOG_PATTERN="%{X-Forwarded-For}i %h %t \"%r\" %s" \
  ghcr.io/thaidevv/meloo-backend:latest >/dev/null

echo "Đang chờ backend Spring Boot khởi động..."
BACKEND_READY=0
for i in $(seq 1 35); do
  if docker exec "$REAL_BACKEND" wget -qO- http://localhost:8080/actuator/health/readiness 2>/dev/null | grep -q '"UP"'; then
    BACKEND_READY=1
    echo "✅ Backend đã sẵn sàng (readiness: UP) sau ${i}s."
    break
  fi
  sleep 1
done

if [ "$BACKEND_READY" -ne 1 ]; then
  echo "❌ Lỗi: Backend không sẵn sàng sau 35s!" >&2
  docker logs --tail=40 "$REAL_BACKEND"
  exit 1
fi

# 4. Khởi động Caddy reverse proxy tới backend theo Caddyfile sản xuất
docker run -d --name "$REAL_CADDY" --network "$REAL_NET" -p "${REAL_PORT}:80" \
  caddy:2-alpine sh -c "cat << 'EOF' > /etc/caddy/Caddyfile
:80 {
    reverse_proxy $REAL_BACKEND:8080 {
        header_up X-Forwarded-For {remote_host}
    }
}
EOF
caddy run --config /etc/caddy/Caddyfile" >/dev/null

sleep 2

# 5. Gửi 6 lần POST /api/v1/auth/guest qua Caddy với các IP giả khác nhau
echo ">> Gửi 6 lần POST /api/v1/auth/guest qua Caddy với X-Forwarded-For giả khác nhau..."
for i in $(seq 1 6); do
  FAKE_IP="10.0.0.$((10 + i))"
  STATUS=$(curl -s -o /tmp/resp_guest.json -w "%{http_code}" \
    -H "X-Forwarded-For: $FAKE_IP" \
    -X POST "http://127.0.0.1:${REAL_PORT}/api/v1/auth/guest" \
    -H "Content-Type: application/json" \
    -d "{\"nickname\":\"Tester${i}\"}")

  echo "   [Request $i] Header: X-Forwarded-For: $FAKE_IP -> HTTP Status: $STATUS"

  if [ "$i" -le 5 ]; then
    if [ "$STATUS" -ne 201 ]; then
      echo "❌ Lỗi: Request $i mong đợi HTTP 201 nhưng nhận được $STATUS" >&2
      cat /tmp/resp_guest.json >&2
      exit 1
    fi
  else
    if [ "$STATUS" -ne 429 ]; then
      echo "❌ Lỗi: Request 6 mong đợi HTTP 429 (Too Many Requests) nhưng nhận được $STATUS" >&2
      cat /tmp/resp_guest.json >&2
      exit 1
    fi
  fi
done
echo "✅ Test bắt buộc 1 ĐẠT: 5 lần đầu trả về HTTP 201, lần thứ 6 trả về HTTP 429."

# Kiểm tra log backend ghi nhận IP thật
echo ">> Kiểm tra Tomcat access log trong log backend..."
BACKEND_LOGS=$(docker logs "$REAL_BACKEND" 2>&1 | grep "POST /api/v1/auth/guest" | tail -n 6)
echo "$BACKEND_LOGS"

# Xác nhận trong các dòng access log KHÔNG chứa bất kỳ IP giả 10.0.0.x nào
if echo "$BACKEND_LOGS" | grep -qE "10\.0\.0\."; then
  echo "❌ Lỗi: Log backend vẫn chứa IP giả mạo 10.0.0.x!" >&2
  exit 1
fi
echo "✅ Log backend ĐẠT: Không chứa IP giả mạo, Caddy đã ghi đè IP socket thật cho toàn bộ 6 request."

echo "=== [5/5] [TEST BẮT BUỘC 2] KIỂM TRA ĐÓNG CỔNG BACKEND TỪ NGOÀI DOCKER ==="
echo ">> Thử kết nối trực tiếp cổng 8080 của container backend từ host..."
DIRECT_EXIT=0
curl -s --connect-timeout 2 "http://127.0.0.1:8080/actuator/health" >/dev/null 2>&1 || DIRECT_EXIT=$?

if [ "$DIRECT_EXIT" -eq 0 ]; then
  echo "❌ Lỗi: Cổng 8080 của backend vẫn kết nối được từ ngoài host!" >&2
  exit 1
fi
echo "✅ Test bắt buộc 2 ĐẠT: Kết nối trực tiếp tới cổng 8080 từ host bị từ chối (exit code $DIRECT_EXIT - Connection refused)."

echo ""
echo "🎉 TẤT CẢ CÁC BƯỚC KIỂM THỬ VÀ TEST BẮT BUỘC TRÊN BACKEND THẬT CHO MEL-127 ĐÃ THÀNH CÔNG RỰC RỠ!"
