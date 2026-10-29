"""AX 유스케이스 ROI 계산기 — python roi.py usecases.yaml [--md]

연간 절감액 = 사용자 × 하루 절감 분 ÷ 60 × 근무일 × 시간당 인건비 × 활용률 × (1 − 검토 비율)
연간 순효과 = 연간 절감액 − 연간 운영비
회수 기간(개월) = 초기 구축비 ÷ (연간 순효과 ÷ 12)
3년 ROI = (3 × 연간 순효과 − 초기 구축비) ÷ 초기 구축비
결과는 입력값에 따른 '추정'입니다. 파일럿 실측값으로 다시 계산하세요.
"""
import sys

import yaml


def won(x: float) -> str:
    return f"{x / 1e6:,.1f}백만"


def evaluate(uc: dict, a: dict) -> dict:
    hours = (uc["users"] * uc["minutes_saved_per_user_per_day"] / 60 * a["work_days_per_year"]
             * a["adoption_rate"] * (1 - a["quality_review_ratio"]))
    saving = hours * a["hourly_cost_krw"]
    net = saving - uc["annual_run_cost_krw"]
    payback = uc["one_time_cost_krw"] / (net / 12) if net > 0 else float("inf")
    roi3 = (3 * net - uc["one_time_cost_krw"]) / uc["one_time_cost_krw"]
    quadrant = ("먼저 할 것" if uc["effect"] >= 4 and uc["difficulty"] <= 3 else
                "전략 과제" if uc["effect"] >= 4 else
                "빠른 개선" if uc["difficulty"] <= 3 else "보류")
    return {"name": uc["name"], "hours": hours, "saving": saving, "net": net,
            "payback": payback, "roi3": roi3, "quadrant": quadrant}


def main(path: str, markdown: bool):
    cfg = yaml.safe_load(open(path, encoding="utf-8"))
    a = cfg["assumptions"]
    rows = [evaluate(u, a) for u in cfg["usecases"]]
    # 회수 기간이 짧은 순, 같으면 3년 ROI 가 큰 순
    rows.sort(key=lambda r: (r["payback"], -r["roi3"]))

    if markdown:
        print("| 순위 | 유스케이스 | 사분면 | 연간 절감 시간 | 연간 순효과(원) | 회수 기간 | 3년 ROI |")
        print("|---:|---|---|---:|---:|---:|---:|")
    for n, r in enumerate(rows, 1):
        pb = f"{r['payback']:.1f}개월" if r["payback"] != float("inf") else "회수 불가"
        if markdown:
            print(f"| {n} | {r['name']} | {r['quadrant']} | {r['hours']:,.0f}h | {won(r['net'])} | {pb} | {r['roi3']:.0%} |")
        else:
            print(f"{n}. {r['name']} [{r['quadrant']}]")
            print(f"   절감 {r['hours']:,.0f}시간/년 · 순효과 {won(r['net'])}원/년 · 회수 {pb} · 3년 ROI {r['roi3']:.0%}")
    print(f"\n가정: 시간당 {a['hourly_cost_krw']:,}원 · 근무일 {a['work_days_per_year']}일 · "
          f"활용률 {a['adoption_rate']:.0%} · 검토 비율 {a['quality_review_ratio']:.0%} (예시 값)")


if __name__ == "__main__":
    args = [x for x in sys.argv[1:] if not x.startswith("--")]
    main(args[0] if args else "usecases.yaml", "--md" in sys.argv)
