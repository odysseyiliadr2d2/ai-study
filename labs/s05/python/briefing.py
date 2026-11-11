"""아침 브리핑: digest → Claude 요약 → 파일 저장 · CSV 기록 — python briefing.py
n8n 의 ⑥ Basic LLM Chain ⑦ Gmail Send ⑧ Google Sheets Append 를 코드로 옮긴 것입니다.
"""
import csv
import os
from pathlib import Path

from anthropic import Anthropic

from collect import build_digest

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")   # 요약은 claude-haiku-5-5 로도 충분한지 비교
OUT = Path(os.getenv("BRIEF_OUT", "out"))

# n8n 실습의 prompt.txt 와 같은 문구
PROMPT = """아래는 {date} 아침까지 받은 메일 {mailCount}건과
RSS 새 글 {feedCount}건입니다.

다음 형식으로 한국어 브리핑을 작성하세요.
1. 오늘 꼭 처리할 일 (최대 3개, 보낸 사람 표시)
2. 참고만 할 메일 (한 줄씩)
3. 기술 소식 (RSS, 제목과 한 줄 요약)
- 원문에 없는 내용은 쓰지 않습니다.
- 전체 15줄 이내.

---
{digest}"""


def summarize(d: dict) -> str:
    client = Anthropic()   # ANTHROPIC_API_KEY 환경변수 — 코드와 파일에는 키를 두지 않습니다
    msg = client.messages.create(
        model=MODEL,
        max_tokens=2048,   # temperature · top_p 는 넣지 않습니다 (Claude 5.5 는 400 오류)
        messages=[{"role": "user", "content": PROMPT.format(**d)}],
    )
    return "".join(b.text for b in msg.content if b.type == "text").strip()


def deliver(d: dict, summary: str) -> Path:
    """⑦ 대신 Markdown 파일, ⑧ 대신 CSV 한 줄. 엑셀에서 한글이 깨지지 않게 새 CSV 는 BOM 을 붙입니다."""
    OUT.mkdir(exist_ok=True)
    md = OUT / f"briefing-{d['date']}.md"
    md.write_text(f"# [아침 브리핑] {d['date']}\n\n{summary}\n", encoding="utf-8")
    log = OUT / "briefing_log.csv"
    new = not log.exists()
    with log.open("a", newline="", encoding="utf-8-sig" if new else "utf-8") as f:
        w = csv.writer(f)
        if new:
            w.writerow(["date", "mails", "feeds", "summary"])
        w.writerow([d["date"], d["mailCount"], d["feedCount"], summary[:500]])
    return md


if __name__ == "__main__":
    d = build_digest()
    summary = summarize(d)          # 메일 0건이어도 '메일 0건' 브리핑을 만듭니다
    md = deliver(d, summary)
    print(summary)
    print(f"\n저장: {md} · 기록: {OUT / 'briefing_log.csv'}")
