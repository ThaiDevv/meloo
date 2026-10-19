#!/usr/bin/env bash
# deps-merged.sh — kiểm tra ticket phụ thuộc đã squash-merge vào main chưa (luật 2).
#
# Cách dùng: deps-merged.sh MEL-31 MEL-38 ...   (nhận cả mã nội bộ như CT-01, tra trong ticket-index.tsv)
# Dựa vào quy ước tiêu đề PR = commit sau squash: "MEL-<số> <type>(<module>): …".
# Commit squash bị sửa mất tiền tố MEL thì tra thêm PR đã merge qua gh (nếu có gh).
# Ticket owner-me (việc của Thái, không có PR) được báo KIỂM TAY: xem trạng thái Done trên Jira.
# Mã thoát: 0 tất cả đã merge, 1 còn ticket chưa merge, cần kiểm tay hoặc không tra được.
set -uo pipefail

if [ $# -eq 0 ]; then
  sed -n '2,8p' "$0"
  exit 1
fi

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
index="$script_dir/../references/ticket-index.tsv"

git rev-parse --show-toplevel >/dev/null 2>&1 || { echo "Không ở trong git repo." >&2; exit 1; }
git fetch -q origin main 2>/dev/null || echo "(không fetch được origin/main, dùng bản đang có)" >&2
ref=origin/main
git rev-parse -q --verify "$ref" >/dev/null || ref=main

# In "PR #<số> → <commit>" nếu có PR tiêu đề bắt đầu bằng KEY đã merge và commit merge nằm trên $ref.
pr_merged_into_ref() {
  command -v gh >/dev/null || return 0
  local line num oid
  while IFS=$'\t' read -r num oid line; do
    [[ $line == "$1 "* || $line == "$1:"* ]] || continue
    [ -n "$oid" ] && git merge-base --is-ancestor "$oid" "$ref" 2>/dev/null || continue
    echo "PR #$num → ${oid:0:7}"
    return 0
  done < <(gh pr list --state merged --base main --search "$1 in:title" --limit 20 \
             --json number,title,mergeCommit -q '.[] | [.number, (.mergeCommit.oid // ""), .title] | @tsv' 2>/dev/null)
}

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
    continue
  fi
  pr_hit=$(pr_merged_into_ref "$key")
  if [ -n "$pr_hit" ]; then
    echo "[ĐÃ MERGE] $key — $pr_hit (commit squash thiếu tiền tố $key)"
    continue
  fi
  if [ -f "$index" ] && awk -F'\t' -v k="$key" '$1==k && $4 ~ /(^|,)owner-me(,|$)/ {f=1} END {exit !f}' "$index"; then
    echo "[KIỂM TAY] $key — việc của Thái, không có PR: xem ticket đã Done trên Jira chưa"
  else
    echo "[CHƯA] $key — không có commit nào trên $ref bắt đầu bằng \"$key \" và không thấy PR đã merge"
  fi
  missing=$((missing + 1))
done

if [ "$missing" -eq 0 ]; then
  echo "Kết quả: mọi phụ thuộc đã merge"
  exit 0
fi
echo "Kết quả: còn $missing phụ thuộc chưa merge, cần kiểm tay hoặc chưa tra được"
exit 1
