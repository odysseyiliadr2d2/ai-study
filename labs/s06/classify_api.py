"""실무 예제: 문의 메일 분류 API.  uvicorn classify_api:app --port 8000
POST /classify {"subject": "...", "body": "..."} → 분류 JSON
"""
import re
import time

from fastapi import FastAPI
from pydantic import BaseModel

from extract import extract
from schema import Category, Urgency

app = FastAPI(title="문의 메일 분류 API")

URGENT_WORDS = ("전면 장애", "접속 불가", "결제 실패", "데이터 유실")
TEAM = {
    Category.ACCOUNT: "인증운영팀", Category.BILLING: "영업지원팀", Category.BUG: "운영개발팀",
    Category.REQUEST: "기획팀", Category.DATA: "데이터팀", Category.OTHER: "고객지원팀",
}


class MailIn(BaseModel):
    subject: str
    body: str


def mask(text: str) -> str:
    """외부 API 로 보내기 전 개인정보 형태를 가립니다 (규칙은 사내 기준에 맞춰 보강)."""
    text = re.sub(r"[\w.+-]+@[\w-]+\.[\w.]+", "[이메일]", text)
    text = re.sub(r"01[016789]-?\d{3,4}-?\d{4}", "[전화]", text)
    text = re.sub(r"\d{6}-?[1-4]\d{6}", "[주민번호]", text)
    text = re.sub(r"\d{3,6}-\d{2,6}-\d{2,6}", "[번호]", text)
    return text


@app.post("/classify")
def classify(mail: MailIn):
    started = time.perf_counter()
    text = mask(f"제목: {mail.subject}\n{mail.body}")
    try:
        r = extract(text)
    except Exception as e:   # 재시도까지 실패하면 사람 검토로
        return {"status": "review", "reason": str(e)[:200]}

    urgency = r.urgency
    if any(w in text for w in URGENT_WORDS):   # 규칙이 모델보다 우선
        urgency = Urgency.HIGH
    status = "auto" if r.confidence >= 0.7 else "review"
    return {
        "status": status,
        "category": r.category.value,
        "team": TEAM[r.category],
        "urgency": urgency.value,
        "summary": r.summary,
        "error_code": r.error_code,
        "system_name": r.system_name,
        "requested_by_date": r.requested_by_date,
        "confidence": r.confidence,
        "elapsed_ms": int((time.perf_counter() - started) * 1000),
    }
