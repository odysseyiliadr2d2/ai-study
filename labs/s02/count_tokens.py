"""한국어와 영어의 토큰 수 비교 — python count_tokens.py
Anthropic 토큰 카운팅 API(무료, 분당 요청 수 제한 있음)를 씁니다. 결과는 추정치입니다.
"""
import os

from anthropic import Anthropic

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
client = Anthropic()  # ANTHROPIC_API_KEY 환경변수 사용

SAMPLES = {
    "한국어": "야간 배치가 실패하면 운영 당직자에게 먼저 알리고, 30분 안에 복구되지 않으면 팀장에게 전화합니다.",
    "영어": "If the nightly batch fails, notify the on-call operator first, and call the team lead if it is not recovered within 30 minutes.",
    "SQL": "SELECT BASE_DT, BRNCH_CD, SUM(TRD_AMT) FROM ADW.TB_TRD_DAILY WHERE BASE_DT = :dt GROUP BY BASE_DT, BRNCH_CD",
    "테이블명": "TB_ADW_CUST_GRD_HIST",
}


def count(text: str) -> int:
    resp = client.messages.count_tokens(
        model=MODEL,
        messages=[{"role": "user", "content": text}],
    )
    return resp.input_tokens   # 메시지 구조에 쓰이는 토큰 몇 개가 함께 셉니다


print(f"모델: {MODEL}")
print(f"{'구분':8s} {'글자 수':>6s} {'토큰 수':>6s} {'글자/토큰':>8s}")
for name, text in SAMPLES.items():
    n = count(text)
    print(f"{name:8s} {len(text):>6d} {n:>6d} {len(text) / n:>8.2f}")
