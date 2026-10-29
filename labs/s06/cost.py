"""5단계: 토큰 · 비용 계산.  python cost.py"""
import os

from anthropic import Anthropic

from extract import SAMPLE, SYSTEM

client = Anthropic()
MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")

# 1백만 토큰당 달러 — 2026-10 기준 Sonnet 5.5 값. 반드시 공식 가격 페이지에서 최신 값을 확인하세요
PRICE_IN = float(os.getenv("PRICE_IN_PER_MTOK", "2.0"))
PRICE_OUT = float(os.getenv("PRICE_OUT_PER_MTOK", "10.0"))

# 보내기 전에 입력 토큰 수를 셉니다 (호출 비용 없음)
count = client.messages.count_tokens(
    model=MODEL,
    system=SYSTEM,
    messages=[{"role": "user", "content": SAMPLE}],
)
print("입력 토큰(예상):", count.input_tokens)

# 실제 호출 후에는 usage 로 정확한 값을 받습니다
msg = client.messages.create(model=MODEL, max_tokens=2048, system=SYSTEM,
                             output_config={"effort": "low"},
                             messages=[{"role": "user", "content": SAMPLE + "\n\n한 줄로 요약하세요."}])
# 주의: output_tokens 에는 thinking 토큰도 포함되어 출력 단가로 과금됩니다
u = msg.usage
cost = u.input_tokens / 1e6 * PRICE_IN + u.output_tokens / 1e6 * PRICE_OUT
print(f"입력 {u.input_tokens} · 출력 {u.output_tokens} 토큰 → ${cost:.5f}")

# 월 비용 추정: 하루 300통 × 22일
monthly = cost * 300 * 22
print(f"하루 300통, 월 22일 기준 약 ${monthly:.2f}/월 (환율 1,400원이면 약 {monthly * 1400:,.0f}원)")
