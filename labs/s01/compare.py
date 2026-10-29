"""두 방식을 같은 평가셋으로 비교합니다 — python compare.py
train_predict.py 를 먼저 실행해 ticket_model.joblib 을 만들어 두세요.
"""
import csv
import time

import joblib
from sklearn.model_selection import train_test_split

from zero_shot import classify

rows = list(csv.DictReader(open("tickets.csv", encoding="utf-8")))
texts, labels = [r["text"] for r in rows], [r["label"] for r in rows]
_, X_test, _, y_test = train_test_split(texts, labels, test_size=0.25, stratify=labels, random_state=42)

ml = joblib.load("ticket_model.joblib")


def run(name, fn):
    start = time.perf_counter()
    preds = [fn(t) for t in X_test]
    sec = time.perf_counter() - start
    acc = sum(p == y for p, y in zip(preds, y_test)) / len(y_test)
    print(f"{name:10s} 정확도 {acc:.0%}  전체 {sec:.2f}초  건당 {sec / len(X_test) * 1000:.0f}ms")
    for t, p, y in zip(X_test, preds, y_test):
        if p != y:
            print(f"   오답: {t[:30]}… → {p} (정답 {y})")


run("고전 ML", lambda t: ml.predict([t])[0])
run("LLM 제로샷", classify)
