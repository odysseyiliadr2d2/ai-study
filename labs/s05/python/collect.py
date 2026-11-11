"""메일 · RSS 를 모아 LLM 에 넘길 digest 하나로 만듭니다 — python collect.py
n8n 워크플로의 ② Gmail ③ RSS Read ④ Merge ⑤ Code(Digest) 를 코드로 옮긴 것입니다.
"""
import json
import os
import re
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import feedparser

KST = ZoneInfo("Asia/Seoul")
MAX_BODY = 500      # 메일 한 통에서 쓸 최대 글자 수
MAX_MAILS = 30      # 토큰 비용을 막는 상한
DATA = Path(os.getenv("BRIEF_DATA", "data"))
FEED = os.getenv("BRIEF_FEED", str(DATA / "feed.xml"))   # 실제 RSS 주소를 넣어도 됩니다


def now() -> datetime:
    """BRIEF_NOW(예: 2026-11-13T07:30:00+09:00)가 있으면 그 시각, 없으면 지금(서울 시간)."""
    v = os.getenv("BRIEF_NOW")
    return datetime.fromisoformat(v) if v else datetime.now(KST)


def squash(text: str, limit: int) -> str:
    """HTML 태그와 연속 공백을 지우고 limit 글자로 자릅니다."""
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", text or "")).strip()[:limit]


def load_mails(at: datetime) -> list[dict]:
    """② Gmail 대신: 지난 24시간에 받은 메일, 프로모션 제외 (n8n 의 Received After · -category:promotions)."""
    mails = json.loads((DATA / "mails.json").read_text(encoding="utf-8"))
    since = at - timedelta(hours=24)
    return [m for m in mails
            if m.get("category") != "promotions" and since <= datetime.fromisoformat(m["received"]) <= at]


def load_feed() -> list[dict]:
    """③ RSS Read: 주소와 파일 경로를 모두 받습니다."""
    parsed = feedparser.parse(FEED)
    return [{"title": e.get("title", ""), "link": e.get("link", ""), "summary": e.get("summary", "")}
            for e in parsed.entries]


def build_digest(at: datetime | None = None) -> dict:
    """④ Merge + ⑤ Code: 항목 수십 개를 문자열 하나로 합쳐 LLM 호출을 한 번으로 줄입니다."""
    at = at or now()
    mails = [f"- [{m.get('from', '')}] {m.get('subject') or '(제목 없음)'}\n  {squash(m.get('snippet', ''), MAX_BODY)}"
             for m in load_mails(at)]
    feeds = [f"- {f['title']} ({f['link']})\n  {squash(f['summary'], 300)}" for f in load_feed()]
    digest = "\n".join([f"## 받은 메일 {len(mails)}건", *mails[:MAX_MAILS], "",
                        f"## RSS 새 글 {len(feeds)}건", *feeds])
    return {"date": at.astimezone(KST).strftime("%Y-%m-%d"),
            "mailCount": len(mails), "feedCount": len(feeds), "digest": digest}


if __name__ == "__main__":
    d = build_digest()
    print(f"{d['date']} 메일 {d['mailCount']}건 · RSS {d['feedCount']}건\n")
    print(d["digest"])
