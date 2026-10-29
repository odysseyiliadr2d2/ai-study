"""질의 재작성 + 하이브리드 + 리랭킹 + 가드레일로 답하기 — python guard.py "질문" """
import json
import re
import sys

from ask import LLM_MODEL, SYSTEM, client       # 세션 9 의 시스템 프롬프트·클라이언트 재사용
from hybrid import hybrid_ids, records, rrf
from rerank import rerank

MIN_RERANK = 0.30   # 최상위 근거 점수가 이보다 낮으면 답하지 않음 (평가셋으로 조정)

# 업무 용어사전: 약어 → 문서에 실제로 쓰인 표현 (세션 12에서 정비하는 그 사전)
GLOSSARY = {"TSS": "Terrastream", "당직": "운영 당직자", "재처리": "재실행", "임시 공간": "TEMP 테이블스페이스"}

PII = [
    (re.compile(r"\b\d{6}-?[1-4]\d{6}\b"), "[주민번호]"),
    (re.compile(r"\b01[016789]-?\d{3,4}-?\d{4}\b"), "[전화번호]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"), "[이메일]"),
    (re.compile(r"\b\d{3,6}-\d{2,6}-\d{4,8}\b"), "[계좌번호]"),
]


def text_of(msg) -> str:
    """응답에 thinking 블록이 먼저 올 수 있으므로 text 블록만 모읍니다."""
    return "".join(b.text for b in msg.content if b.type == "text")


def mask(text: str) -> str:
    for pat, label in PII:
        text = pat.sub(label, text)
    return text


def rewrite(question: str) -> list[str]:
    """원 질문 + 용어사전 치환본 + LLM이 만든 검색어 2개."""
    expanded = question
    for short, full in GLOSSARY.items():
        expanded = expanded.replace(short, f"{short}({full})")
    msg = client.messages.create(
        model=LLM_MODEL, max_tokens=200,
        system="사내 문서 검색어를 만듭니다. 질문을 문서에 나올 법한 표현으로 바꾼 검색어 2개를 "
               "JSON 문자열 배열로만 출력하세요. 예: [\"...\", \"...\"]",
        messages=[{"role": "user", "content": expanded}],
    )
    try:
        text = text_of(msg)
        extra = json.loads(text[text.index("["): text.rindex("]") + 1])
    except (ValueError, json.JSONDecodeError):
        extra = []
    return list(dict.fromkeys([question, expanded, *[str(e) for e in extra[:2]]]))


def answer(question: str, where=None) -> dict:
    question = mask(question)                          # 1) 입력 마스킹: 외부 LLM 로 나가기 전에
    queries = rewrite(question)                        # 2) 질의 재작성
    ids = rrf([hybrid_ids(q, 20, where) for q in queries])[:20]   # 3) 검색어별 결과를 다시 RRF
    picked = rerank(question, [records[i] for i in ids], top=4)   # 4) 리랭킹
    if not picked or picked[0][0] < MIN_RERANK:        # 5) 근거 없음 처리
        return {"question": question, "queries": queries, "grounded": False, "sources": [],
                "answer": "문서에서 확실한 근거를 찾지 못했습니다. 운영 당직자에게 문의해 주세요."}
    context = "\n\n".join(
        f"[{n}] ({r['doc']} · {r['section']})\n{r['text']}" for n, (_, r) in enumerate(picked, 1)
    )
    msg = client.messages.create(
        model=LLM_MODEL, max_tokens=800, system=SYSTEM,
        messages=[{"role": "user", "content": f"<근거>\n{context}\n</근거>\n\n질문: {question}"}],
    )
    text = mask(text_of(msg))                          # 6) 출력 마스킹
    cited = bool(re.search(r"\[\d+\]", text))
    return {"question": question, "queries": queries, "grounded": cited, "answer": text,
            "sources": [{"doc": r["doc"], "section": r["section"], "score": round(s, 3)} for s, r in picked]}


if __name__ == "__main__":
    out = answer(" ".join(sys.argv[1:]) or "TSS 재처리는 어디서부터 하나요?")
    print(json.dumps(out, ensure_ascii=False, indent=2))
