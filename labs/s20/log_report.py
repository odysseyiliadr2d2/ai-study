"""관측 로그 요약 — python log_report.py logs/agent-20261029.jsonl

요청 수, 종료 유형, 도구 호출·오류율, 가드레일 차단, 토큰·지연(p50/p95)·비용을 한 화면에.
운영에서는 같은 JSONL 을 로그 수집기(ELK·Loki 등)로 보내 대시보드로 봅니다.
"""
import json
import statistics
import sys
from collections import Counter, defaultdict


def pct(values, p):
    if not values:
        return 0
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def main(path: str):
    evs = [json.loads(line) for line in open(path, encoding="utf-8")]
    runs = defaultdict(list)
    for e in evs:
        runs[e["run_id"]].append(e)

    finals = [e for e in evs if e["event"] == "final"]
    tools = [e for e in evs if e["event"] == "tool_call"]
    guards = [e for e in evs if e["event"] == "guard"]
    llm = [e for e in evs if e["event"] == "llm_call"]
    run_latency = [sum(x.get("latency_ms", 0) for x in r if x["event"] in ("llm_call", "tool_call"))
                   for r in runs.values()]

    print(f"요청 {len(runs)}건 · 정상 종료 {sum(1 for f in finals if f.get('stop') != 'max_steps')} · "
          f"최대 반복 도달 {sum(1 for f in finals if f.get('stop') == 'max_steps')}")
    print(f"모델 호출 {len(llm)}회 (요청당 평균 {len(llm) / max(1, len(runs)):.1f}) · "
          f"토큰 in {sum(e['in_tok'] for e in llm):,} / out {sum(e['out_tok'] for e in llm):,} · "
          f"추정 비용 ${sum(e.get('cost_usd', 0) for e in llm):.4f}")
    print(f"요청 지연 p50 {pct(run_latency, 50) / 1000:.1f}s · p95 {pct(run_latency, 95) / 1000:.1f}s")
    by_tool = Counter(e["tool"] for e in tools)
    err_tool = Counter(e["tool"] for e in tools if e["is_error"])
    for t, n in by_tool.most_common():
        print(f"  도구 {t}: {n}회 · 오류 {err_tool[t]}회 ({err_tool[t] / n:.0%})")
    print(f"가드레일 차단 {len(guards)}건: {Counter(g['kind'] for g in guards)}")
    if llm:
        print(f"모델 호출 지연 중앙값 {statistics.median(e['latency_ms'] for e in llm) / 1000:.1f}s")


if __name__ == "__main__":
    main(sys.argv[1])
