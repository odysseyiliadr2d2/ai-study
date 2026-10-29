"""리랭킹: 후보 청크를 cross-encoder 로 다시 채점 — python rerank.py "질문" """
import os
import sys

from sentence_transformers import CrossEncoder

from hybrid import hybrid_search

# 다국어(한국어 포함) 리랭커. Apache-2.0. 폐쇄망이면 모델 폴더 경로를 넣습니다.
RERANK_MODEL = os.getenv("RERANK_MODEL", "BAAI/bge-reranker-v2-m3")
reranker = CrossEncoder(RERANK_MODEL, max_length=512)


def rerank(question: str, candidates: list[dict], top: int = 4) -> list[tuple[float, dict]]:
    """질문과 청크를 한 쌍으로 모델에 넣어 관련도를 0~1로 받습니다 (출력 1개 모델은 시그모이드 적용)."""
    if not candidates:
        return []
    ranked = reranker.rank(question, [c["text"] for c in candidates], top_k=top)
    return [(float(r["score"]), candidates[r["corpus_id"]]) for r in ranked]


if __name__ == "__main__":
    q = " ".join(sys.argv[1:]) or "배치가 30분 넘게 안 돌아오면 누구한테 전화하나요?"
    for score, r in rerank(q, hybrid_search(q, 20)):
        print(f"{score:.3f}  {r['doc']} › {r['section']}")
