"""1단계: 임베딩과 코사인 유사도 감 잡기 — python cosine_demo.py"""
import numpy as np
from sentence_transformers import SentenceTransformer

model = SentenceTransformer("intfloat/multilingual-e5-small")   # 처음 한 번 내려받음 (약 0.5GB)

docs = [
    "고객번호: 고객을 식별하는 고유 번호 (CUST_NO)",
    "고객 식별 번호는 계좌 개설 시 부여됩니다",
    "야간 배치가 실패하면 STG 적재부터 재처리합니다",
    "월말 기준 고객 등급 변경 이력을 보관하는 테이블",
]
query = "손님을 구별하는 번호가 뭐야?"

# e5 규칙: 저장할 문서는 "passage: ", 질문은 "query: " 를 앞에 붙입니다
d = model.encode([f"passage: {t}" for t in docs], normalize_embeddings=True)
q = model.encode(f"query: {query}", normalize_embeddings=True)
print("벡터 차원:", d.shape[1])           # 384


def cosine(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


for text, vec in zip(docs, d):
    # 정규화했으므로 내적(np.dot) 과 코사인 유사도가 같습니다
    print(f"{cosine(q, vec):.3f}  {np.dot(q, vec):.3f}  {text}")
# 키워드 '손님'은 문서에 없지만 고객번호 관련 두 문장이 위로 올라옵니다
