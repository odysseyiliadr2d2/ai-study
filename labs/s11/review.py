"""담당자 검토: 검토 대기 SQL 을 보고 승인하면 examples.json 에 예시로 추가 — python review.py"""
import json

LOG, EXAMPLES = "review_log.jsonl", "examples.json"


def main():
    logs = [json.loads(line) for line in open(LOG, encoding="utf-8")]
    examples = json.load(open(EXAMPLES, encoding="utf-8"))
    known = {e["question"] for e in examples}
    for rec in logs:
        if rec["status"] != "검토대기":
            continue
        print("=" * 60)
        print(f"질문  : {rec['question']}  (기준일 {rec['base_dt']}, {rec['rows']}행, 비용 {rec['cost']})")
        print(f"가정  : {rec['assumptions']}")
        print(rec["sql"])
        print(f"binds : {rec['binds']}")
        ans = input("승인(a) / 반려(r) / 건너뛰기(Enter) > ").strip().lower()
        if ans == "a":
            rec["status"] = "승인"
            if rec["question"] not in known:          # 승인된 질문·SQL 은 다음 생성의 few-shot 예시가 됩니다
                examples.append({"question": rec["question"], "sql": rec["sql"], "binds": rec["binds"]})
        elif ans == "r":
            rec["status"] = "반려"
            rec["reason"] = input("반려 사유 > ").strip()
    with open(LOG, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in logs)
    with open(EXAMPLES, "w", encoding="utf-8") as f:
        json.dump(examples, f, ensure_ascii=False, indent=1)
    print(f"예시 쿼리 {len(examples)}개 저장")


if __name__ == "__main__":
    main()
