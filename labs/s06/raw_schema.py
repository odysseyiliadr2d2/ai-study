"""참고: SDK 헬퍼 없이 JSON Schema 를 직접 보내는 방법.  python raw_schema.py"""
import json
import os

from anthropic import Anthropic

from schema import Inquiry

client = Anthropic()
schema = {
    "type": "object",
    "properties": {
        "category": {"type": "string", "enum": ["계정·로그인", "요금·계약", "장애·오류", "기능 요청", "데이터·보고서", "기타"]},
        "urgency": {"type": "string", "enum": ["높음", "보통", "낮음"]},
        "summary": {"type": "string"},
        "system_name": {"anyOf": [{"type": "string"}, {"type": "null"}]},
        "error_code": {"anyOf": [{"type": "string"}, {"type": "null"}]},
        "requested_by_date": {"anyOf": [{"type": "string", "format": "date"}, {"type": "null"}]},
        "confidence": {"type": "number"},
    },
    "required": ["category", "urgency", "summary", "system_name", "error_code", "requested_by_date", "confidence"],
    "additionalProperties": False,   # 객체마다 false 필수
}

msg = client.messages.create(
    model=os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5"),
    max_tokens=4096,
    messages=[{"role": "user", "content": "문의: 로그인 화면에서 계속 튕깁니다. 분류하세요."}],
    output_config={"effort": "low", "format": {"type": "json_schema", "schema": schema}},
)
# content[0] 은 thinking 블록일 수 있으므로 text 블록만 모읍니다
text = "".join(b.text for b in msg.content if b.type == "text")
data = json.loads(text)                      # 스키마에 맞는 JSON
print(Inquiry.model_validate(data))          # 업무 규칙 검증은 여전히 pydantic 으로
