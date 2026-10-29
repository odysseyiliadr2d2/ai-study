"""파인튜닝 vs RAG 판단 체크리스트 — python decide.py answers.yaml

answers.yaml 예:
  name: 장애 보고서 요약
  needs_new_facts: false        # 모델이 모르는 사내 사실·최신 문서가 필요한가
  facts_change_often: false     # 그 사실이 자주 바뀌는가
  needs_citation: false         # 답에 출처가 꼭 필요한가
  format_or_tone_issue: true    # 형식·말투·분류 기준이 문제인가
  prompt_tried: true            # 프롬프트(지시+예시)로 충분히 시도해 봤는가
  prompt_score: 0.72            # 그때 평가셋 합격률 (0~1)
  target_score: 0.90
  labeled_examples: 800         # 검수된 입력-정답 쌍 수
  has_gpu: true
"""
import sys

import yaml


def decide(a: dict) -> list[str]:
    out = []
    if not a.get("prompt_tried"):
        return ["먼저 프롬프트(규칙 + 예시 3~5개)로 평가셋을 돌려 보세요. 대부분 여기서 해결됩니다."]
    if a.get("prompt_score", 0) >= a.get("target_score", 0.9):
        return ["프롬프트로 목표를 넘었습니다. 추가 작업 없이 운영하고 평가셋만 주기적으로 다시 돌립니다."]

    if a.get("needs_new_facts") or a.get("facts_change_often") or a.get("needs_citation"):
        out.append("RAG: 모르는 사실·자주 바뀌는 문서·출처 요구는 검색으로 해결합니다 (세션 9~10).")
    if a.get("format_or_tone_issue"):
        n = a.get("labeled_examples", 0)
        if n < 200:
            out.append(f"파인튜닝 보류: 검수된 예시가 {n}건뿐입니다. 200건 이상(권장 500~1,000건) 모을 때까지 "
                       "RAG 로 비슷한 예시를 찾아 프롬프트에 붙이는 방식(동적 few-shot)을 씁니다.")
        elif not a.get("has_gpu"):
            out.append("파인튜닝 후보: 데이터는 충분하지만 GPU 가 없습니다. 학습용 GPU 확보 계획부터 세웁니다.")
        else:
            out.append(f"LoRA 파인튜닝: 형식·말투 문제이고 예시 {n}건이 있으므로 작은 LoRA 로 시도합니다.")
    if not out:
        out.append("원인 분석 필요: 실패 사례 20건을 직접 읽고 '사실 부족'인지 '형식 문제'인지 분류해 보세요.")
    return out


if __name__ == "__main__":
    answers = yaml.safe_load(open(sys.argv[1] if len(sys.argv) > 1 else "answers.yaml", encoding="utf-8"))
    print(f"[{answers.get('name', '유스케이스')}]")
    for line in decide(answers):
        print("-", line)
