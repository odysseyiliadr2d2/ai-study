"""답변 평가: LLM 판정(기준표) + 사람 검수 목록 — python judge.py eval.jsonl"""
import csv
import json
import os
import random
import sys

from ask import client
from guard import answer, text_of

JUDGE_MODEL = os.getenv("JUDGE_MODEL", os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5"))

RUBRIC = """당신은 사내 문서 Q&A 봇의 답변을 채점하는 평가자입니다. 아래 기준으로만 채점하세요.
- correct (0~2): 정답 요지와 비교. 2=핵심이 모두 맞음, 1=일부만 맞거나 불필요한 내용 섞임, 0=틀림.
  정답 요지가 비어 있으면 '문서에 없는 질문'이므로, 답하지 않고 모른다고 하면 2, 지어내면 0.
- faithful (0~2): 답변의 모든 문장이 제공된 근거로 뒷받침되는가. 2=전부, 1=일부 근거 밖, 0=대부분 근거 밖.
- cited (0~1): 답변 문장에 [번호] 출처가 붙어 있는가. 거절 답변이면 1.
JSON 한 줄로만 답하세요: {"correct": n, "faithful": n, "cited": n, "reason": "한 문장"}"""


def judge(item: dict, out: dict) -> dict:
    sources = "\n".join(f"- {s['doc']} › {s['section']}" for s in out["sources"]) or "(없음)"
    prompt = (f"질문: {item['question']}\n정답 요지: {item['answer']}\n"
              f"사용된 근거:\n{sources}\n\n봇 답변:\n{out['answer']}")
    # Claude 5.5 계열은 temperature 를 받지 않습니다. 대신 기준표를 구체적으로 적어 흔들림을 줄입니다.
    msg = client.messages.create(model=JUDGE_MODEL, max_tokens=1000,
                                 system=RUBRIC, messages=[{"role": "user", "content": prompt}])
    text = text_of(msg)
    try:
        return json.loads(text[text.index("{"): text.rindex("}") + 1])
    except (ValueError, json.JSONDecodeError):
        return {"correct": None, "faithful": None, "cited": None, "reason": "판정 파싱 실패: " + text[:80]}


def main(path: str, review_ratio: float = 0.2):
    items = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    results = []
    for it in items:
        out = answer(it["question"])
        score = judge(it, out)
        results.append({"id": it["id"], "category": it["category"], "answerable": it["answerable"],
                        "bot": out["answer"], "grounded": out["grounded"], **score})
    ok = [r for r in results if r["correct"] is not None]
    acc = sum(r["correct"] == 2 for r in ok) / max(len(ok), 1)
    faith = sum(r["faithful"] == 2 for r in ok) / max(len(ok), 1)
    print(f"정답률 {acc:.0%} · 근거 충실 {faith:.0%} · 채점 {len(ok)}/{len(results)}건")

    with open("judge_results.jsonl", "w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    # 사람 검수: 낮은 점수·파싱 실패는 전부, 나머지는 무작위 20%
    random.seed(42)
    review = [r for r in results if r["correct"] != 2 or random.random() < review_ratio]
    with open("human_review.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["id", "category", "bot", "correct", "faithful", "reason", "사람판정", "메모"])
        w.writeheader()
        for r in review:
            w.writerow({k: r.get(k, "") for k in w.fieldnames})
    print(f"사람 검수 대상 {len(review)}건 → human_review.csv (엑셀로 열어 '사람판정' 열을 채웁니다)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "eval.jsonl")
