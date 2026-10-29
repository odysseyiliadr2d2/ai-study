#!/usr/bin/env python3
"""세션 JSON 의 실습 코드 중 file 이 labs/ 로 시작하는 것을 실제 파일로 꺼냅니다.

    python3 tools/export_labs.py

labs/sNN/ 아래에 실행 가능한 예제 파일이 생깁니다. 세션 JSON 을 고친 뒤 다시 실행하세요.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def walk(obj):
    if isinstance(obj, dict):
        if "src" in obj and "file" in obj:
            yield obj
        for v in obj.values():
            yield from walk(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from walk(v)


def main():
    n = 0
    for f in sorted((ROOT / "data" / "sessions").glob("s*.json")):
        s = json.loads(f.read_text(encoding="utf-8"))
        for c in walk(s):
            rel = c["file"]
            if not rel.startswith("labs/"):
                continue
            out = ROOT / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            src = c["src"] if c["src"].endswith("\n") else c["src"] + "\n"
            out.write_text(src, encoding="utf-8")
            n += 1
    print(f"실습 파일 {n}개 → labs/")


if __name__ == "__main__":
    main()
