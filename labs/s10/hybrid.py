"""하이브리드 검색: BM25 + 벡터를 RRF로 합치기 — python hybrid.py "질문"
세션 9 의 chunk.py·index.py 로 sample/ 을 색인한 폴더(index.npy, index.json 이 있는 곳)에서 실행합니다.
"""
import json
import re
import sys

import numpy as np
from rank_bm25 import BM25Okapi
from sentence_transformers import SentenceTransformer

EMB_MODEL = "intfloat/multilingual-e5-small"
RRF_K = 60          # RRF 상수. 원 논문(Cormack 외, 2009) 값
POOL = 50           # 각 검색기에서 가져올 후보 수

vecs = np.load("index.npy")
records = json.load(open("index.json", encoding="utf-8"))
embedder = SentenceTransformer(EMB_MODEL)


def tokenize(text: str) -> list[str]:
    """형태소 분석기 없이 쓰는 간단한 토큰화.
    영문·숫자·밑줄 단어(테이블명, 코드값)는 통째로, 한글은 2글자씩 잘라 조사 차이를 흡수합니다."""
    text = text.lower()
    tokens = re.findall(r"[a-z0-9_]+", text)
    for word in re.findall(r"[가-힣]+", text):
        tokens += [word[i:i + 2] for i in range(len(word) - 1)] or [word]
    return tokens


bm25 = BM25Okapi([tokenize(r["text"]) for r in records])


def vector_rank(question: str, n: int = POOL) -> list[int]:
    q = embedder.encode([f"query: {question}"], normalize_embeddings=True)[0]
    return [int(i) for i in np.argsort(-(vecs @ q))[:n]]


def bm25_rank(question: str, n: int = POOL) -> list[int]:
    scores = bm25.get_scores(tokenize(question))
    return [int(i) for i in np.argsort(-scores)[:n] if scores[i] > 0]


def rrf(rankings: list[list[int]], k: int = RRF_K) -> list[int]:
    """순위만 보고 합칩니다: 점수 = Σ 1 / (k + 순위). 점수 척도가 달라도 섞을 수 있습니다."""
    fused: dict[int, float] = {}
    for ranking in rankings:
        for rank, idx in enumerate(ranking, 1):
            fused[idx] = fused.get(idx, 0.0) + 1.0 / (k + rank)
    return sorted(fused, key=fused.get, reverse=True)


def hybrid_ids(question: str, n: int = 20, where=None) -> list[int]:
    ids = rrf([vector_rank(question), bm25_rank(question)])
    if where:                                  # 메타데이터 필터 (예: 특정 문서만)
        ids = [i for i in ids if where(records[i])]
    return ids[:n]


def hybrid_search(question: str, n: int = 20, where=None) -> list[dict]:
    return [records[i] for i in hybrid_ids(question, n, where)]


def vector_search(question: str, n: int = 20) -> list[dict]:
    return [records[i] for i in vector_rank(question, n)]


if __name__ == "__main__":
    q = " ".join(sys.argv[1:]) or "Terrastream 콘솔에서 재실행"
    print("벡터만 :", [f"{r['doc']}#{r['section']}" for r in vector_search(q, 5)])
    print("하이브리드:", [f"{r['doc']}#{r['section']}" for r in hybrid_search(q, 5)])
