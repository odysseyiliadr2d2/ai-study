"""3·4단계: 검색하고 근거와 함께 답하기 — python ask.py "질문" """
import json
import os
import sys

import numpy as np
from anthropic import Anthropic
from sentence_transformers import SentenceTransformer

EMB_MODEL = "intfloat/multilingual-e5-small"
LLM_MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
TOP_K = 4

vecs = np.load("index.npy")
records = json.load(open("index.json", encoding="utf-8"))
embedder = SentenceTransformer(EMB_MODEL)
client = Anthropic()  # ANTHROPIC_API_KEY 환경변수 사용


def search(question: str, k: int = TOP_K):
    q = embedder.encode([f"query: {question}"], normalize_embeddings=True)[0]
    scores = vecs @ q                      # 정규화했으므로 내적 = 코사인 유사도
    top = np.argsort(-scores)[:k]
    return [(float(scores[i]), records[i]) for i in top]


SYSTEM = """당신은 사내 문서 Q&A 도우미입니다.
- 반드시 <근거> 안의 내용만으로 답합니다.
- 문장 끝에 사용한 근거 번호를 [1]처럼 붙입니다.
- 근거로 답할 수 없으면 "문서에서 찾지 못했습니다"라고만 답합니다."""


def answer(question: str):
    hits = search(question)
    context = "\n\n".join(
        f"[{n}] ({r['doc']} · {r['section']})\n{r['text']}" for n, (_, r) in enumerate(hits, 1)
    )
    msg = client.messages.create(
        model=LLM_MODEL,
        max_tokens=800,
        system=SYSTEM,
        messages=[{"role": "user", "content": f"<근거>\n{context}\n</근거>\n\n질문: {question}"}],
    )
    print("".join(b.text for b in msg.content if b.type == "text"))   # thinking 블록이 앞에 올 수 있어 type 으로 고름
    print("\n— 출처")
    for n, (score, r) in enumerate(hits, 1):
        print(f"[{n}] {r['doc']} › {r['section']} (유사도 {score:.2f})")


if __name__ == "__main__":
    answer(" ".join(sys.argv[1:]) or "야간 배치가 실패하면 누구에게 연락하나요?")
