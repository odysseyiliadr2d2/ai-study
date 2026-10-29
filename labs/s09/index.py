"""2단계: 청크를 임베딩해 저장 — python index.py chunks.jsonl"""
import json
import sys

import numpy as np
from sentence_transformers import SentenceTransformer

# 한국어가 되는 작은 다국어 임베딩 모델 (CPU로도 동작, 사내 서버에 내려받아 쓸 수 있음)
MODEL = "intfloat/multilingual-e5-small"


def main(path: str):
    records = [json.loads(line) for line in open(path, encoding="utf-8")]
    model = SentenceTransformer(MODEL)
    # e5 계열은 문서 쪽에 "passage: " 를 붙이는 것이 규칙입니다
    vecs = model.encode([f"passage: {r['text']}" for r in records],
                        normalize_embeddings=True, batch_size=32, show_progress_bar=True)
    np.save("index.npy", vecs.astype("float32"))
    with open("index.json", "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False)
    print(f"{len(records)}개 청크 색인 완료 → index.npy, index.json")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "chunks.jsonl")
