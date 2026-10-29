"""민감도 분석 — 활용률과 절감 시간이 예상보다 낮으면 회수 기간이 어떻게 바뀌나
python sensitivity.py usecases.yaml "장애 보고서 초안 자동 작성"
"""
import sys

import yaml

from roi import evaluate

cfg = yaml.safe_load(open(sys.argv[1] if len(sys.argv) > 1 else "usecases.yaml", encoding="utf-8"))
target = sys.argv[2] if len(sys.argv) > 2 else cfg["usecases"][0]["name"]
uc = next(u for u in cfg["usecases"] if u["name"] == target)

print(f"[{target}] 회수 기간(개월) — 행: 활용률, 열: 하루 절감 분 배율")
scales = [0.5, 0.75, 1.0]
print("활용률  " + "  ".join(f"×{s:<4}" for s in scales))
for rate in (0.3, 0.5, 0.7, 0.9):
    cells = []
    for s in scales:
        a = dict(cfg["assumptions"], adoption_rate=rate)
        u = dict(uc, minutes_saved_per_user_per_day=uc["minutes_saved_per_user_per_day"] * s)
        pb = evaluate(u, a)["payback"]
        cells.append(f"{pb:5.1f}" if pb != float("inf") else "  불가")
    print(f"{rate:>5.0%}   " + "  ".join(cells))
