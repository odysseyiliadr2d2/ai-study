"""1단계: 첫 호출 — 메시지 구조와 응답 읽기.  python first_call.py"""
import os

from anthropic import Anthropic

client = Anthropic()  # ANTHROPIC_API_KEY 환경변수 사용
MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")


def text_of(msg) -> str:
    """응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 골라 잇습니다."""
    return "".join(b.text for b in msg.content if b.type == "text")


msg = client.messages.create(
    model=MODEL,
    max_tokens=2048,                     # 생각(thinking) + 답을 합친 상한 (넘으면 stop_reason == "max_tokens")
    output_config={"effort": "low"},     # 간단한 일은 생각을 줄여 빠르고 싸게
    system="당신은 SI 회사 고객지원팀의 분류 담당자입니다. 한 문장으로 답합니다.",
    messages=[
        {"role": "user", "content": "로그인이 안 됩니다. 비밀번호를 바꿔도 같아요. 어느 팀에 물어봐야 하나요?"},
    ],
)

print("블록 종류:", [b.type for b in msg.content])   # 예: ['thinking', 'text']
print("답변:", text_of(msg))
print("stop_reason:", msg.stop_reason)
print("토큰:", msg.usage.input_tokens, "→", msg.usage.output_tokens)

# 대화를 이어 가려면 이전 답을 assistant 로 넣고 다시 보냅니다 (API 는 상태를 기억하지 않음)
follow = client.messages.create(
    model=MODEL,
    max_tokens=2048,
    output_config={"effort": "low"},
    messages=[
        {"role": "user", "content": "로그인이 안 됩니다. 어느 팀에 물어봐야 하나요?"},
        {"role": "assistant", "content": text_of(msg)},
        {"role": "user", "content": "그 팀에 보낼 문의 제목을 한 줄로 써 주세요."},
    ],
)
print("후속:", text_of(follow))
