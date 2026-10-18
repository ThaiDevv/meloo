#!/usr/bin/env bash
# check-scope.sh — kiểm tra luật 3 (chỉ sửa file trong Phạm vi file) và luật 5 (khóa file nóng).
#
# Cách dùng:
#   check-scope.sh --scope-file FILE [--labels "hot-ci,hot-pom"] [--base REF]
#       Tự kiểm trong worktree: so file đổi (đã commit + chưa commit + file mới) với các glob trong FILE.
#   check-scope.sh --pr N [--labels ...]
#       Review: lấy khối meloo:scope và dòng "File nóng đã giữ" từ mô tả PR, file đổi từ gh pr diff.
#   check-scope.sh --pr-body FILE [--base REF] [--labels ...]
#       CI: đọc mô tả PR từ FILE, file đổi từ git (BASE...HEAD).
#
# Glob: '*' và '**' khớp mọi ký tự, kể cả '/'. Mục kết thúc bằng '/' được hiểu là cả thư mục.
# Dòng bắt đầu bằng '!' là loại trừ, VD '!frontend/src/shared/realtime/messages.ts'.
# Mã thoát: 0 đạt, 1 có vi phạm, 2 dùng sai hoặc thiếu dữ liệu.
set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
hot_table="$script_dir/hot-files.tsv"

scope_file="" pr="" pr_body="" labels_arg="" base=""
while [ $# -gt 0 ]; do
  case "$1" in
    --scope-file) scope_file=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --pr-body) pr_body=${2:-}; shift 2 ;;
    --labels) labels_arg=${2:-}; shift 2 ;;
    --base) base=${2:-}; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "Tham số lạ: $1 (xem --help)" >&2; exit 2 ;;
  esac
done

if [ -z "$scope_file$pr$pr_body" ]; then
  echo "Cần một trong --scope-file, --pr hoặc --pr-body (xem --help)." >&2
  exit 2
fi

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "Không ở trong git repo." >&2; exit 2; }
cd "$repo_root" || exit 2

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Lấy các dòng glob trong khối <!-- meloo:scope --> … <!-- /meloo:scope --> của mô tả PR.
extract_scope_block() {
  awk '/<!-- *meloo:scope *-->/{inside=1; next} /<!-- *\/meloo:scope *-->/{inside=0} inside' "$1"
}

# Lấy label hot-* từ dòng "File nóng đã giữ: …" của mô tả PR.
extract_hot_labels() {
  grep -i 'File nóng đã giữ' "$1" | grep -oE 'hot-[a-z-]+' | paste -sd, - || true
}

body_file=""
if [ -n "$pr" ]; then
  command -v gh >/dev/null || { echo "Cần gh CLI cho --pr." >&2; exit 2; }
  body_file="$tmp/pr-body.md"
  gh pr view "$pr" --json body -q .body >"$body_file" || { echo "Không đọc được PR $pr." >&2; exit 2; }
  gh pr diff "$pr" --name-only >"$tmp/changed.raw" || { echo "Không lấy được diff của PR $pr." >&2; exit 2; }
elif [ -n "$pr_body" ]; then
  [ -f "$pr_body" ] || { echo "Không thấy file mô tả PR: $pr_body" >&2; exit 2; }
  body_file="$pr_body"
fi

if [ -n "$body_file" ]; then
  extract_scope_block "$body_file" >"$tmp/scope.raw"
  [ -z "$labels_arg" ] && labels_arg=$(extract_hot_labels "$body_file")
else
  [ -f "$scope_file" ] || { echo "Không thấy file phạm vi: $scope_file" >&2; exit 2; }
  cp "$scope_file" "$tmp/scope.raw"
fi

# Chuẩn hóa glob: bỏ dòng trống, comment, hàng rào code, dấu gạch đầu dòng, backtick.
# Lọc hàng rào code TRƯỚC khi xóa backtick, nếu không dòng "```text" sẽ thành glob "text".
patterns=() excludes=()
while IFS= read -r line || [ -n "$line" ]; do
  line=${line%%$'\r'}
  line=$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
  case "$line" in ''|'#'*|'```'*|'~~~'*|'<!--'*) continue ;; esac
  line=$(printf '%s' "$line" | sed -E 's/^[-*][[:space:]]+//; s/`//g; s/[[:space:]]+$//')
  case "$line" in ''|'#'*) continue ;; esac
  negate=0
  case "$line" in '!'*) negate=1; line=${line#!} ;; esac
  case "$line" in */) line="${line}*" ;; esac
  if [ "$negate" -eq 1 ]; then excludes+=("$line"); else patterns+=("$line"); fi
