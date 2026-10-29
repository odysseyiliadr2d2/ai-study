"""폐쇄망 팀의 모델 선택 정책을 코드로 — python model_policy.py
업무(데이터 등급 · 필요 성능)에 따라 어느 엔드포인트로 보낼지 정하고, 이유를 함께 돌려줍니다.
엔드포인트 주소와 모델 이름은 예시입니다. 실제 값은 사내 게이트웨이 담당자에게 받아 바꿉니다.
"""
from dataclasses import dataclass

# 모든 엔드포인트는 OpenAI 호환 API 로 통일 → 호출 코드는 base_url 과 model 만 바꿉니다
ENDPOINTS = {
    "internal-small": {"base_url": "http://llm-gw.internal:8080/v1", "model": "gemma4:e4b",
                       "where": "사내 GPU", "note": "분류·요약·초안"},
    "internal-large": {"base_url": "http://llm-gw.internal:8080/v1", "model": "gpt-oss-120b",
                       "where": "사내 GPU", "note": "SQL 리뷰·긴 문서"},
    "external-api":   {"base_url": "http://llm-gw.internal:8080/external/v1", "model": "claude-sonnet-5-5",
                       "where": "외부(계약)", "note": "게이트웨이가 마스킹 후 중계"},
}

GRADES = ("공개", "내부", "기밀")


@dataclass
class Job:
    name: str
    grade: str               # 공개 · 내부 · 기밀
    needs_top_model: bool    # 사내 모델 평가 점수가 기준 미달이었나
    approved_external: bool  # 업무 × 데이터 등급 단위로 외부 API 승인을 받았나


def route(job: Job) -> tuple[str, str]:
    if job.grade not in GRADES:
        raise ValueError(f"{job.name}: 데이터 등급을 먼저 정하세요 ({'/'.join(GRADES)})")
    if job.grade == "기밀":
        return "internal-large" if job.needs_top_model else "internal-small", "기밀 데이터는 사내 모델만"
    if job.needs_top_model and job.approved_external:
        return "external-api", f"{job.grade} 등급 · 승인됨 · 사내 모델 평가 미달"
    if job.needs_top_model:
        return "internal-large", "외부 승인 전이므로 사내 대형 모델로 대체"
    return "internal-small", "사내 소형 모델로 충분"


JOBS = [
    Job("장애 로그 요약", "내부", False, False),
    Job("고객 문의 메일 분류", "기밀", False, False),
    Job("ETL 설계서 기반 SQL 리뷰", "내부", True, False),
    Job("공개 규제 문서 요약·비교", "공개", True, True),
]

if __name__ == "__main__":
    print(f"{'업무':18s} {'등급':4s} → {'엔드포인트':15s} {'모델':18s} 이유")
    for j in JOBS:
        key, why = route(j)
        ep = ENDPOINTS[key]
        print(f"{j.name:18s} {j.grade:4s} → {key:15s} {ep['model']:18s} {why} ({ep['where']})")

    # 호출 예 (게이트웨이가 있을 때): OpenAI SDK 하나로 세 엔드포인트를 모두 부릅니다
    # import os
    # from openai import OpenAI
    # ep = ENDPOINTS[route(JOBS[0])[0]]
    # client = OpenAI(base_url=ep["base_url"], api_key=os.environ["LLM_GW_KEY"])
    # client.chat.completions.create(model=ep["model"], messages=[{"role": "user", "content": "..."}])
