#!/usr/bin/env bash
# link-agent-skills.sh — tạo symlink .agents/skills/<tên> → ../../.claude/skills/<tên>
# để Codex đọc cùng bộ skill với Claude Code (một nguồn duy nhất, không chép tay).
# Chạy lại an toàn bao nhiêu lần cũng được; xóa các link hỏng.
set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$root"
mkdir -p .agents/skills

link_skill() {
  local name=$1
  local source=".claude/skills/$name"
  local link=".agents/skills/$name"
  local target="../../.claude/skills/$name"

  if [ -L "$link" ]; then
    ln -sfn "$target" "$link" || return 1
  elif [ ! -e "$link" ]; then
    ln -s "$target" "$link" || return 1
  elif [ -L "$source" ] && [ "$(readlink -f -- "$source")" = "$(readlink -f -- "$link")" ]; then
    # Cấu trúc cũ của orca-cli: nguồn Claude trỏ ngược tới thư mục thật phía agent.
    # Đưa thư mục thật sang nguồn chuẩn trước, rồi mới tạo link đúng chiều.
    local staging=".claude/skills/.${name}.migrating.$$"
    mv -- "$link" "$staging" || return 1
    if ! rm -- "$source"; then
      mv -- "$staging" "$link"
      return 1
    fi
    if ! mv -- "$staging" "$source"; then
      mv -- "$staging" "$link"
      ln -s "../../.agents/skills/$name" "$source"
      return 1
    fi
    ln -s "$target" "$link" || return 1
    echo "đã chuyển nội dung $name về .claude/skills"
  elif [ -d "$source" ] && [ -d "$link" ] && diff -qr -- "$source" "$link" >/dev/null; then
    # Orca có thể cài lại cùng skill vào shared .agents/skills. Chỉ bỏ bản sao
    # khi đã chứng minh hai cây giống hệt nhau; nội dung khác nhau luôn được giữ lại.
    rm -rf -- "$link" || return 1
    ln -s "$target" "$link" || return 1
    echo "đã thay bản sao trùng $link bằng symlink"
  elif [ -d "$link" ] && [ -z "$(find "$link" -mindepth 1 -print -quit)" ]; then
    rmdir -- "$link" || return 1
    ln -s "$target" "$link" || return 1
  else
    echo "LỖI: $link là đường dẫn thật có nội dung khác; giữ nguyên để tránh mất dữ liệu" >&2
    return 1
  fi

  echo "liên kết .agents/skills/$name"
}

status=0
for d in .claude/skills/*/; do
  name=$(basename "$d")
  link_skill "$name" || status=1
done

for l in .agents/skills/*; do
  if [ -L "$l" ] && [ ! -e "$l" ]; then
    rm "$l"
    echo "đã xóa link hỏng $l"
  fi
done

exit "$status"
