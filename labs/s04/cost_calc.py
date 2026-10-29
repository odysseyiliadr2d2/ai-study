"""토큰 비용 계산기 — python cost_calc.py
가격은 코드에 넣지 않고 prices.json 에서 읽습니다. 값이 비어 있는 모델은 계산하지 않고 알려 줍니다.
"""
import json
from dataclasses import dataclass

PRICES = {k: v for k, v in json.load(open("prices.json", encoding="utf-8")).items() if not k.startswith("_")}
M = 1_000_000


@dataclass
class Usage:
    input_tokens: int            # 캐시를 거치지 않은 입력
    output_tokens: int
    cache_read_tokens: int = 0   # 캐시에서 읽은 입력
    cache_write_tokens: int = 0  # 이번 요청에서 캐시에 쓴 입력


def request_cost(model: str, u: Usage) -> float:
    p = PRICES[model]
    keys = ("input", "output", "cache_write", "cache_read")
    missing = [k for k in keys if p.get(k) is None]
    if missing:
        raise ValueError(f"{model}: prices.json 에 {missing} 값을 채우세요")
    in_mult = out_mult = 1.0
    lc = p.get("long_context")
    prompt_len = u.input_tokens + u.cache_read_tokens + u.cache_write_tokens
    if lc and prompt_len > lc["threshold"]:
        if lc.get("input_mult") is None or lc.get("output_mult") is None:
            raise ValueError(f"{model}: 긴 입력({prompt_len:,}토큰) 단가 배수를 채우세요")
        in_mult, out_mult = lc["input_mult"], lc["output_mult"]
    return (
        u.input_tokens * p["input"] * in_mult
        + u.cache_read_tokens * p["cache_read"] * in_mult
        + u.cache_write_tokens * p["cache_write"] * in_mult
        + u.output_tokens * p["output"] * out_mult
    ) / M


def monthly(model: str, per_request: Usage, requests_per_day: int, days: int = 21) -> float:
    return request_cost(model, per_request) * requests_per_day * days


if __name__ == "__main__":
    # 시나리오: 장애 로그 요약 — 고정 지시·정의서 8,000토큰(캐시) + 로그 1,500토큰 → 요약 800토큰
    scenarios = {
        "캐시 없음": Usage(input_tokens=9_500, output_tokens=800),
        "캐시 적중": Usage(input_tokens=1_500, output_tokens=800, cache_read_tokens=8_000),
    }
    per_day = 200   # 팀 전체 하루 호출 수
    print(f"{'모델':20s} {'시나리오':8s} {'건당($)':>10s} {'월($, 21일)':>12s}")
    for model in PRICES:
        for name, u in scenarios.items():
            try:
                print(f"{model:20s} {name:8s} {request_cost(model, u):>10.4f} {monthly(model, u, per_day):>12.2f}")
            except ValueError as e:
                print(f"{model:20s} 건너뜀 — {e}")
                break
