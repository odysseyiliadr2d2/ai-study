"""검색 평가: recall@k 와 MRR 을 세 가지 설정 × 질문 유형별로 비교 — python evaluate.py eval.jsonl"""
import json
import sys
from collections import defaultdict

from hybrid import hybrid_search, vector_search
from rerank import rerank

K = 4


def key(r: dict) -> str:
    return f"{r['doc']}#{r['section']}"


def dedup(keys: list[str]) -> list[str]:
    """같은 섹션의 청크가 여러 개면 첫 번째만 셉니다 (근거 단위가 섹션이므로)."""
    return list(dict.fromkeys(keys))


RETRIEVERS = {
    "vector": lambda q: dedup([key(r) for r in vector_search(q, 20)]),
    "hybrid": lambda q: dedup([key(r) for r in hybrid_search(q, 20)]),
    "hybrid+rerank": lambda q: dedup([key(r) for _, r in rerank(q, hybrid_search(q, 20), top=10)]),
}


def recall_at_k(ranked: list[str], gold: set[str], k: int = K) -> float:
    """정답 근거 중 상위 k개 안에 들어온 비율."""
    return len(gold & set(ranked[:k])) / len(gold)


def reciprocal_rank(ranked: list[str], gold: set[str]) -> float:
    """첫 정답 근거의 순위 역수. 1위면 1.0, 3위면 0.33, 못 찾으면 0."""
    for i, k in enumerate(ranked, 1):
        if k in gold:
            return 1.0 / i
    return 0.0


def main(path: str):
    items = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    items = [it for it in items if it["answerable"] and it["evidence"]]   # 검색 평가는 답이 있는 문항만
    types = sorted({it["type"] for it in items})
    rows = []
    print(f"{'설정':14s} {'전체':>14s} " + " ".join(f"{t:>14s}" for t in types))
    for name, retrieve in RETRIEVERS.items():
        rec, rr = defaultdict(list), defaultdict(list)
        for it in items:
            ranked, gold = retrieve(it["question"]), set(it["evidence"])
            for g in ("전체", it["type"]):
                rec[g].append(recall_at_k(ranked, gold))
                rr[g].append(reciprocal_rank(ranked, gold))
            if name == "hybrid+rerank":
                hit = next((i for i, k in enumerate(ranked, 1) if k in gold), None)
                rows.append({"id": it["id"], "type": it["type"], "category": it["category"],
                             "rank": hit, "top": ranked[:K]})
        cell = lambda g: f"R{sum(rec[g]) / len(rec[g]):.2f} M{sum(rr[g]) / len(rr[g]):.2f}"
        print(f"{name:14s} {cell('전체'):>14s} " + " ".join(f"{cell(t):>14s}" for t in types))
    print(f"(R = recall@{K}, M = MRR, 답이 있는 문항 {len(items)}개)")
    with open("eval_results.jsonl", "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print("문항별 결과 → eval_results.jsonl (rank 가 비었거나 4보다 큰 문항부터 원인을 봅니다)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "eval.jsonl")
