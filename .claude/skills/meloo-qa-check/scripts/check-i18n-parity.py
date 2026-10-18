#!/usr/bin/env python3
"""So khớp key i18n giữa các ngôn ngữ trong <root>/<lang>/*.json (mặc định frontend/src/locales, vi và en).

Báo: thiếu file, thiếu key, giá trị rỗng, biến nội suy {{...}} khác nhau giữa các ngôn ngữ.
Hậu tố số nhiều của i18next (_zero, _one, _two, _few, _many, _other) được gộp về key gốc,
vì tiếng Việt chỉ có _other còn tiếng Anh có _one và _other.
Mã thoát: 0 khớp, 1 có lệch, 2 sai tham số hoặc không có thư mục.
"""
import argparse
import json
import re
import sys
from pathlib import Path

PLURAL = re.compile(r"_(zero|one|two|few|many|other)$")
PLACEHOLDER = re.compile(r"\{\{\s*([\w.]+)[^}]*\}\}")


def flatten(obj, prefix=""):
    out = {}
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            if isinstance(v, dict):
                out.update(flatten(v, key))
            else:
                out[key] = v
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("root", nargs="?", default="frontend/src/locales")
    ap.add_argument("--langs", default="vi,en", help="danh sách ngôn ngữ, cách nhau bằng dấu phẩy")
    args = ap.parse_args()

    root = Path(args.root)
    langs = [l.strip() for l in args.langs.split(",") if l.strip()]
    if not root.is_dir() or len(langs) < 2:
        print(f"Không thấy thư mục {root} hoặc cần ít nhất 2 ngôn ngữ.", file=sys.stderr)
        return 2

    files = {lang: {p.name for p in (root / lang).glob("*.json")} for lang in langs}
    all_files = sorted(set().union(*files.values()))
    problems = 0

    for fname in all_files:
        data = {}
        for lang in langs:
            path = root / lang / fname
            if not path.exists():
                print(f"[THIẾU FILE] {lang}/{fname}")
                problems += 1
                continue
            try:
                data[lang] = flatten(json.loads(path.read_text(encoding="utf-8")))
            except json.JSONDecodeError as e:
                print(f"[JSON LỖI] {lang}/{fname}: {e}")
                problems += 1

        bases = {lang: {PLURAL.sub("", k) for k in d} for lang, d in data.items()}
        union = set().union(*bases.values()) if bases else set()
        for lang, keys in bases.items():
            for k in sorted(union - keys):
                print(f"[THIẾU KEY] {lang}/{fname}: {k}")
                problems += 1
        for lang, d in data.items():
            for k, v in sorted(d.items()):
                if isinstance(v, str) and not v.strip():
                    print(f"[RỖNG] {lang}/{fname}: {k}")
                    problems += 1

        if len(data) == len(langs):
            for base in sorted(union):
                found = {}
                for lang, d in data.items():
                    names = set()
                    for k, v in d.items():
                        if PLURAL.sub("", k) == base and isinstance(v, str):
                            names |= set(PLACEHOLDER.findall(v))
                    found[lang] = names
                if len({frozenset(s) for s in found.values()}) > 1:
                    detail = ", ".join(f"{lang}={sorted(s)}" for lang, s in found.items())
                    print(f"[BIẾN LỆCH] {fname}: {base} ({detail})")
                    problems += 1

    status = "ĐẠT" if problems == 0 else f"KHÔNG ĐẠT ({problems} vấn đề)"
    print(f"check-i18n-parity: {len(all_files)} file, ngôn ngữ {', '.join(langs)} — {status}")
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
