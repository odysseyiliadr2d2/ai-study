"""FAQ 조회 도구: 에이전트에 줄 도구 스키마와 실제 실행 함수"""
import csv
import json
import re
from pathlib import Path

FAQ_PATH = Path("faq.csv")

# 모델은 description 을 읽고 언제 이 도구를 쓸지 판단합니다 (n8n Tool 노드의 Description 과 같은 역할)
TOOLS = [{
    "name": "faq_lookup",
    "description": "고객지원 FAQ 에서 문의와 관련된 항목을 찾습니다. 답장을 쓰기 전에 반드시 먼저 호출합니다. "
                   "결과가 빈 배열이면 FAQ 에 근거가 없다는 뜻입니다.",
    "input_schema": {
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "문의의 핵심 표현 (예: 'ERR-5031 정산 보고서')"},
            "category": {"type": "string", "description": "분류 (선택)",
                         "enum": ["계정·로그인", "요금·계약", "장애·오류", "기능 요청", "기타"]},
        },
        "required": ["query"],
    },
}]


def tokens(text: str) -> set[str]:
    """영문·숫자 코드(ERR-5031)는 통째로, 한글은 2글자씩 잘라 조사 차이를 흡수합니다."""
    text = text.lower()
    out = set(re.findall(r"[a-z0-9-]{2,}", text))
    for w in re.findall(r"[가-힣]+", text):
        out |= {w[i:i + 2] for i in range(len(w) - 1)}
    return out


def faq_lookup(query: str, category: str | None = None, top: int = 3) -> list[dict]:
    with open(FAQ_PATH, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    q = tokens(query)
    scored = []
    for r in rows:
        score = len(q & tokens(r["question"] + " " + r["answer"]))
        if category and r["category"] == category:
            score += 1
        if score >= 2:                        # 한두 글자만 겹치는 우연한 일치는 버립니다
            scored.append((score, r))
    scored.sort(key=lambda x: -x[0])
    return [r for _, r in scored[:top]]


def call_tool(name: str, args: dict) -> str:
    """모델이 요청한 도구를 실행하고 결과를 문자열로 돌려줍니다. 모르는 도구는 실행하지 않습니다."""
    if name == "faq_lookup":
        return json.dumps(faq_lookup(args.get("query", ""), args.get("category")), ensure_ascii=False)
    return f"알 수 없는 도구입니다: {name}"


if __name__ == "__main__":
    print(call_tool("faq_lookup", {"query": "정산 보고서 ERR-5031 오류"}))
