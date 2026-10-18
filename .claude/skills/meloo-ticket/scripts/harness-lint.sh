#!/usr/bin/env bash
# harness-lint.sh — kiểm tra cấu trúc harness: skill, agent, symlink cho Codex, file cấu hình.
# Chạy tay hoặc trong CI (job "harness" của SET-05). Mã thoát: 0 đạt, 1 có lỗi.
set -uo pipefail
root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
exec python3 -I - "$root" <<'PY'
import json, re, sys, tomllib
from pathlib import Path

root = Path(sys.argv[1])
errors, warnings = [], []

def frontmatter(path):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None, text
    try:
        end = lines[1:].index("---") + 1
    except ValueError:
        return None, text
    fm, key = {}, None
    for raw in lines[1:end]:
        m = re.match(r"^([A-Za-z_][\w-]*):\s*(.*)$", raw)
        if m:
            key, val = m.group(1), m.group(2).strip()
            fm[key] = val.strip('"') if val else []
        elif key and re.match(r"^\s+-\s+", raw):
            if not isinstance(fm.get(key), list):
                fm[key] = []
            fm[key].append(raw.split("-", 1)[1].strip())
    return fm, "\n".join(lines[end + 1:])

skills_dir = root / ".claude/skills"
skill_names = set()
for d in sorted(p for p in skills_dir.iterdir() if p.is_dir()) if skills_dir.is_dir() else []:
    name = d.name
    skill_names.add(name)
    md = d / "SKILL.md"
    if not md.is_file():
        errors.append(f"skill {name}: thiếu SKILL.md"); continue
    fm, body = frontmatter(md)
    if fm is None:
        errors.append(f"skill {name}: frontmatter phải bắt đầu ở dòng 1 bằng ---"); continue
    if fm.get("name") != name:
        errors.append(f"skill {name}: name trong frontmatter ({fm.get('name')}) khác tên thư mục")
    if not re.fullmatch(r"[a-z0-9-]{1,64}", name):
        errors.append(f"skill {name}: tên chỉ gồm chữ thường, số, gạch nối, ≤ 64 ký tự")
    desc = fm.get("description") or ""
    if not desc:
        errors.append(f"skill {name}: thiếu description")
    elif len(desc) > 1024:
        errors.append(f"skill {name}: description dài {len(desc)} ký tự (> 1024, Codex có thể từ chối)")
    extra = set(fm) - {"name", "description"}
    if extra:
        warnings.append(f"skill {name}: frontmatter có thêm {sorted(extra)} — Codex có thể bỏ qua")
    n_lines = len(body.splitlines())
    if n_lines >= 500:
        errors.append(f"skill {name}: thân SKILL.md {n_lines} dòng (≥ 500), chuyển bớt sang references/")
    for ref in sorted(set(re.findall(r"(?<![\w/.-])((?:references|scripts)/[\w./-]+\.(?:md|sh|py|tsv|js))", body))):
        if not (d / ref).exists():
            errors.append(f"skill {name}: tham chiếu {ref} không tồn tại")
    link = root / ".agents/skills" / name
    if not link.is_symlink():
        errors.append(f"skill {name}: thiếu symlink .agents/skills/{name} (chạy scripts/link-agent-skills.sh)")
    elif link.resolve() != d.resolve():
        errors.append(f"skill {name}: .agents/skills/{name} trỏ sai chỗ ({link.resolve()})")

agents_link_dir = root / ".agents/skills"
if agents_link_dir.is_dir():
    for l in agents_link_dir.iterdir():
        if l.name not in skill_names:
            errors.append(f".agents/skills/{l.name}: không có skill tương ứng trong .claude/skills")

