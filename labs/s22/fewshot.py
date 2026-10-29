"""1·2단계: 학습 없이 '비슷한 옛 보고서'를 예시로 붙이는 동적 few-shot
python fewshot.py "새 장애 메모"   (사내 OpenAI 호환 서버 사용, 세션 21)

LoRA 를 학습하기 전에 이 방식으로 평가셋을 먼저 돌려 봅니다.
"""
import json
import os
import sys

import numpy as np
from openai import OpenAI
from sentence_transformers import SentenceTransformer

client = OpenAI(base_url=os.getenv("LLM_BASE_URL", "http://localhost:8000/v1"),
                api_key=os.getenv("LLM_API_KEY", "change-me"))
MODEL = os.getenv("LLM_MODEL", "qwen3.5-9b")
emb = SentenceTransformer("intfloat/multilingual-e5-small")

# 과거 보고서(원문 메모 → 최종 요약) 쌍. train.jsonl 을 그대로 재사용합니다.
pairs = [json.loads(line)["messages"] for line in open("train.jsonl", encoding="utf-8") if line.strip()]
memos = [m[-2]["content"] for m in pairs]
vecs = emb.encode([f"passage: {t}" for t in memos], normalize_embeddings=True)

RULES = ("사내 장애 보고서 문체로 요약합니다. 반드시 '■ 개요 / ■ 원인 / ■ 조치 / ■ 영향 / ■ 후속' "
         "다섯 줄로 쓰고, 메모에 없는 사실은 쓰지 않습니다. 시각은 HH:MM 으로 씁니다.")


def summarize(memo: str, k: int = 3) -> str:
    q = emb.encode([f"query: {memo}"], normalize_embeddings=True)[0]
    top = np.argsort(-(vecs @ q))[:k]
    shots = []
    for i in top:                                  # 비슷한 예시 k 개를 대화로 붙임
        shots += [{"role": "user", "content": pairs[i][-2]["content"]},
                  {"role": "assistant", "content": pairs[i][-1]["content"]}]
    resp = client.chat.completions.create(
        model=MODEL, temperature=0, max_tokens=400,
        messages=[{"role": "system", "content": RULES}, *shots, {"role": "user", "content": memo}])
    return resp.choices[0].message.content


if __name__ == "__main__":
    print(summarize(" ".join(sys.argv[1:]) or "14:05 주문 원장 적재 실패. 디스크 사용률 98%. 로그 정리 후 14:40 재처리 완료."))
