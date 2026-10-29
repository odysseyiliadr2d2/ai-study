"""1단계: 문서를 청크로 자르기 — python chunk.py docs/ > chunks.jsonl"""
import json
import re
import sys
from pathlib import Path

MAX_CHARS = 600   # 청크 최대 길이
OVERLAP = 100     # 앞 청크와 겹치는 글자 수


def split_sections(text: str):
    """마크다운 제목(#) 기준으로 큰 덩어리를 나눕니다."""
    parts = re.split(r"(?m)^(#{1,3} .+)$", text)
    title, buf = "", ""
    for p in parts:
        if re.match(r"^#{1,3} ", p):
            if buf.strip():
                yield title, buf.strip()
            title, buf = p.lstrip("# ").strip(), ""
        else:
            buf += p
    if buf.strip():
        yield title, buf.strip()


def chunk(text: str):
    """긴 덩어리는 MAX_CHARS 단위로 자르되 OVERLAP 만큼 겹칩니다."""
    start = 0
    while start < len(text):
        end = min(start + MAX_CHARS, len(text))
        yield text[start:end]
        if end == len(text):
            break
        start = end - OVERLAP


def main(folder: str):
    for path in sorted(Path(folder).glob("**/*.md")):
        text = path.read_text(encoding="utf-8")
        for title, body in split_sections(text):
            for i, piece in enumerate(chunk(body)):
                rec = {
                    "doc": path.name,
                    "section": title,
                    "part": i,
                    # 제목을 본문 앞에 붙이면 검색이 훨씬 잘 됩니다
                    "text": f"[{path.stem} > {title}] {piece}",
                }
                print(json.dumps(rec, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs")
