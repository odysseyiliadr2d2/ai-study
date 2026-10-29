"""학습 데이터(JSONL chat 형식) 검사 — python validate_jsonl.py train.jsonl

검사 항목: JSON 형식, messages 구조, 역할 순서, 빈 내용, 길이, 중복, 개인정보 의심 패턴.
개인정보 패턴은 예시용이며 사내 기준으로 보완해야 합니다.
"""
import hashlib
import json
import re
import sys
from collections import Counter

ROLES = {"system", "user", "assistant"}
MAX_CHARS = 6000          # 대략적인 길이 상한(토큰 수는 토크나이저로 다시 확인)
PII = {
    "주민등록번호 의심": re.compile(r"\d{6}-?[1-4]\d{6}"),
    "전화번호 의심": re.compile(r"01[016789]-?\d{3,4}-?\d{4}"),
    "이메일": re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"),
    "긴 숫자열(계좌 등) 의심": re.compile(r"\d{2,6}-\d{2,6}-\d{2,8}"),
}


def check_record(n: int, rec: dict, errors: list, warns: list):
    msgs = rec.get("messages")
    if not isinstance(msgs, list) or len(msgs) < 2:
        errors.append(f"{n}행: messages 가 없거나 2개 미만")
        return
    roles = [m.get("role") for m in msgs]
    if any(r not in ROLES for r in roles):
        errors.append(f"{n}행: 알 수 없는 role {roles}")
    if roles[-1] != "assistant":
        errors.append(f"{n}행: 마지막 메시지는 assistant(정답)여야 합니다")
    for m in msgs:
        text = m.get("content", "")
        if not isinstance(text, str) or not text.strip():
            errors.append(f"{n}행: 빈 content ({m.get('role')})")
            continue
        for label, pat in PII.items():
            if pat.search(text):
                errors.append(f"{n}행: {label} — 마스킹 후 다시 넣으세요")
    total = sum(len(m.get("content", "")) for m in msgs)
    if total > MAX_CHARS:
        warns.append(f"{n}행: 길이 {total}자 — max_length 에서 잘릴 수 있음")


def main(path: str):
    errors, warns, seen = [], [], Counter()
    answers = Counter()
    count = 0
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip():
            continue
        count += 1
        try:
            rec = json.loads(line)
        except json.JSONDecodeError as e:
            errors.append(f"{n}행: JSON 오류 {e}")
            continue
        check_record(n, rec, errors, warns)
        seen[hashlib.md5(line.encode()).hexdigest()] += 1
        if rec.get("messages"):
            answers[rec["messages"][-1].get("content", "")[:40]] += 1
    dup = sum(c - 1 for c in seen.values() if c > 1)
    if dup:
        warns.append(f"완전히 같은 행 {dup}개 — 과적합 원인이 됩니다")
    common = answers.most_common(1)
    if common and count >= 20 and common[0][1] / count > 0.3:
        warns.append("정답의 30% 이상이 같은 문장으로 시작합니다 — 다양성을 확인하세요")
    print(f"{'OK' if not errors else 'FAIL'} {count}건 검사 · 오류 {len(errors)} · 경고 {len(warns)}")
    for e in errors[:50]:
        print("  FAIL", e)
    for w in warns[:50]:
        print("  WARN", w)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "train.jsonl")
