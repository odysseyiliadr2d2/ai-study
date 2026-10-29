"""나쁜 프롬프트 vs 고친 프롬프트를 나란히 비교 — python compare.py
같은 로그를 두 프롬프트로 3번씩 보내 결과 길이와 모양이 얼마나 흔들리는지 봅니다.
"""
import os

from anthropic import Anthropic

from templates import render

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
RUNS = 3
client = Anthropic()
log = open("sample_log.txt", encoding="utf-8").read()

BAD = ("", f"이 로그 요약해줘\n{log}")
GOOD = render("log_summary", base_date="2026-10-28", log=log)


def call(system: str, user: str) -> str:
    kwargs = {"system": system} if system else {}
    msg = client.messages.create(model=MODEL, max_tokens=1500,
                                 messages=[{"role": "user", "content": user}], **kwargs)
    return "".join(b.text for b in msg.content if b.type == "text")


def shape(text: str) -> str:
    """결과의 모양을 숫자로: 글자 수, 줄 수, 표 줄 수, 'ORA-' 언급 여부"""
    lines = text.splitlines()
    table = sum(1 for ln in lines if ln.strip().startswith("|"))
    return f"{len(text):>5}자 {len(lines):>3}줄 표{table:>2}줄 ORA코드={'O' if 'ORA-01653' in text else 'X'}"


for label, (system, user) in (("나쁜 프롬프트", BAD), ("고친 프롬프트", GOOD)):
    print(f"\n========== {label} ==========")
    outputs = [call(system, user) for _ in range(RUNS)]
    for i, out in enumerate(outputs, 1):
        print(f"[{i}회] {shape(out)}")
    print("----- 1회차 원문 -----")
    print(outputs[0])
