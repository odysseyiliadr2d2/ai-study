"""최소 LoRA 파인튜닝 (TRL SFTTrainer + PEFT) — python train_lora.py

작은 모델로 구조를 익히는 실습입니다. GPU 1장(16GB 이상)이면 수 분 안에 끝납니다.
데이터: train.jsonl / eval.jsonl (messages 형식, validate_jsonl.py 통과한 것)
"""
import os

from datasets import load_dataset
from peft import LoraConfig
from trl import SFTConfig, SFTTrainer

BASE = os.getenv("BASE_MODEL", "Qwen/Qwen3-0.6B")   # 실습용 작은 모델 (폐쇄망에서는 로컬 경로)
OUT = os.getenv("OUT_DIR", "out/report-style-lora")

data = load_dataset("json", data_files={"train": "train.jsonl", "eval": "eval.jsonl"})

peft_config = LoraConfig(
    r=16,                         # 랭크: 8~32 에서 시작
    lora_alpha=32,                # 보통 r 의 2배
    lora_dropout=0.05,
    target_modules="all-linear",  # 모든 선형층에 어댑터 (QLoRA 방식)
    task_type="CAUSAL_LM",
)

args = SFTConfig(
    output_dir=OUT,
    num_train_epochs=2,               # 데이터가 적을수록 1~3 에포크로 제한
    per_device_train_batch_size=4,
    gradient_accumulation_steps=4,
    learning_rate=1e-4,               # 어댑터 학습은 1e-4 안팎
    max_length=2048,
    assistant_only_loss=True,         # 정답(assistant) 부분만 손실 계산
    eval_strategy="steps",
    eval_steps=50,
    logging_steps=10,
    save_strategy="steps",
    save_steps=50,
    load_best_model_at_end=True,      # 검증 손실이 가장 낮은 지점을 남김
    report_to="none",
)

trainer = SFTTrainer(
    model=BASE,
    args=args,
    train_dataset=data["train"],
    eval_dataset=data["eval"],
    peft_config=peft_config,
)
trainer.train()
trainer.save_model(OUT)          # 어댑터 가중치만 저장(수십 MB)
print("저장:", OUT)
