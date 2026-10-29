"""업무 후보를 규칙 · 고전 ML · LLM 중 어디에 맞는지 판정 — python decide.py
업무마다 질문 다섯 개에 답을 적으면 방식과 이유, 검증 방법을 표로 출력합니다. 외부 패키지 없음.
"""
from dataclasses import dataclass


@dataclass
class Task:
    name: str
    rule_writable: bool      # 판단 기준을 규칙(SQL·if)으로 빠짐없이 쓸 수 있나
    labeled_rows: int        # 정답(라벨)이 붙은 과거 데이터 건수
    free_text_input: bool    # 입력이 자유 문장(메일·문의·문서)인가
    daily_volume: int        # 하루 처리 건수
    exact_answer: bool       # 정답이 하나로 정해지는 일인가 (금액·건수 검증 등)


def decide(t: Task) -> tuple[str, str, str]:
    """(방식, 이유, 검증 방법)"""
    if t.rule_writable or t.exact_answer:
        return ("규칙·SQL", "기준을 규칙으로 쓸 수 있고 결과가 항상 같아야 함",
                "원천 대비 건수·합계 대사, 규칙별 단위 테스트")
    if not t.free_text_input and t.labeled_rows >= 1_000:
        return ("고전 ML", f"숫자·코드 입력이고 라벨 {t.labeled_rows:,}건이 있음",
                "시간 순으로 나눈 평가셋 정확도, 월별 성능 추적")
    if t.free_text_input and t.labeled_rows >= 5_000 and t.daily_volume >= 50_000:
        return ("고전 ML (LLM과 비교 후)", "대량 처리라 건당 비용·지연이 중요하고 라벨이 충분함",
                "같은 평가셋으로 LLM과 정확도·비용 비교")
    return ("LLM", "자유 문장 입력이고 라벨이 적어 학습보다 지시문으로 시작하는 편이 빠름",
            "허용 목록 검증 + 담당자 검수, 고친 결과를 라벨로 축적")


TASKS = [
    Task("일별 적재 건수·합계 검증", True, 0, False, 300, True),
    Task("영업점별 다음 달 신규 계좌 수 예측", False, 36 * 120, False, 1, False),
    Task("운영 메일함 문의 유형 분류·배정", False, 0, True, 150, False),
    Task("콜센터 상담 메모 유형 분류", False, 80_000, True, 60_000, False),
]

if __name__ == "__main__":
    print(f"{'업무':24s} | {'방식':22s} | 이유 | 검증 방법")
    for t in TASKS:
        how, why, check = decide(t)
        print(f"{t.name:24s} | {how:22s} | {why} | {check}")
