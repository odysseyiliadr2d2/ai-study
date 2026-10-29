"""학습 전후 비교 평가 — python eval_before_after.py

같은 eval.jsonl 로 '기본 모델'과 '기본 모델 + LoRA 어댑터'의 답을 만들고,
형식 준수율(필수 항목 5개)과 정답과의 글자 겹침(간이 지표)을 비교합니다.
최종 판단은 사람 검토(블라인드 선호도)로 합니다.
"""
import json
import os

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE = os.getenv("BASE_MODEL", "Qwen/Qwen3-0.6B")
ADAPTER = os.getenv("OUT_DIR", "out/report-style-lora")
REQUIRED = ["■ 개요", "■ 원인", "■ 조치", "■ 영향", "■ 후속"]

tok = AutoTokenizer.from_pretrained(BASE)
device = "cuda" if torch.cuda.is_available() else "cpu"


def generate(model, messages) -> str:
    prompt = tok.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    ids = tok(prompt, return_tensors="pt").to(device)
    with torch.no_grad():
        out = model.generate(**ids, max_new_tokens=300, do_sample=False)
    return tok.decode(out[0][ids["input_ids"].shape[1]:], skip_special_tokens=True).strip()


def char_f1(pred: str, ref: str) -> float:
    p, r = set(pred.replace(" ", "")), set(ref.replace(" ", ""))
    if not p or not r:
        return 0.0
    inter = len(p & r)
    return 2 * inter / (len(p) + len(r))


def score(model, rows):
    fmt, f1 = 0, 0.0
    samples = []
    for row in rows:
        msgs = row["messages"]
        pred = generate(model, msgs[:-1])
        ref = msgs[-1]["content"]
        fmt += all(k in pred for k in REQUIRED)
        f1 += char_f1(pred, ref)
        samples.append(pred)
    n = len(rows)
    return fmt / n, f1 / n, samples


rows = [json.loads(line) for line in open("eval.jsonl", encoding="utf-8") if line.strip()]
base = AutoModelForCausalLM.from_pretrained(BASE, torch_dtype="auto").to(device)
b_fmt, b_f1, b_out = score(base, rows)

tuned = PeftModel.from_pretrained(base, ADAPTER).to(device)
t_fmt, t_f1, t_out = score(tuned, rows)

print(f"형식 준수율  기본 {b_fmt:.0%} → LoRA {t_fmt:.0%}")
print(f"글자 겹침    기본 {b_f1:.2f} → LoRA {t_f1:.2f}")
with open("compare.jsonl", "w", encoding="utf-8") as f:   # 사람 검토용
    for row, b, t in zip(rows, b_out, t_out):
        f.write(json.dumps({"input": row["messages"][-2]["content"], "base": b, "lora": t,
                            "ref": row["messages"][-1]["content"]}, ensure_ascii=False) + "\n")
print("compare.jsonl 에 나란히 저장 — 검토자 2명이 모델 이름을 가린 채 선호를 표시합니다")
