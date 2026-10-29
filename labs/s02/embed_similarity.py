"""임베딩 코사인 유사도 — python embed_similarity.py
로컬에서 도는 다국어 임베딩 모델을 씁니다 (폐쇄망에서는 모델 폴더를 반입해 경로로 지정).
"""
import numpy as np
from sentence_transformers import SentenceTransformer

model = SentenceTransformer("intfloat/multilingual-e5-small")

query = "야간 적재 작업이 실패했을 때 대응 절차"
docs = [
    "배치 장애 발생 시 운영 당직자에게 알리고 재처리합니다",       # 뜻이 가까움
    "ETL job failure: notify the on-call operator and rerun",      # 영어지만 뜻이 가까움
    "적재 실패 건은 콘솔에서 해당 블록부터 다시 실행합니다",       # 단어는 다르지만 관련
    "신규 입사자 DW 조회 계정 신청 방법",                          # 관련 없음
    "구내식당 점심 메뉴 안내",                                      # 전혀 관련 없음
]

# e5 계열 규칙: 질문은 "query: ", 문서는 "passage: " 접두어
q = model.encode([f"query: {query}"], normalize_embeddings=True)[0]
d = model.encode([f"passage: {x}" for x in docs], normalize_embeddings=True)

print(f"벡터 차원: {d.shape[1]}")
scores = d @ q          # 길이 1로 정규화했으므로 내적 = 코사인 유사도
for s, text in sorted(zip(scores, docs), reverse=True):
    print(f"{s:.3f}  {text}")
