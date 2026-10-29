"""실패 유형별로 묶어 개선 우선순위표 만들기 — python report.py
evaluate.py(eval_results.jsonl) 와 judge.py(judge_results.jsonl) 를 먼저 실행합니다.
"""
import json
from collections import Counter

ACTION = {
    "검색 실패": "용어사전·동의어 보강, 청크 제목 확인, BM25 가중, 문서 누락 여부 확인",
    "생성 실패": "프롬프트 지시 보강, 리랭커 top 조정, 근거 순서·길이 확인",
    "거절 실패": "리랭커 점수 하한선 올리기, 거절 문구 명시, 범위 밖 예시 추가",
}


def load(path: str) -> dict:
    return {r["id"]: r for r in map(json.loads, open(path, encoding="utf-8"))}


def classify(j: dict, e: dict | None) -> str | None:
    if j["correct"] == 2:
        return None
    if not j["answerable"]:
        return "거절 실패"                    # 문서에 없는데 답을 지어냄
    if e is None or e["rank"] is None or e["rank"] > 4:
        return "검색 실패"                    # 정답 근거가 상위 4개 안에 없음
    return "생성 실패"                        # 근거는 있었는데 답이 틀림


def main():
    judged, retrieved = load("judge_results.jsonl"), load("eval_results.jsonl")
    fails = Counter()
    for qid, j in judged.items():
        kind = classify(j, retrieved.get(qid))
        if kind:
            fails[(kind, j["category"])] += 1
    total = len(judged)
    correct = sum(j["correct"] == 2 for j in judged.values())
    print(f"전체 {total}문항 · 정답 {correct} ({correct / total:.0%})\n")
    print("| 순위 | 실패 유형 | 업무 분류 | 건수 | 먼저 할 일 |")
    print("|---|---|---|---|---|")
    for n, ((kind, cat), cnt) in enumerate(fails.most_common(), 1):
        print(f"| {n} | {kind} | {cat} | {cnt} | {ACTION[kind]} |")


if __name__ == "__main__":
    main()
