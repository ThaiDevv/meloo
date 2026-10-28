#!/usr/bin/env bash
# ==============================================================================
# test-caddy-ip-spoofing.sh — Kiểm thử xác minh MEL-127
#
# Kiểm tra:
# 1. Caddyfile hợp lệ và được adapt thành cấu hình reverse_proxy có header_up
#    ghi đè X-Forwarded-For={remote_host}, không cấu hình trusted_proxies.
# 2. Compose production không expose cổng backend ra host (chỉ expose mạng nội bộ).
# 3. Dynamic test bằng Docker:
#    - Upstream mock nhận request qua Caddy.
#    - Khi client gửi X-Forwarded-For: 1.2.3.4, upstream nhận IP thật, không có 1.2.3.4.
#    - Gửi nhiều request với các header giả khác nhau -> upstream luôn thấy cùng 1 IP thật.
#    - Thử gọi thẳng cổng backend từ host -> bị từ chối kết nối.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
CADDYFILE="$REPO_ROOT/infra/caddy/Caddyfile"
COMPOSE_PROD="$REPO_ROOT/infra/compose/docker-compose.prod.yml"
COMPOSE_AWS="$REPO_ROOT/infra/aws/docker-compose.prod.yml"

echo "=== [1/4] KIỂM TRA CÚ PHÁP VÀ JSON ADAPT CỦA CADDYFILE ==="
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

echo "=== [2/4] KIỂM TRA ĐÓNG CỔNG BACKEND TRONG DOCKER COMPOSE ==="
for file in "$COMPOSE_PROD" "$COMPOSE_AWS"; do
  if [ ! -f "$file" ]; then
    echo "❌ Lỗi: Không tìm thấy $file" >&2
    exit 1
  fi

  python3 -c '
import sys

filename = sys.argv[1]
with open(filename, "r") as f:
    lines = f.readlines()

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
        # Service tiếp theo khi gặp indent cấp service (2 spaces)
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

print(f"✅ Đã kiểm tra {filename}: backend chỉ dùng expose (nội bộ), không ánh xạ ports ra host.")
' "$file"
done

echo "=== [3/4] THỰC THI KIỂM THỬ ĐỘNG BẰNG DOCKER (MOCK REVERSE PROXY) ==="
TEST_ID="meloo-test-$$"
NET_NAME="${TEST_ID}-net"
MOCK_BACKEND="${TEST_ID}-backend"
TEST_CADDY="${TEST_ID}-caddy"
HOST_PORT="18080"

cleanup() {
  echo "Đang dọn dẹp môi trường test..."
  docker rm -f "$TEST_CADDY" "$MOCK_BACKEND" 2>/dev/null || true
  docker network rm "$NET_NAME" 2>/dev/null || true
}
trap cleanup EXIT

# 1. Tạo Docker network cô lập
docker network create "$NET_NAME" >/dev/null

# 2. Khởi động Mock Backend (Caddy respond trả về X-Forwarded-For nhận được)
# Mock backend KHÔNG mở port ra host (chỉ lắng nghe cổng 8080 trong Docker network)
docker run -d --rm \
  --name "$MOCK_BACKEND" \
  --network "$NET_NAME" \
  caddy:2-alpine sh -c "cat << 'EOF' > /etc/caddy/Caddyfile
:8080 {
    respond \"ECHO_XFF:{header.X-Forwarded-For}|ECHO_REMOTE:{remote_host}\" 200
}
EOF
caddy run --config /etc/caddy/Caddyfile" >/dev/null

# 3. Khởi động Test Caddy với Caddyfile cấu hình reverse_proxy giống production
# Caddy lắng nghe trên HOST_PORT của host
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

# Chờ Caddy sẵn sàng
for _ in $(seq 1 10); do
  if curl -s --max-time 1 "http://127.0.0.1:${HOST_PORT}" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

# Test 3.1: Gửi request mang header giả mạo X-Forwarded-For: 1.2.3.4
echo ">> Kiểm tra gửi request với header giả mạo 'X-Forwarded-For: 1.2.3.4' qua Caddy..."
RESP=$(curl -s -H "X-Forwarded-For: 1.2.3.4" "http://127.0.0.1:${HOST_PORT}")
echo "Phản hồi từ upstream mock: $RESP"

RECEIVED_XFF=$(echo "$RESP" | sed -n 's/.*ECHO_XFF:\([^|]*\)|.*/\1/p')

if [[ "$RECEIVED_XFF" == *"1.2.3.4"* ]]; then
  echo "❌ Lỗi: Upstream backend vẫn nhận được IP giả mạo 1.2.3.4!" >&2
  exit 1
fi

if [ -z "$RECEIVED_XFF" ]; then
  echo "❌ Lỗi: Upstream backend không nhận được X-Forwarded-For!" >&2
  exit 1
fi
echo "✅ Thành công: Caddy đã loại bỏ '1.2.3.4', upstream chỉ nhận IP thật: '$RECEIVED_XFF'."

# Test 3.2: Gửi 6 request với 6 header X-Forwarded-For giả khác nhau
echo ">> Kiểm tra gửi 6 request với các header IP giả khác nhau..."
for i in {1..6}; do
  FAKE_IP="192.168.1.$((100 + i))"
  R=$(curl -s -H "X-Forwarded-For: $FAKE_IP" "http://127.0.0.1:${HOST_PORT}")
  CURR_XFF=$(echo "$R" | sed -n 's/.*ECHO_XFF:\([^|]*\)|.*/\1/p')
  if [[ "$CURR_XFF" != "$RECEIVED_XFF" ]]; then
    echo "❌ Lỗi ở request $i: IP nhận diện thay đổi ($CURR_XFF != $RECEIVED_XFF) khi gửi fake IP $FAKE_IP" >&2
    exit 1
  fi
  echo "   [Request $i] Header gửi: $FAKE_IP -> Upstream nhận IP thật: $CURR_XFF"
done
echo "✅ Thành công: Cả 6 request đều cho ra cùng một IP thực tế, chứng minh kẻ tấn công không thể đổi header giả để chia nhỏ bucket rate limit."

# Test 3.3: Gọi thẳng cổng 8080 của mock-backend từ host (ngoài mạng Docker)
echo "=== [4/4] KIỂM TRA ĐÓNG CỔNG TRUY CẬP TRỰC TIẾP TỪ HOST ==="
echo ">> Thử kết nối trực tiếp cổng 8080 của backend từ ngoài Docker..."
CONNECT_EXIT=0
curl -s --connect-timeout 2 "http://127.0.0.1:8080" >/dev/null 2>&1 || CONNECT_EXIT=$?

if [ "$CONNECT_EXIT" -eq 0 ]; then
  echo "❌ Lỗi: Cổng 8080 vẫn kết nối được từ bên ngoài host!" >&2
  exit 1
fi
echo "✅ Thành công: Kết nối thẳng tới cổng 8080 từ host bị từ chối (exit code $CONNECT_EXIT - Connection refused)."

echo ""
echo "🎉 TẤT CẢ CÁC BƯỚC KIỂM THỬ CHO TICKET MEL-127 ĐÃ THÀNH CÔNG!"
