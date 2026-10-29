"""(b) LLM 제로샷: 학습 없이 유형 설명만 주고 분류합니다 — python zero_shot.py"""
import os

from anthropic import Anthropic

MODEL = os.getenv("CLAUDE_MODEL", "claude-haiku-5-5")   # 대량 분류는 작고 빠른 모델이 맞습니다
LABELS = {
    "배치장애": "야간·정기 배치, ETL, 적재 작업이 실패하거나 지연됨",
    "데이터문의": "숫자·코드·컬럼 값이 이상하거나 의미를 묻는 질문",
    "권한요청": "계정·조회·접속 권한의 생성, 추가, 회수",
    "보고서요청": "새 보고서·대시보드 또는 기존 보고서 변경 요청",
}

client = Anthropic()  # ANTHROPIC_API_KEY 환경변수 사용

SYSTEM = "당신은 증권사 정보계 운영팀의 티켓 분류 담당입니다.\n" + "\n".join(
    f"- {k}: {v}" for k, v in LABELS.items()
) + "\n티켓 문장을 읽고 위 유형 이름 중 하나만 출력하세요. 다른 말은 쓰지 않습니다."


def text_of(msg) -> str:
    """응답 content 에는 thinking 블록이 먼저 올 수 있으므로 text 블록만 모읍니다."""
    return "".join(b.text for b in msg.content if b.type == "text").strip()


def classify(ticket: str) -> str:
    msg = client.messages.create(
        model=MODEL,
        max_tokens=1024,
        system=SYSTEM,
        messages=[{"role": "user", "content": ticket}],
    )
    answer = text_of(msg)
    # LLM 출력은 반드시 허용 목록으로 검증합니다
    return answer if answer in LABELS else f"분류불가({answer[:20]})"


if __name__ == "__main__":
    for t in [
        "새벽 적재 작업이 ORA-00942 오류로 실패했습니다",
        "지점별 예금 잔고를 주간 단위로 보는 화면이 필요합니다",
        "외부 감사인이 다음 주까지 DW 조회만 할 수 있게 해 주세요",
    ]:
        print(f"{classify(t):8s} {t}")
