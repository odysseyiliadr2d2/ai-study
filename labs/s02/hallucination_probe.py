"""환각 탐지 실험: 존재하지 않는 사내 테이블을 물어봅니다 — python hallucination_probe.py"""
import os

from anthropic import Anthropic

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
client = Anthropic()

QUESTION = "우리 DW 의 TB_ADW_CUST_GRD_HIST 테이블 컬럼 목록과 각 컬럼 설명을 알려 주세요."

# 실습용 가상 정의서 발췌 (실제 테이블이 아닙니다)
SCHEMA = """테이블: TB_ADW_CUST_GRD_HIST (고객 등급 변경 이력)
- CUST_ID      VARCHAR2(20)  고객 식별자(가명처리)
- GRD_CD       CHAR(2)       고객 등급 코드
- VALID_FROM   DATE          유효 시작일
- VALID_TO     DATE          유효 종료일 (현재 유효 행은 9999-12-31)
- LOAD_DTTM    DATE          적재 일시"""

VARIANTS = {
    "A. 질문만": ("", QUESTION),
    "B. 모르면 모른다고": (
        "사내 시스템에 대해 확실히 모르는 내용은 추측하지 말고 '확인할 수 없습니다'라고 답하세요.",
        QUESTION,
    ),
    "C. 정의서 + 근거 지시": (
        "아래 정의서에 있는 내용만으로 답하세요. 정의서에 없는 컬럼이나 의미는 '정의서에 없음'이라고 쓰세요.",
        f"<정의서>\n{SCHEMA}\n</정의서>\n\n{QUESTION} 등급 변경 사유 컬럼도 있나요?",
    ),
}


def ask(system: str, user: str) -> str:
    kwargs = {"system": system} if system else {}
    msg = client.messages.create(
        model=MODEL, max_tokens=1024,
        messages=[{"role": "user", "content": user}], **kwargs,
    )
    return "".join(b.text for b in msg.content if b.type == "text")


for name, (system, user) in VARIANTS.items():
    print(f"\n===== {name} =====")
    print(ask(system, user))
