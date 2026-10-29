"""평가 하네스 — python run_eval.py scenarios.yaml [--repeat 3] [--only I01,I02]

시나리오마다 에이전트를 돌리고 ① 필수 도구 호출 ② 금지 도구 시도 ③ 필수 핵심어 ④ 금지 패턴을 자동 채점합니다.
결과: eval_out/results.jsonl, eval_out/report.md, eval_out/review.csv (사람 검수용)
"""
import argparse
import csv
import json
import re
from collections import defaultdict
from datetime import date
from pathlib import Path

import yaml

import ops_tools
import traced_agent

BASE = Path(__file__).parent
OUT = BASE / "eval_out"


def grade(sc: dict, tr) -> list[str]:
    """실패 사유 목록. 비어 있으면 통과."""
    fails = []
    exp, forb = sc.get("expect", {}), sc.get("forbid", {})
    called = [name for name, _, _ in tr.tool_calls]
    for t in exp.get("tools_all", []):
        if t not in called:
            fails.append(f"필수 도구 미호출: {t}")
    for t in forb.get("tools", []):
        if t in called:
            fails.append(f"금지 도구 시도: {t}")
    for kw in exp.get("contains", []):
        if kw not in tr.final:
            fails.append(f"핵심어 없음: {kw}")
    for pat in forb.get("patterns", []):
        if re.search(pat, tr.final):
            fails.append(f"금지 패턴: {pat}")
    if tr.stop != "end":
        fails.append(f"비정상 종료: {tr.stop}")
    return fails


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--repeat", type=int, default=1, help="같은 시나리오 반복 횟수 (모델 응답 흔들림 확인)")
    ap.add_argument("--only", default="")
    args = ap.parse_args()

    scenarios = yaml.safe_load(open(args.file, encoding="utf-8"))
    if args.only:
        keep = set(args.only.split(","))
        scenarios = [s for s in scenarios if s["id"] in keep]
    OUT.mkdir(exist_ok=True)
    rows = []
    for sc in scenarios:
        ops_tools.DATA = BASE / sc.get("data", "mock")          # 시나리오별 데이터 묶음 교체
        for k in range(args.repeat):
            tr = traced_agent.run(sc["question"], today=date.fromisoformat(sc["today"]))
            fails = grade(sc, tr)
            row = {"id": sc["id"], "category": sc["category"], "try": k + 1, "pass": not fails, "fails": fails,
                   "tools": [n for n, _, _ in tr.tool_calls], "blocked": len(tr.blocked),
                   "tokens_in": tr.tokens_in, "tokens_out": tr.tokens_out, "cost_usd": round(tr.cost_usd, 5),
                   "latency_ms": tr.latency_ms, "run_id": tr.run_id, "answer": tr.final}
            rows.append(row)
            print(f"{'PASS' if row['pass'] else 'FAIL'} {sc['id']}#{k + 1} {row['tools']} {'; '.join(fails)}")

    with open(OUT / "results.jsonl", "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    by_cat = defaultdict(list)
    for r in rows:
        by_cat[r["category"]].append(r)
    lines = [f"# 운영 에이전트 평가 결과 ({date.today()})", "",
             "| 유형 | 실행 | 통과 | 통과율 | 평균 토큰(in/out) | 평균 지연(s) |", "|---|---|---|---|---|---|"]
    for cat, rs in sorted(by_cat.items()):
        n, ok = len(rs), sum(r["pass"] for r in rs)
        lines.append(f"| {cat} | {n} | {ok} | {ok / n:.0%} | {sum(r['tokens_in'] for r in rs) // n}/"
                     f"{sum(r['tokens_out'] for r in rs) // n} | {sum(r['latency_ms'] for r in rs) / n / 1000:.1f} |")
    total_ok = sum(r["pass"] for r in rows)
    lines += ["", f"전체 {total_ok}/{len(rows)} ({total_ok / max(1, len(rows)):.0%}) · 추정 비용 ${sum(r['cost_usd'] for r in rows):.4f}",
              "", "## 실패 목록"] + [f"- {r['id']}#{r['try']}: {'; '.join(r['fails'])}" for r in rows if not r["pass"]]
    (OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # 사람 검수: 실패 전부 + 통과 중 safety·injection 전부 (키워드 채점이 놓치는 것을 사람이 봄)
    with open(OUT / "review.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "category", "try", "auto_pass", "fails", "answer", "human_ok(Y/N)", "comment"])
        for r in rows:
            if not r["pass"] or r["category"] in ("safety", "injection"):
                w.writerow([r["id"], r["category"], r["try"], r["pass"], "; ".join(r["fails"]), r["answer"], "", ""])
    print(f"\n전체 {total_ok}/{len(rows)} 통과 → {OUT / 'report.md'}")


if __name__ == "__main__":
    main()
