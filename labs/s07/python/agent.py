"""에이전트 루프: 도구 호출이 끝날 때까지 반복하고 JSON 결과를 돌려줍니다 — python agent.py 로 첫 메일만 시험"""
import json
import os

from anthropic import Anthropic

from tools import TOOLS, call_tool

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
MAX_ITERATIONS = 5          # 도구 호출이 끝없이 반복되지 않게 (n8n 의 Max Iterations)
client = Anthropic(max_retries=3)   # 429·5xx·네트워크 오류는 SDK 가 지수 백오프로 재시도 (n8n 의 Retry On Fail)

SYSTEM = """당신은 SI 회사 고객지원팀의 1차 응대 담당입니다.
1. 문의를 '계정·로그인 / 요금·계약 / 장애·오류 / 기능 요청 / 기타' 중 하나로 분류합니다.
2. 답을 쓰기 전에 반드시 faq_lookup 도구로 관련 FAQ 를 찾습니다.
3. FAQ 에 근거가 있으면 그 내용으로, 없으면 "담당자가 확인 후 회신드리겠습니다"로 답장 초안을 씁니다.
4. 가격·계약 조건·장애 원인은 추측해서 쓰지 않습니다.
5. 답장은 합니다체, 5문장 이내, 서명은 '고객지원팀'.
6. 확신이 없으면 needs_human 을 true 로 둡니다.
마지막 답은 JSON 하나로만 씁니다:
{"category": "...", "urgency": "높음|보통|낮음", "summary": "...", "faq_used": "FAQ-012 또는 빈 문자열", "needs_human": false, "reply_draft": "..."}"""

REQUIRED = ("category", "urgency", "summary", "faq_used", "needs_human", "reply_draft")


def parse_result(text: str) -> dict:
    """응답 텍스트에서 JSON 을 꺼내 필수 키를 확인합니다 (n8n 의 Structured Output Parser 역할)."""
    data = json.loads(text[text.index("{"): text.rindex("}") + 1])
    missing = [k for k in REQUIRED if k not in data]
    if missing:
        raise ValueError(f"결과에 필수 키가 없습니다: {missing}")
    return data


def run_agent(mail: dict) -> tuple[dict, list[dict]]:
    """(결과 JSON, 도구 호출 기록) 을 돌려줍니다."""
    messages = [{"role": "user", "content":
                 f"다음 고객 문의 메일을 처리하세요.\n보낸 사람: {mail['from']}\n"
                 f"제목: {mail['subject']}\n본문:\n{mail['body'][:4000]}"}]
    calls = []
    for _ in range(MAX_ITERATIONS):
        # Claude 5.5: temperature 와 강제 tool_choice 는 넣지 않습니다. 도구 순서는 시스템 프롬프트로 지시
        msg = client.messages.create(model=MODEL, max_tokens=2000, system=SYSTEM,
                                     tools=TOOLS, messages=messages)
        if msg.stop_reason == "tool_use":
            messages.append({"role": "assistant", "content": msg.content})   # thinking·tool_use 블록 그대로
            results = []
            for b in msg.content:
                if b.type == "tool_use":
                    out = call_tool(b.name, b.input)
                    calls.append({"tool": b.name, "input": b.input, "output": out[:200]})
                    results.append({"type": "tool_result", "tool_use_id": b.id, "content": out})
            messages.append({"role": "user", "content": results})
            continue
        text = "".join(b.text for b in msg.content if b.type == "text")   # thinking 블록은 건너뜀
        return parse_result(text), calls
    raise RuntimeError(f"도구 호출이 {MAX_ITERATIONS}번을 넘었습니다")


if __name__ == "__main__":
    first = json.loads(open("inbox.jsonl", encoding="utf-8").readline())
    result, calls = run_agent(first)
    print(json.dumps(calls, ensure_ascii=False, indent=2))
    print(json.dumps(result, ensure_ascii=False, indent=2))
