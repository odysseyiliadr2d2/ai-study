"""사내 vLLM 서버를 OpenAI SDK 로 호출 — python client.py "질문"

LLM_BASE_URL, LLM_API_KEY, LLM_MODEL 환경변수로 대상만 바꾸면
vLLM · Ollama · llama.cpp 어느 쪽이든 같은 코드로 부를 수 있습니다.
"""
import os
import sys

from openai import OpenAI

client = OpenAI(
    base_url=os.getenv("LLM_BASE_URL", "http://localhost:8000/v1"),
    api_key=os.getenv("LLM_API_KEY", "change-me"),
)
MODEL = os.getenv("LLM_MODEL", "qwen3.5-9b")   # --served-model-name 과 같게

SYSTEM = "당신은 증권사 정보계 운영 문서를 요약하는 도우미입니다. 한국어로, 5줄 이내로 답합니다."


def ask(question: str) -> str:
    resp = client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "system", "content": SYSTEM},
                  {"role": "user", "content": question}],
        temperature=0.2,
        max_tokens=600,
    )
    return resp.choices[0].message.content


if __name__ == "__main__":
    print(ask(" ".join(sys.argv[1:]) or "야간 배치 재처리 절차를 신규 담당자에게 설명하듯 요약해 주세요."))
