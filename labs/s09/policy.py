"""실무형 개선: 근거가 약하면 답하지 않고, 최신 개정판만 근거로 씁니다.
ask.py 와 같은 폴더에서 실행: python policy.py "질문"
"""
import sys
from dataclasses import dataclass

from ask import LLM_MODEL, SYSTEM, client, search

MIN_SCORE = 0.78   # 이 값보다 낮으면 근거가 약하다고 봅니다 (평가셋으로 조정)


@dataclass
class Answer:
    text: str
    sources: list[dict]
    grounded: bool


def generate(question: str, picked) -> str:
    context = "\n\n".join(
        f"[{n}] ({r['doc']} · {r['section']})\n{r['text']}" for n, (_, r) in enumerate(picked, 1)
    )
    msg = client.messages.create(
        model=LLM_MODEL, max_tokens=800, system=SYSTEM,
        messages=[{"role": "user", "content": f"<근거>\n{context}\n</근거>\n\n질문: {question}"}],
    )
    return "".join(b.text for b in msg.content if b.type == "text")


def answer_with_policy(question: str) -> Answer:
    hits = search(question, k=6)
    strong = [(s, r) for s, r in hits if s >= MIN_SCORE]
    if not strong:
        return Answer(
            text="문서에서 확실한 근거를 찾지 못했습니다. 운영 당직자(내선 1234)에게 문의해 주세요.",
            sources=[], grounded=False,
        )
    # 같은 문서(doc_id)의 옛 개정판은 빼고 최신 개정판 청크만 남깁니다
    # (색인할 때 doc_id·rev_date 를 메타데이터로 함께 저장해 둔 경우)
    newest = {}
    for _, r in strong:
        key = r.get("doc_id", r["doc"])
        newest[key] = max(newest.get(key, ""), r.get("rev_date", ""))
    picked = [(s, r) for s, r in strong
              if r.get("rev_date", "") == newest[r.get("doc_id", r["doc"])]][:4]
    text = generate(question, picked)
    return Answer(text=text, grounded=True,
                  sources=[{"doc": r["doc"], "section": r["section"], "score": round(s, 2)} for s, r in picked])


if __name__ == "__main__":
    result = answer_with_policy(" ".join(sys.argv[1:]) or "재처리는 어디서부터 하나요?")
    print(result.text)
    for src in result.sources:
        print("-", src)
