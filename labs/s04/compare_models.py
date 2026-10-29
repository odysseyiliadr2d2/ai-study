"""같은 프롬프트를 Claude · OpenAI · 로컬(Ollama) 에 보내 답 · 지연 · 토큰을 비교 — python compare_models.py
API 키가 없거나 로컬 서버가 꺼져 있으면 그 항목은 건너뜁니다.
"""
import os
import time

PROMPT = "Oracle 에서 파티션 테이블의 오래된 파티션을 정리할 때 주의할 점 3가지를 한 줄씩 알려 주세요."


def ask_claude(prompt: str):
    from anthropic import Anthropic
    client = Anthropic()
    msg = client.messages.create(
        model=os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5"), max_tokens=1024,
        messages=[{"role": "user", "content": prompt}],
    )
    text = "".join(b.text for b in msg.content if b.type == "text")
    return text, msg.usage.input_tokens, msg.usage.output_tokens


def ask_openai(prompt: str):
    from openai import OpenAI
    client = OpenAI()
    resp = client.responses.create(model=os.getenv("OPENAI_MODEL", "gpt-5.5"), input=prompt)
    return resp.output_text, resp.usage.input_tokens, resp.usage.output_tokens


def ask_local(prompt: str):
    from openai import OpenAI
    # Ollama 의 OpenAI 호환 엔드포인트. 키는 검사하지 않지만 값이 필요합니다.
    client = OpenAI(base_url=os.getenv("LOCAL_BASE_URL", "http://localhost:11434/v1"), api_key="ollama")
    resp = client.chat.completions.create(
        model=os.getenv("LOCAL_MODEL", "gemma4:e4b"),
        messages=[{"role": "user", "content": prompt}],
    )
    usage = resp.usage
    return (resp.choices[0].message.content,
            getattr(usage, "prompt_tokens", None), getattr(usage, "completion_tokens", None))


TARGETS = [
    ("Claude", ask_claude, "ANTHROPIC_API_KEY"),
    ("OpenAI", ask_openai, "OPENAI_API_KEY"),
    ("로컬(Ollama)", ask_local, None),
]

rows = []
for name, fn, key in TARGETS:
    if key and not os.getenv(key):
        print(f"[{name}] {key} 가 없어 건너뜁니다")
        continue
    start = time.perf_counter()
    try:
        text, tin, tout = fn(PROMPT)
    except Exception as e:      # 연결 실패, 모델 없음 등
        print(f"[{name}] 실패: {e}")
        continue
    sec = time.perf_counter() - start
    rows.append((name, sec, tin, tout))
    print(f"\n===== {name} ({sec:.1f}초) =====\n{text.strip()}")

print(f"\n{'대상':14s} {'지연(초)':>8s} {'입력 토큰':>9s} {'출력 토큰':>9s}")
for name, sec, tin, tout in rows:
    print(f"{name:14s} {sec:>8.1f} {str(tin):>9s} {str(tout):>9s}")
print("\n같은 문장이라도 모델마다 토크나이저가 달라 입력 토큰 수가 다릅니다.")
