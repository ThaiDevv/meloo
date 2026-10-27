#!/usr/bin/env bash
# ==============================================================================
# setup-ec2.sh — Script bootstrap máy chủ EC2 (Ubuntu 24.04 / 22.04 LTS) cho Meloo
#
# Chức năng:
# 1. Tạo và kích hoạt Swap 2 GB (bắt buộc cho EC2 1 GB RAM để tránh OOM).
# 2. Cài đặt Docker Engine và Docker Compose plugin chính thức.
# 3. Giới hạn dung lượng Docker log tránh tràn ổ đĩa (max-size 10m, max-file 3).
# 4. Khởi tạo thư mục /opt/meloo và phân quyền cho người dùng deploy.
#
# Cách dùng:
#   chmod +x setup-ec2.sh
#   sudo ./setup-ec2.sh
# ==============================================================================

set -euo pipefail

log() {
  echo -e "\033[1;32m[MELOO-EC2]\033[0m $1"
}

warn() {
  echo -e "\033[1;33m[MELOO-EC2:WARN]\033[0m $1"
}

# 1. Kiểm tra quyền root
if [[ $EUID -ne 0 ]]; then
  echo "Lỗi: Script này cần quyền root. Hãy chạy: sudo $0" >&2
  exit 1
fi

DEPLOY_USER="${SUDO_USER:-ubuntu}"
log "Bắt đầu thiết lập EC2 instance cho Meloo (Deploy user: ${DEPLOY_USER})..."

# ------------------------------------------------------------------------------
# 2. Cấu hình Swap 2 GB
# ------------------------------------------------------------------------------
log "Kiểm tra Swap..."
if swapon --show | grep -q "/swapfile"; then
  log "Swap /swapfile đã tồn tại và đang hoạt động. Bỏ qua tạo mới."
else
  log "Đang tạo Swap file 2 GB tại /swapfile..."
  if ! fallocate -l 2G /swapfile 2>/dev/null; then
    dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
  fi
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile

  if ! grep -q "/swapfile" /etc/fstab; then
    echo "/swapfile none swap sw 0 0" >> /etc/fstab
    log "Đã thêm /swapfile vào /etc/fstab."
  fi
fi

# Tối ưu swappiness (10 phù hợp cho VPS ít RAM để ưu tiên RAM thực trước khi swap)
sysctl vm.swappiness=10
cat <<EOF > /etc/sysctl.d/99-meloo-swap.conf
vm.swappiness=10
vm.vfs_cache_pressure=50
EOF
log "Cấu hình Swap hoàn tất:"
free -h

# ------------------------------------------------------------------------------
# 3. Cập nhật hệ thống và cài đặt Docker CE + Compose plugin
# ------------------------------------------------------------------------------
log "Cập nhật apt package index và cài dependencies..."
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release ufw netcat-openbsd

log "Cài đặt Docker CE chính thức..."
install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.asc ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi

UBUNTU_CODENAME=$(lsb_release -cs 2>/dev/null || echo "jammy")
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  ${UBUNTU_CODENAME} stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Phân quyền cho deploy user dùng Docker không cần sudo
if id "${DEPLOY_USER}" &>/dev/null; then
  usermod -aG docker "${DEPLOY_USER}"
  log "Đã thêm user ${DEPLOY_USER} vào nhóm docker."
fi

# ------------------------------------------------------------------------------
# 4. Giới hạn dung lượng log của Docker daemon (tránh đầy ổ đĩa gp3 20-30GB)
# ------------------------------------------------------------------------------
log "Cấu hình log rotation cho Docker daemon..."
mkdir -p /etc/docker
cat <<EOF > /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

systemctl daemon-reload
systemctl restart docker
systemctl enable docker

# ------------------------------------------------------------------------------
# 5. Khởi tạo thư mục ứng dụng /opt/meloo
# ------------------------------------------------------------------------------
log "Tạo thư mục ứng dụng /opt/meloo..."
mkdir -p /opt/meloo
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" /opt/meloo
chmod 755 /opt/meloo

log "=========================================================================="
log "Thiết lập EC2 hoàn tất thành công!"
log "- Swap: 2 GB kích hoạt (swappiness=10)"
log "- Docker: $(docker --version)"
log "- Docker Compose: $(docker compose version)"
log "- Thư mục ứng dụng: /opt/meloo (chủ sở hữu: ${DEPLOY_USER})"
log "=========================================================================="
