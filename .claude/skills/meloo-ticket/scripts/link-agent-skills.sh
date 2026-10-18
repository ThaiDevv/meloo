#!/usr/bin/env bash
# link-agent-skills.sh — tạo symlink .agents/skills/<tên> → ../../.claude/skills/<tên>
# để Codex đọc cùng bộ skill với Claude Code (một nguồn duy nhất, không chép tay).
# Chạy lại an toàn bao nhiêu lần cũng được; xóa các link hỏng.
set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$root"
mkdir -p .agents/skills

for d in .claude/skills/*/; do
  name=$(basename "$d")
  ln -sfn "../../.claude/skills/$name" ".agents/skills/$name"
  echo "liên kết .agents/skills/$name"
done

for l in .agents/skills/*; do
  if [ -L "$l" ] && [ ! -e "$l" ]; then
    rm "$l"
    echo "đã xóa link hỏng $l"
  fi
done
