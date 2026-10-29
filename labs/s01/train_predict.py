"""(a) 고전 ML: 정답 달린 티켓으로 학습하고, 새 티켓을 예측합니다 — python train_predict.py"""
import csv

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from sklearn.pipeline import make_pipeline

rows = list(csv.DictReader(open("tickets.csv", encoding="utf-8")))
texts = [r["text"] for r in rows]
labels = [r["label"] for r in rows]

# 1) 학습용과 평가용을 나눕니다. 평가용은 학습에 절대 쓰지 않습니다.
X_train, X_test, y_train, y_test = train_test_split(
    texts, labels, test_size=0.25, stratify=labels, random_state=42
)

# 2) 문장을 숫자로 바꾸는 단계 + 분류기를 한 파이프라인으로 묶습니다.
#    한국어는 띄어쓰기가 불규칙하므로 단어 대신 글자 2~3개 묶음(char n-gram)을 씁니다.
model = make_pipeline(
    TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 3)),
    LogisticRegression(max_iter=1000),
)

# 3) 학습(training): 정답과 예측의 차이가 줄도록 가중치를 조정합니다.
model.fit(X_train, y_train)

# 4) 평가: 처음 보는 문장으로 맞힌 비율을 잽니다.
print(classification_report(y_test, model.predict(X_test), zero_division=0))

# 5) 저장: 학습 결과(가중치)는 파일 하나입니다. 추론 서버는 이 파일만 있으면 됩니다.
joblib.dump(model, "ticket_model.joblib")

# 6) 추론(inference): 새 문장에 대해 계산만 합니다. 밀리초 단위로 끝납니다.
new_tickets = [
    "새벽 적재 작업이 ORA-00942 오류로 실패했습니다",
    "지점별 예금 잔고를 주간 단위로 보는 화면이 필요합니다",
]
for text, label, proba in zip(new_tickets, model.predict(new_tickets), model.predict_proba(new_tickets)):
    print(f"{label:6s} (확신도 {proba.max():.2f})  {text}")
