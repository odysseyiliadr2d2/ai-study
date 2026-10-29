"""4단계: 스트리밍 — 답장 초안을 글자가 생기는 대로 보여 주기.  python stream_reply.py"""
import os

from anthropic import Anthropic

client = Anthropic()
MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")

with client.messages.stream(
    model=MODEL,
    max_tokens=4096,
    output_config={"effort": "low"},
    system="당신은 SI 회사 고객지원 담당자입니다. 정중한 합니다체로 5문장 이내 답장 초안을 씁니다.",
    messages=[{"role": "user", "content": "월별 정산 보고서 화면 ERR-5031 오류 문의에 대한 1차 답장 초안을 써 주세요."}],
) as stream:
    for text in stream.text_stream:
        print(text, end="", flush=True)      # text_stream 은 text 조각만 내보냅니다 (thinking 제외)
    final = stream.get_final_message()       # 끝나면 전체 메시지와 usage 를 받습니다

print()
print("stop_reason:", final.stop_reason, "/ 출력 토큰:", final.usage.output_tokens)
