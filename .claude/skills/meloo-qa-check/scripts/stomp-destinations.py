#!/usr/bin/env python3
"""So destination STOMP trong contracts/asyncapi.yaml với destination dùng trong code (backend + frontend).

Đây là kiểm tra theo mẫu chữ (heuristic), không phân tích cú pháp đầy đủ: đọc lại bằng mắt từng mục [LỆCH].
- Hợp đồng: dòng `address:` (AsyncAPI 3) hoặc key kênh bắt đầu bằng '/' dưới `channels:` (AsyncAPI 2).
- Code: @MessageMapping/@SubscribeMapping (cộng tiền tố /app), và chuỗi bắt đầu bằng /app/, /topic/, /user/queue/, /queue/.
- Tham số {roomId}, ${id}, :id được chuẩn hóa thành {}. Chuỗi kết thúc bằng '/' (nối chuỗi) được hiểu là tiền tố.
Mã thoát: 0 khớp, 1 có destination trong code không có trong hợp đồng, 2 thiếu file.
"""
import argparse
import re
import sys
from pathlib import Path

PREFIXES = ("/app/", "/topic/", "/user/queue/", "/queue/")
CODE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx"}
SKIP_DIRS = {"node_modules", "target", "dist", "build", ".git", "coverage"}

MAPPING = re.compile(r"@(?:MessageMapping|SubscribeMapping)\(\s*(?:value\s*=\s*)?\{?\s*\"([^\"]+)\"")
LITERAL = re.compile(r"[\"'`](/(?:app|topic|user/queue|queue)/[^\"'`\s]*)[\"'`]")


def norm(d):
    d = d.strip().strip("\"'`")
    d = re.sub(r"\$\{[^}]*\}", "{}", d)
    d = re.sub(r"\{[^}/]*\}", "{}", d)
    d = re.sub(r"/:[A-Za-z_]\w*", "/{}", d)
    return d.rstrip("/") or "/"


def contract_destinations(path):
    text = path.read_text(encoding="utf-8")
    found = set()
    for m in re.finditer(r"^\s*address:\s*['\"]?([^'\"#\s]+)", text, re.M):
        found.add(norm(m.group(1)))
    in_channels = False
    for line in text.splitlines():
        if re.match(r"^channels:\s*$", line):
            in_channels = True
            continue
        if in_channels and re.match(r"^\S", line):
            in_channels = False
        if in_channels:
            m = re.match(r"^  ['\"]?(/[^'\":]+)['\"]?:\s*$", line)
            if m:
                found.add(norm(m.group(1)))
    return found


def code_destinations(roots, app_prefix):
    found = {}
    for root in roots:
        base = Path(root)
        if not base.exists():
            continue
        for p in base.rglob("*"):
            if p.suffix not in CODE_SUFFIXES or SKIP_DIRS & set(p.parts) or not p.is_file():
                continue
            text = p.read_text(encoding="utf-8", errors="ignore")
            for m in MAPPING.finditer(text):
                d = m.group(1) if m.group(1).startswith("/") else "/" + m.group(1)
                if not d.startswith(PREFIXES):
                    d = app_prefix.rstrip("/") + d
                found.setdefault((norm(d), False), set()).add(str(p))
            for m in LITERAL.finditer(text):
                raw = m.group(1)
                is_prefix = raw.endswith("/") or raw.endswith("${")
                found.setdefault((norm(raw), is_prefix), set()).add(str(p))
    return found


def known(dest, is_prefix, contract):
    candidates = {dest}
    if dest.startswith("/queue/"):
        candidates.add("/user" + dest)
    for c in candidates:
        if c in contract:
            return True
        if is_prefix and any(x.startswith(c + "/") for x in contract):
            return True
    return False


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--contract", default="contracts/asyncapi.yaml")
    ap.add_argument("--roots", default="backend/src/main,frontend/src",
                    help="thư mục code cần quét, cách nhau bằng dấu phẩy")
    ap.add_argument("--app-prefix", default="/app", help="application destination prefix của Spring")
    args = ap.parse_args()

    contract_path = Path(args.contract)
    if not contract_path.exists():
        print(f"Không thấy {contract_path}", file=sys.stderr)
        return 2
    contract = contract_destinations(contract_path)
    code = code_destinations([r for r in args.roots.split(",") if r], args.app_prefix)

    print(f"Hợp đồng: {len(contract)} destination · code: {len(code)} chỗ dùng")
    mismatches = 0
    used = set()
    for (dest, is_prefix), files in sorted(code.items()):
        if known(dest, is_prefix, contract):
            # Chỉ tính "đã dùng" khi khớp nguyên văn; tiền tố (nối chuỗi) không chứng minh được kênh cụ thể nào.
            used |= {c for c in contract if c == dest or c == "/user" + dest}
            continue
        mismatches += 1
        kind = " (tiền tố)" if is_prefix else ""
        print(f"[LỆCH] {dest}{kind} — có trong code, không có trong hợp đồng: {', '.join(sorted(files))}")
    for c in sorted(contract - used):
        print(f"[CHƯA DÙNG] {c} — có trong hợp đồng, chưa thấy trong code (bình thường nếu tính năng chưa làm)")
    print("Kết quả: " + ("KHỚP" if mismatches == 0 else f"LỆCH {mismatches} destination"))
    return 0 if mismatches == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