valid_models = {"sonnet", "opus", "haiku", "fable", "inherit"}
for md in sorted((root / ".claude/agents").glob("*.md")):
    fm, body = frontmatter(md)
    if fm is None:
        errors.append(f"agent {md.name}: thiếu frontmatter"); continue
    if fm.get("name") != md.stem:
        errors.append(f"agent {md.name}: name ({fm.get('name')}) khác tên file")
    if not fm.get("description"):
        errors.append(f"agent {md.name}: thiếu description")
    model = fm.get("model")
    if model and model not in valid_models and not str(model).startswith("claude-"):
        errors.append(f"agent {md.name}: model lạ '{model}'")
    for s in fm.get("skills") or []:
        if s not in skill_names:
            errors.append(f"agent {md.name}: skill '{s}' không tồn tại")

for t in sorted((root / ".codex/agents").glob("*.toml")):
    try:
        data = tomllib.loads(t.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as e:
        errors.append(f"{t.relative_to(root)}: TOML lỗi: {e}"); continue
    for k in ("name", "description", "developer_instructions"):
        if not data.get(k):
            errors.append(f"{t.relative_to(root)}: thiếu {k}")
    if data.get("name") != t.stem:
        errors.append(f"{t.relative_to(root)}: name ({data.get('name')}) khác tên file")

for j in (".claude/settings.json", ".mcp.json"):
    p = root / j
    if p.exists():
        try:
            json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            errors.append(f"{j}: JSON lỗi: {e}")
    else:
        errors.append(f"{j}: không có file")
cfg = root / ".codex/config.toml"
if cfg.exists():
    try:
        tomllib.loads(cfg.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as e:
        errors.append(f".codex/config.toml: TOML lỗi: {e}")

agents_md = root / "AGENTS.md"
if not agents_md.exists():
    errors.append("AGENTS.md: không có file")
elif len(agents_md.read_text(encoding="utf-8").splitlines()) > 200:
    warnings.append("AGENTS.md dài hơn 200 dòng: nạp ở mọi phiên, nên rút gọn")
claude_md = root / "CLAUDE.md"
if not claude_md.exists():
    errors.append("CLAUDE.md: không có file")
else:
    first = next((l.strip() for l in claude_md.read_text(encoding="utf-8").splitlines() if l.strip()), "")
    if first != "@AGENTS.md":
        errors.append("CLAUDE.md: dòng đầu phải là @AGENTS.md để Claude Code nạp luật chung")

for p in (root / ".claude").rglob("*"):
    if p.is_file() and p.suffix in {".md", ".js", ".json"}:
        txt = p.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"\b(TeamCreate|TeamDelete|CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS)\b", txt):
            errors.append(f"{p.relative_to(root)}: còn cú pháp harness v1 (TeamCreate/TeamDelete/cờ thử nghiệm)")
if (root / ".claude/commands").exists():
    errors.append(".claude/commands/: không dùng nữa, chuyển thành skill")

for wf in sorted((root / ".claude/workflows").glob("*.js")):
    txt = wf.read_text(encoding="utf-8")
    first_stmt = next((l for l in txt.splitlines() if l.strip() and not l.strip().startswith("//")), "")
    if not first_stmt.startswith("export const meta = {"):
        errors.append(f"{wf.relative_to(root)}: câu lệnh đầu phải là export const meta = {{")
    code = re.sub(r"//.*", "", txt)
    if re.search(r"Date\.now\(|Math\.random\(|new Date\(\s*\)", code):
        errors.append(f"{wf.relative_to(root)}: không dùng Date.now/Math.random/new Date() trong workflow")

for w in warnings:
    print(f"[CẢNH BÁO] {w}")
for e in errors:
    print(f"[LỖI] {e}")
print(f"harness-lint: {len(skill_names)} skill, {len(list((root / '.claude/agents').glob('*.md')))} agent Claude, "
      f"{len(list((root / '.codex/agents').glob('*.toml')))} agent Codex — "
      + ("ĐẠT" if not errors else f"KHÔNG ĐẠT ({len(errors)} lỗi)"))
sys.exit(1 if errors else 0)
PY
