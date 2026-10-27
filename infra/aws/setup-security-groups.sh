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

# Hàm cấp quyền Ingress an toàn, chỉ bỏ qua lỗi InvalidPermission.Duplicate
authorize_ingress() {
  local sg_id="$1"
  shift
  local err_file
  err_file=$(mktemp)
  if ! aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$sg_id" "$@" 2>"$err_file"; then
    if grep -q "InvalidPermission.Duplicate" "$err_file"; then
      rm -f "$err_file"
      return 0
    else
      echo "Lỗi khi cấu hình ingress rule cho $sg_id:" >&2
      cat "$err_file" >&2
      rm -f "$err_file"
      return 1
    fi
  fi
  rm -f "$err_file"
  return 0
}

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

# Inbound rules cho EC2: 80, 443 từ 0.0.0.0/0
echo "Cấu hình Inbound Rules cho $EC2_SG_NAME..."
authorize_ingress "$EC2_SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0
authorize_ingress "$EC2_SG_ID" --protocol tcp --port 443 --cidr 0.0.0.0/0

# Thu hồi các rule SSH cũ không khớp IP hiện tại (đảm bảo AC2: chỉ từ IP của bạn)
echo "Kiểm tra và dọn dẹp các rule SSH cũ..."
OLD_SSH_CIDRS=$(aws ec2 describe-security-group-rules \
  --region "$REGION" \
  --filters "Name=group-id,Values=$EC2_SG_ID" \
  --query "SecurityGroupRules[?FromPort==\`22\` && ToPort==\`22\` && !IsEgress].CidrIpv4" \
  --output text 2>/dev/null || true)

for cidr in $OLD_SSH_CIDRS; do
  if [[ "$cidr" != "$SSH_CIDR" && "$cidr" != "None" ]]; then
    echo "Thu hồi SSH rule cũ không khớp: $cidr..."
    aws ec2 revoke-security-group-ingress \
      --region "$REGION" \
      --group-id "$EC2_SG_ID" \
      --protocol tcp \
      --port 22 \
      --cidr "$cidr" 2>/dev/null || true
  fi
done

# Thêm rule SSH từ IP hiện tại
authorize_ingress "$EC2_SG_ID" --protocol tcp --port 22 --cidr "$SSH_CIDR"

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
authorize_ingress "$RDS_SG_ID" --protocol tcp --port 5432 --source-group "$EC2_SG_ID"

echo "=========================================================================="
echo "Hoàn tất thiết lập Security Groups:"
echo "- EC2 Security Group ID: $EC2_SG_ID ($EC2_SG_NAME)"
echo "  + Inbound 80, 443 (TCP): 0.0.0.0/0"
echo "  + Inbound 22 (SSH TCP): $SSH_CIDR (đã dọn các rule SSH cũ)"
echo "- RDS Security Group ID: $RDS_SG_ID ($RDS_SG_NAME)"
echo "  + Inbound 5432 (Postgres): CHỈ từ $EC2_SG_ID"
echo "  + Public access: KHÔNG MỞ"
echo "=========================================================================="