done <"$tmp/scope.raw"

if [ ${#patterns[@]} -eq 0 ]; then
  echo "Không có glob nào trong Phạm vi file. Điền khối meloo:scope hoặc file phạm vi trước." >&2
  exit 2
fi

# Danh sách file đổi.
if [ ! -f "$tmp/changed.raw" ]; then
  if [ -z "$base" ]; then
    if git rev-parse -q --verify origin/main >/dev/null; then base=origin/main
    elif git rev-parse -q --verify main >/dev/null; then base=main
    else echo "Không thấy origin/main hay main; truyền --base." >&2; exit 2; fi
  fi
  {
    git diff --name-only "$base"...HEAD
    if [ -z "$pr_body" ]; then
      git diff --name-only HEAD
      git ls-files --others --exclude-standard
    fi
  } >"$tmp/changed.raw" 2>/dev/null || { echo "git diff thất bại với base $base." >&2; exit 2; }
fi
sort -u "$tmp/changed.raw" | sed '/^$/d' >"$tmp/changed"
changed_count=$(wc -l <"$tmp/changed" | tr -d ' ')

matches_any() {
  local f=$1; shift
  local p
  for p in "$@"; do
    # shellcheck disable=SC2053
    [[ $f == $p ]] && return 0
  done
  return 1
}

# Trong phạm vi = khớp ít nhất một glob và không khớp dòng loại trừ nào.
in_scope() {
  matches_any "$1" "${patterns[@]}" || return 1
  if [ ${#excludes[@]} -gt 0 ] && matches_any "$1" "${excludes[@]}"; then return 1; fi
  return 0
}

held=",${labels_arg// /},"
out_of_scope=() hot_violation=() hot_info=() harness_touch=()

while IFS= read -r f; do
  in_scope "$f" || out_of_scope+=("$f")
  while IFS=$'\t' read -r label glob; do
    case "$label" in ''|'#'*) continue ;; esac
    # shellcheck disable=SC2053
    if [[ $f == $glob ]]; then
      if [ "$label" = harness ]; then
        in_scope "$f" || harness_touch+=("$f")
      elif [[ $held == *",$label,"* ]]; then
        hot_info+=("$label: $f")
      else
        hot_violation+=("$label: $f")
      fi
    fi
  done <"$hot_table"
done <"$tmp/changed"

src=${scope_file:-${pr:+PR #$pr}}
src=${src:-$pr_body}
echo "check-scope: $changed_count file đổi, ${#patterns[@]} glob phạm vi, ${#excludes[@]} loại trừ (nguồn: $src${base:+, base $base})"
problems=0
if [ ${#out_of_scope[@]} -gt 0 ]; then
  echo "[LỖI] File ngoài phạm vi (${#out_of_scope[@]}):"
  printf '  - %s\n' "${out_of_scope[@]}"
  problems=$((problems + ${#out_of_scope[@]}))
fi
if [ ${#hot_violation[@]} -gt 0 ]; then
  echo "[LỖI] Chạm file nóng mà ticket không giữ label:"
  printf '  - %s\n' "${hot_violation[@]}"
  problems=$((problems + ${#hot_violation[@]}))
fi
if [ ${#harness_touch[@]} -gt 0 ]; then
  echo "[LỖI] Sửa file harness ngoài phạm vi (chỉ sửa khi ticket ghi rõ):"
  printf '  - %s\n' "${harness_touch[@]}"
fi
if [ ${#hot_info[@]} -gt 0 ]; then
  echo "[THÔNG TIN] File nóng có chạm và ticket đang giữ label:"
  printf '  - %s\n' "${hot_info[@]}"
fi
if [ "$problems" -eq 0 ]; then
  echo "Kết quả: ĐẠT"
  exit 0
fi
echo "Kết quả: KHÔNG ĐẠT ($problems vấn đề)"
exit 1
