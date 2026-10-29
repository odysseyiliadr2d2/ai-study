"""temperature 실험 — python temperature.py
최신 Claude 모델(예: Sonnet 5.5)은 temperature 를 보내면 400 오류를 돌려주므로,
temperature 를 받는 로컬 Ollama 모델로 실험합니다 (OpenAI 호환 엔드포인트).
"""
import os
from collections import Counter

from openai import OpenAI

MODEL = os.getenv("OLLAMA_MODEL", "gemma4:e4b")
# Ollama 는 API 키를 검사하지 않지만 클라이언트가 값을 요구하므로 아무 문자열이나 넣습니다
client = OpenAI(base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434/v1"), api_key="ollama")

PROMPT = "정보계 야간 배치 장애 보고서의 제목을 15자 이내로 하나만 지어 주세요. 제목만 출력하세요."
RUNS = 5


def sample(temperature: float) -> str:
    resp = client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": PROMPT}],
        temperature=temperature,
        max_tokens=40,
    )
    return resp.choices[0].message.content.strip()


for t in (0.0, 0.7, 1.5):
    answers = [sample(t) for _ in range(RUNS)]
    kinds = Counter(answers)
    print(f"\n[temperature={t}] 서로 다른 답 {len(kinds)}개 / {RUNS}회")
    for text, n in kinds.most_common():
        print(f"  {n}회  {text}")
