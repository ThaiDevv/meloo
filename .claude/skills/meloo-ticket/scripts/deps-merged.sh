#!/usr/bin/env bash
# deps-merged.sh — kiểm tra ticket phụ thuộc đã squash-merge vào main chưa (luật 2).
#
# Cách dùng: deps-merged.sh MEL-31 MEL-38 ...   (nhận cả mã nội bộ như CT-01, tra trong ticket-index.tsv)
# Dựa vào quy ước tiêu đề PR = commit sau squash: "MEL-<số> <type>(<module>): …".
# Mã thoát: 0 tất cả đã merge, 1 còn ticket chưa merge hoặc không tra được.
set -uo pipefail

if [ $# -eq 0 ]; then
  sed -n '2,6p' "$0"
  exit 1
fi

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
index="$script_dir/../references/ticket-index.tsv"

git rev-parse --show-toplevel >/dev/null 2>&1 || { echo "Không ở trong git repo." >&2; exit 1; }
git fetch -q origin main 2>/dev/null || echo "(không fetch được origin/main, dùng bản đang có)" >&2
ref=origin/main
git rev-parse -q --verify "$ref" >/dev/null || ref=main

missing=0
for arg in "$@"; do
  key=$(printf '%s' "$arg" | tr '[:lower:]' '[:upper:]')
  if ! [[ $key =~ ^MEL-[0-9]+$ ]]; then
    found=""
    [ -f "$index" ] && found=$(awk -F'\t' -v c="$key" 'toupper($2)==c {print $1; exit}' "$index")
    if [ -z "$found" ]; then
      echo "[?] $arg: không tra được key MEL (kiểm tra ticket-index.tsv hoặc Jira)"
      missing=$((missing + 1))
      continue
    fi
    echo "($arg = $found)"
    key=$found
  fi
  hit=$(git log "$ref" --format='%h %s' 2>/dev/null | grep -E "^[0-9a-f]+ ${key}([ :]|$)" | head -1)
  if [ -n "$hit" ]; then
    echo "[ĐÃ MERGE] $key — $hit"
  else
    echo "[CHƯA] $key — không có commit nào trên $ref bắt đầu bằng \"$key \""
    missing=$((missing + 1))
  fi
done

if [ "$missing" -eq 0 ]; then
  echo "Kết quả: mọi phụ thuộc đã merge"
  exit 0
fi
echo "Kết quả: còn $missing phụ thuộc chưa merge hoặc chưa tra được"
exit 1
