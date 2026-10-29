"""같은 루프를 OpenAI Responses API 로 — 비교용 최소 구현 (읽기 도구만).

차이점: 도구 스키마 키가 parameters, 호출은 output 의 function_call 항목,
결과는 function_call_output(call_id, output) 으로 돌려줍니다. 인자는 JSON 문자열입니다.
"""
import json
import os
import sys

from openai import OpenAI

from ops_tools import FUNCS, READ_TOOLS, ToolError
from tool_specs import TOOLS

client = OpenAI()  # OPENAI_API_KEY
MODEL = os.getenv("OPENAI_MODEL", "gpt-5.5")
OA_TOOLS = [{"type": "function", "name": t["name"], "description": t["description"],
             "parameters": t["input_schema"]} for t in TOOLS if t["name"] in READ_TOOLS]


def run(question: str, max_steps: int = 8) -> str:
    items = [{"role": "user", "content": question}]
    for _ in range(max_steps):
        resp = client.responses.create(model=MODEL, instructions="정보계 배치 운영 에이전트. 도구로 확인 후 답합니다.",
                                       input=items, tools=OA_TOOLS)
        calls = [o for o in resp.output if o.type == "function_call"]
        if not calls:
            return resp.output_text
        items += resp.output                       # 모델 출력(추론 항목 포함)을 그대로 이어 붙임
        for c in calls:
            try:
                out = FUNCS[c.name](**json.loads(c.arguments))
            except (ToolError, TypeError, KeyError) as e:
                out = f"오류: {e}"
            items.append({"type": "function_call_output", "call_id": c.call_id, "output": out})
    return "최대 반복 도달"


if __name__ == "__main__":
    print(run(" ".join(sys.argv[1:]) or "어젯밤 배치 왜 실패했어?"))
