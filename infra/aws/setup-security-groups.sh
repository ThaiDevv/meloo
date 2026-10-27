#!/usr/bin/env bash
# ==============================================================================
# setup-security-groups.sh — Tạo Security Groups cho EC2 và RDS trên AWS
#
# Yêu cầu AC:
# - Region: ap-southeast-1 (Singapore)
# - meloo-ec2-sg: Chỉ mở 80, 443 (HTTP/HTTPS public); SSH 22 CHỈ từ IP hiện tại của bạn.
# - meloo-rds-sg: Chỉ nhận kết nối cổng 5432 từ meloo-ec2-sg, KHÔNG mở ra ngoài internet.
#
# Cách dùng:
#   ./setup-security-groups.sh [VPC_ID] [MY_IP]
#
# Ví dụ:
#   ./setup-security-groups.sh
#   ./setup-security-groups.sh vpc-0123456789abcdef0 14.162.x.x
# ==============================================================================

set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-ap-southeast-1}"

echo "Đang kiểm tra kết nối AWS CLI (Region: $REGION)..."
if ! aws sts get-caller-identity &>/dev/null; then
  echo "Lỗi: AWS CLI chưa cấu hình hoặc không có quyền gọi STS. Hãy chạy 'aws configure'." >&2
  exit 1
fi

# 1. Xác định VPC
VPC_ID="${1:-}"
if [[ -z "$VPC_ID" ]]; then
  echo "Không truyền VPC_ID, tự động tìm default VPC trong region $REGION..."
  VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters "Name=isDefault,Values=true" --query "Vpcs[0].VpcId" --output text)
  if [[ -z "$VPC_ID" || "$VPC_ID" == "None" ]]; then
    echo "Lỗi: Không tìm thấy default VPC. Hãy chỉ định VPC_ID rõ ràng: $0 <VPC_ID>" >&2
    exit 1
  fi
fi
echo "Sử dụng VPC: $VPC_ID"

# 2. Xác định My IP cho SSH
MY_IP="${2:-}"
if [[ -z "$MY_IP" ]]; then
  echo "Đang tự động phát hiện IP công cộng của bạn..."
  MY_IP=$(curl -s --max-time 5 https://checkip.amazonaws.com || curl -s --max-time 5 https://ifconfig.me)
  if [[ -z "$MY_IP" ]]; then
    echo "Lỗi: Không tự động lấy được IP của bạn. Hãy truyền tham số: $0 <VPC_ID> <MY_IP>" >&2
    exit 1
  fi
fi
SSH_CIDR="${MY_IP}/32"
echo "IP cho phép truy cập SSH (Port 22): $SSH_CIDR"

# ------------------------------------------------------------------------------
# 3. Tạo hoặc lấy meloo-ec2-sg
# ------------------------------------------------------------------------------
EC2_SG_NAME="meloo-ec2-sg"
EC2_SG_ID=$(aws ec2 describe-security-groups --region "$REGION" --filters "Name=group-name,Values=$EC2_SG_NAME" "Name=vpc-id,Values=$VPC_ID" --query "SecurityGroups[0].GroupId" --output text 2>/dev/null || true)

if [[ -z "$EC2_SG_ID" || "$EC2_SG_ID" == "None" ]]; then
  echo "Đang tạo Security Group: $EC2_SG_NAME..."
  EC2_SG_ID=$(aws ec2 create-security-group \
    --region "$REGION" \
    --group-name "$EC2_SG_NAME" \
    --description "Security Group cho Meloo EC2 backend & Caddy" \
    --vpc-id "$VPC_ID" \
    --query "GroupId" --output text)
  echo "Đã tạo $EC2_SG_NAME: $EC2_SG_ID"
else
  echo "Security Group $EC2_SG_NAME đã tồn tại: $EC2_SG_ID"
fi

# Gắn tag
aws ec2 create-tags --region "$REGION" --resources "$EC2_SG_ID" --tags "Key=Project,Value=Meloo" "Key=Name,Value=$EC2_SG_NAME"

# Inbound rules cho EC2: 80, 443 từ 0.0.0.0/0, 22 từ MY_IP
echo "Cấu hình Inbound Rules cho $EC2_SG_NAME..."
# Port 80 HTTP
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$EC2_SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0 2>/dev/null || true
# Port 443 HTTPS
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$EC2_SG_ID" --protocol tcp --port 443 --cidr 0.0.0.0/0 2>/dev/null || true
# Port 22 SSH (chỉ từ IP của bạn)
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$EC2_SG_ID" --protocol tcp --port 22 --cidr "$SSH_CIDR" 2>/dev/null || true

# ------------------------------------------------------------------------------
# 4. Tạo hoặc lấy meloo-rds-sg
# ------------------------------------------------------------------------------
RDS_SG_NAME="meloo-rds-sg"
RDS_SG_ID=$(aws ec2 describe-security-groups --region "$REGION" --filters "Name=group-name,Values=$RDS_SG_NAME" "Name=vpc-id,Values=$VPC_ID" --query "SecurityGroups[0].GroupId" --output text 2>/dev/null || true)

if [[ -z "$RDS_SG_ID" || "$RDS_SG_ID" == "None" ]]; then
  echo "Đang tạo Security Group: $RDS_SG_NAME..."
  RDS_SG_ID=$(aws ec2 create-security-group \
    --region "$REGION" \
    --group-name "$RDS_SG_NAME" \
    --description "Security Group cho Meloo RDS PostgreSQL (private)" \
    --vpc-id "$VPC_ID" \
    --query "GroupId" --output text)
  echo "Đã tạo $RDS_SG_NAME: $RDS_SG_ID"
else
  echo "Security Group $RDS_SG_NAME đã tồn tại: $RDS_SG_ID"
fi

# Gắn tag
aws ec2 create-tags --region "$REGION" --resources "$RDS_SG_ID" --tags "Key=Project,Value=Meloo" "Key=Name,Value=$RDS_SG_NAME"

# Inbound rules cho RDS: CHỈ cho phép Port 5432 từ EC2 Security Group
echo "Cấu hình Inbound Rules cho $RDS_SG_NAME (chỉ cho phép traffic từ $EC2_SG_NAME)..."
aws ec2 authorize-security-group-ingress \
  --region "$REGION" \
  --group-id "$RDS_SG_ID" \
  --protocol tcp \
  --port 5432 \
  --source-group "$EC2_SG_ID" 2>/dev/null || true

echo "=========================================================================="
echo "Hoàn tất thiết lập Security Groups:"
echo "- EC2 Security Group ID: $EC2_SG_ID ($EC2_SG_NAME)"
echo "  + Inbound 80, 443: 0.0.0.0/0"
echo "  + Inbound 22 (SSH): $SSH_CIDR"
echo "- RDS Security Group ID: $RDS_SG_ID ($RDS_SG_NAME)"
echo "  + Inbound 5432 (Postgres): CHỈ từ $EC2_SG_ID"
echo "  + Public access: KHÔNG MỞ"
echo "=========================================================================="
