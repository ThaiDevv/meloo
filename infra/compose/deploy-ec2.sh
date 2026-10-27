#!/usr/bin/env bash
# deploy-ec2.sh — Kịch bản triển khai backend và tự động rollback trên máy chủ EC2
set -euo pipefail

DEPLOY_IMAGE="${1:?Lỗi: Cần cung cấp DEPLOY_IMAGE làm tham số thứ nhất}"
cd /opt/meloo

if [ ! -f .env ]; then
  echo "::error::Không tìm thấy file /opt/meloo/.env trên EC2! Vui lòng chuẩn bị theo hướng dẫn INF-01." >&2
  exit 1
fi

# Ghi nhận tag image hiện tại phục vụ rollback an toàn
PREV_IMAGE=$(grep -E '^BACKEND_IMAGE=' .env | cut -d= -f2- || true)
if [ -z "$PREV_IMAGE" ]; then
  PREV_IMAGE="ghcr.io/thaidevv/meloo-backend:latest"
fi

echo "=== BẮT ĐẦU DEPLOY BACKEND ==="
echo "Image hiện tại trước deploy: $PREV_IMAGE"
echo "Image mục tiêu triển khai: $DEPLOY_IMAGE"

# Cập nhật BACKEND_IMAGE trong .env
if grep -q '^BACKEND_IMAGE=' .env; then
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${DEPLOY_IMAGE}|" .env
else
  echo "BACKEND_IMAGE=${DEPLOY_IMAGE}" >> .env
fi

# Kéo image mới, tự hoàn tác .env nếu lỗi kéo image
echo "Đang kéo image mới: $DEPLOY_IMAGE..."
if ! docker compose pull backend; then
  echo "❌ docker compose pull thất bại! Đang hoàn tác .env về $PREV_IMAGE..." >&2
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${PREV_IMAGE}|" .env
  exit 1
fi

# Khởi động lại service backend
echo "Khởi động lại backend container..."
if ! docker compose up -d backend; then
  echo "❌ docker compose up thất bại! Đang rollback về $PREV_IMAGE..." >&2
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${PREV_IMAGE}|" .env
  docker compose pull backend || true
  docker compose up -d backend
  exit 1
fi

# Chờ và kiểm tra trạng thái sức khỏe (/actuator/health)
# Chạy tối đa 30 lần x 5s = 150s cho JVM khởi động lạnh trên EC2 1 GB RAM
echo "Đang kiểm tra /actuator/health trên EC2 (tối đa 150s)..."
HEALTHY=0
for i in $(seq 1 30); do
  sleep 5
  echo "Lần thử $i/30..."
  
  # Kiểm tra trạng thái health của Docker container (CMD-SHELL wget từ Dockerfile)
  CONTAINER_STATUS=$(docker inspect --format '{{.State.Health.Status}}' meloo-backend 2>/dev/null || true)
  if [ "$CONTAINER_STATUS" = "healthy" ]; then
    echo "✅ Container meloo-backend đạt trạng thái healthy!"
    HEALTHY=1
    break
  fi

  # Kiểm tra trực tiếp qua HTTP endpoint /actuator/health (đóng kín stdin </dev/null)
  HTTP_STATUS=$(docker compose exec -T backend wget -qO- http://localhost:8080/actuator/health </dev/null 2>/dev/null || true)
  if echo "$HTTP_STATUS" | grep -q '"UP"'; then
    echo "✅ Backend đã sẵn sàng (status UP): $HTTP_STATUS"
    HEALTHY=1
    break
  fi
done

if [ "$HEALTHY" -ne 1 ]; then
  echo "❌ Backend health check thất bại sau 150s! Đang rollback về $PREV_IMAGE..." >&2
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${PREV_IMAGE}|" .env
  docker compose pull backend || true
  docker compose up -d backend
  echo "❌ Đã rollback về $PREV_IMAGE. Báo lỗi workflow." >&2
  exit 1
fi

# Đảm bảo các service liên quan (như Caddy) hoạt động đồng bộ
docker compose up -d --remove-orphans
echo "✅ Triển khai thành công: $DEPLOY_IMAGE đang hoạt động ổn định!"
