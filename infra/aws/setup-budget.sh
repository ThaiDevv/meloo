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

BUDGET_NAME="Meloo-Monthly-Cost-Budget-1USD"
echo "Đang cấu hình AWS Budget cho Account ID: $ACCOUNT_ID (Budget: $BUDGET_NAME)..."
echo "Email nhận cảnh báo: $EMAIL"

BUDGET_JSON=$(cat <<EOF
{
  "BudgetName": "$BUDGET_NAME",
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

create_or_update_notifications() {
  local notif_array=('{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80,"ThresholdType":"PERCENTAGE"}'
                     '{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":100,"ThresholdType":"PERCENTAGE"}'
                     '{"NotificationType":"FORECASTED","ComparisonOperator":"GREATER_THAN","Threshold":100,"ThresholdType":"PERCENTAGE"}')
  local sub_json="[{\"SubscriptionType\":\"EMAIL\",\"Address\":\"$EMAIL\"}]"

  # Xóa các notification cũ nếu có để tránh trùng lặp
  local existing_notifs
  existing_notifs=$(aws budgets describe-notifications-for-budget \
    --account-id "$ACCOUNT_ID" \
    --budget-name "$BUDGET_NAME" \
    --query "Notifications" \
    --output json 2>/dev/null || echo "[]")

  # Thu hồi notification cũ an toàn
  for notif in "${notif_array[@]}"; do
    aws budgets delete-notification \
      --account-id "$ACCOUNT_ID" \
      --budget-name "$BUDGET_NAME" \
      --notification "$notif" 2>/dev/null || true
  done

  # Thêm các notification mới
  for notif in "${notif_array[@]}"; do
    aws budgets create-notification \
      --account-id "$ACCOUNT_ID" \
      --budget-name "$BUDGET_NAME" \
      --notification "$notif" \
      --subscribers "$sub_json"
  done
}

if aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name "$BUDGET_NAME" &>/dev/null; then
  echo "Budget '$BUDGET_NAME' đã tồn tại. Đang cập nhật định mức và thông báo (không xóa budget)..."
  aws budgets modify-budget \
    --account-id "$ACCOUNT_ID" \
    --new-budget "$BUDGET_JSON"
  create_or_update_notifications
  echo "=========================================================================="
  echo "Thành công: Đã cập nhật AWS Budget 1.00 USD!"
else
  echo "Đang tạo mới Budget '$BUDGET_NAME' kèm thông báo..."
  aws budgets create-budget \
    --account-id "$ACCOUNT_ID" \
    --budget "$BUDGET_JSON" \
    --notifications-with-subscribers "$NOTIFICATIONS_JSON"
  echo "=========================================================================="
  echo "Thành công: Đã tạo mới AWS Budget 1.00 USD!"
fi

echo "Cảnh báo sẽ được gửi tới: $EMAIL"
echo "- Khi chi phí thực tế vượt 80% (\$0.80 USD)"
echo "- Khi chi phí thực tế vượt 100% (\$1.00 USD)"
echo "- Khi dự báo chi phí tháng (Forecasted) vượt 100% (\$1.00 USD)"
echo "=========================================================================="
