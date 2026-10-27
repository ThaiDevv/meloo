#!/usr/bin/env bash
# ==============================================================================
# setup-budget.sh — Script tạo AWS Cost Budget cảnh báo khi chi phí vượt 1 USD
#
# Cách dùng:
#   ./setup-budget.sh [EMAIL_NHAN_CANH_BAO]
#
# Ví dụ:
#   ./setup-budget.sh thai@example.com
# ==============================================================================

set -euo pipefail

EMAIL="${1:-}"

if [[ -z "$EMAIL" ]]; then
  echo "Sử dụng: $0 <email_nhận_cảnh_báo>"
  echo "Ví dụ: $0 thai@example.com"
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text 2>/dev/null || true)
if [[ -z "$ACCOUNT_ID" ]]; then
  echo "Lỗi: Không lấy được AWS Account ID. Hãy chắc chắn đã chạy 'aws configure' hợp lệ." >&2
  exit 1
fi

echo "Đang cấu hình AWS Budget cho Account ID: $ACCOUNT_ID..."
echo "Email nhận cảnh báo: $EMAIL"

BUDGET_JSON=$(cat <<EOF
{
  "BudgetName": "Meloo-Monthly-Cost-Budget-1USD",
  "BudgetLimit": {
    "Amount": "1.0",
    "Unit": "USD"
  },
  "CostTypes": {
    "IncludeTax": true,
    "IncludeSubscription": true,
    "UseBlended": false,
    "IncludeRefund": false,
    "IncludeCredit": false,
    "IncludeUpfront": true,
    "IncludeRecurring": true,
    "IncludeOtherSubscription": true,
    "IncludeSupport": true,
    "IncludeDiscount": true,
    "UseAmortized": false
  },
  "TimeUnit": "MONTHLY",
  "BudgetType": "COST"
}
EOF
)

NOTIFICATIONS_JSON=$(cat <<EOF
[
  {
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 80,
      "ThresholdType": "PERCENTAGE",
      "NotificationState": "ALARM"
    },
    "Subscribers": [
      {
        "SubscriptionType": "EMAIL",
        "Address": "$EMAIL"
      }
    ]
  },
  {
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 100,
      "ThresholdType": "PERCENTAGE",
      "NotificationState": "ALARM"
    },
    "Subscribers": [
      {
        "SubscriptionType": "EMAIL",
        "Address": "$EMAIL"
      }
    ]
  },
  {
    "Notification": {
      "NotificationType": "FORECASTED",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 100,
      "ThresholdType": "PERCENTAGE",
      "NotificationState": "ALARM"
    },
    "Subscribers": [
      {
        "SubscriptionType": "EMAIL",
        "Address": "$EMAIL"
      }
    ]
  }
]
EOF
)

BUDGET_EXISTS=false
if aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name "Meloo-Monthly-Cost-Budget-1USD" &>/dev/null; then
  BUDGET_EXISTS=true
  echo "Budget 'Meloo-Monthly-Cost-Budget-1USD' đã tồn tại. Đang cập nhật..."
  aws budgets delete-budget --account-id "$ACCOUNT_ID" --budget-name "Meloo-Monthly-Cost-Budget-1USD"
fi

echo "Đang áp dụng Budget 'Meloo-Monthly-Cost-Budget-1USD' kèm thông báo..."
aws budgets create-budget \
  --account-id "$ACCOUNT_ID" \
  --budget "$BUDGET_JSON" \
  --notifications-with-subscribers "$NOTIFICATIONS_JSON"

echo "=========================================================================="
if [[ "$BUDGET_EXISTS" = true ]]; then
  echo "Thành công: Đã cập nhật AWS Budget 1.00 USD!"
else
  echo "Thành công: Đã tạo mới AWS Budget 1.00 USD!"
fi
echo "Cảnh báo sẽ được gửi tới: $EMAIL"
echo "- Khi chi phí thực tế vượt 80% (\$0.80 USD)"
echo "- Khi chi phí thực tế vượt 100% (\$1.00 USD)"
echo "- Khi dự báo chi phí tháng (Forecasted) vượt 100% (\$1.00 USD)"
echo "=========================================================================="
