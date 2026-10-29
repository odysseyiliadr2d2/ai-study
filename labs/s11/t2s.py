"""Text-to-SQL CLI — python t2s.py "지난달 영업일 기준 채널별 거래금액" [--base-dt 20261029]
결과·SQL·설명을 보여 주고, review_log.jsonl 에 담당자 검토용 기록을 남깁니다.
"""
import argparse
import datetime as dt
import json
import os
import uuid

import sqlglot
from anthropic import Anthropic
from sqlglot import exp

from metadata import connect, link_schema, load_json, load_tables, schema_text
from sqlguard import MAX_ROWS, UnsafeSQL, check_sql

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
MAX_COST = int(os.getenv("MAX_PLAN_COST", "100000"))   # 실행 계획 비용 상한 (DB마다 조정)
client = Anthropic()


def text_of(msg) -> str:
    """응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 모읍니다."""
    return "".join(b.text for b in msg.content if b.type == "text")


SYSTEM = """당신은 증권사 정보계 Oracle DW 의 SQL 작성 도우미입니다.
규칙:
- 주어진 <스키마> 의 테이블·컬럼만 씁니다. 테이블은 반드시 스키마명.테이블명 으로 씁니다.
- Oracle 문법만 씁니다. 행 제한은 FETCH FIRST n ROWS ONLY 로 합니다 (LIMIT 금지).
- 날짜·코드처럼 질문에서 온 값은 바인드 변수(:from_dt 등)로 두고 값은 binds 에 넣습니다.
- 일자 컬럼은 'YYYYMMDD' 문자열입니다. 상대 날짜(지난달, 최근 5영업일)는 <기준일> 로 계산합니다.
- 조회문(SELECT/WITH) 하나만 만듭니다. 데이터를 바꾸는 문장은 절대 만들지 않습니다.
- 질문이 모호하면 가장 흔한 해석을 고르고 assumptions 에 적습니다.
JSON 하나로만 답하세요: {"sql": "...", "binds": {"이름": "값"}, "assumptions": ["..."]}"""


def ask_json(system: str, user: str) -> dict:
    msg = client.messages.create(model=MODEL, max_tokens=2000, system=system,
                                 messages=[{"role": "user", "content": user}])
    text = text_of(msg)
    return json.loads(text[text.index("{"): text.rindex("}") + 1])


def build_prompt(question, base_dt, schema, terms, examples, error=None):
    shots = "\n\n".join(f"질문: {e['question']}\nSQL:\n{e['sql']}\nbinds: {json.dumps(e['binds'], ensure_ascii=False)}"
                        for e in examples)
    glossary = "\n".join(f"- {k}: {v}" for k, v in terms.items()) or "- (해당 없음)"
    prompt = (f"<기준일>{base_dt}</기준일>\n\n<스키마>\n{schema}\n</스키마>\n\n"
              f"<업무용어>\n{glossary}\n</업무용어>\n\n<예시>\n{shots}\n</예시>\n\n질문: {question}")
    if error:
        prompt += f"\n\n직전 SQL 이 검증에서 거부되었습니다: {error}\n규칙을 지켜 다시 작성하세요."
    return prompt


def bind_names(sql: str) -> set[str]:
    return {p.name for p in sqlglot.parse_one(sql, read="oracle").find_all(exp.Placeholder)}


def plan_cost(conn, sql: str, binds: dict) -> int | None:
    """EXPLAIN PLAN 으로 옵티마이저 예상 비용을 봅니다. 실패하면 None (실행은 계속, 로그에 남김).
    EXPLAIN PLAN 에는 바인드 값을 넘기지 않으므로, 확인용 SQL 에만 값을 리터럴로 넣습니다(sqlglot 이 따옴표 처리)."""
    sid = uuid.uuid4().hex[:20]
    literal = sqlglot.parse_one(sql, read="oracle").transform(
        lambda n: exp.Literal.string(str(binds[n.name])) if isinstance(n, exp.Placeholder) and n.name in binds else n
    ).sql(dialect="oracle")
    try:
        with conn.cursor() as cur:
            cur.execute(f"EXPLAIN PLAN SET STATEMENT_ID = '{sid}' FOR {literal}")
            cur.execute("SELECT cost FROM plan_table WHERE statement_id = :sid AND id = 0", sid=sid)
            row = cur.fetchone()
        conn.rollback()                    # PLAN_TABLE 은 세션 임시 테이블. 남기지 않습니다
        return int(row[0]) if row and row[0] is not None else None
    except Exception as e:                 # 권한·버전에 따라 막힐 수 있음
        print(f"(실행 계획 확인 생략: {e})")
        return None


def run(conn, sql: str, binds: dict):
    with conn.cursor() as cur:
        cur.arraysize = MAX_ROWS
        cur.execute(sql, binds)
        cols = [d[0] for d in cur.description]
        rows = cur.fetchmany(MAX_ROWS)
    return cols, rows


def explain(question, sql, cols, rows, assumptions) -> str:
    sample = "\n".join(" | ".join(map(str, r)) for r in rows[:20])
    msg = client.messages.create(
        model=MODEL, max_tokens=1500,
        system="SQL 결과를 업무 담당자에게 설명합니다. 합니다체로 5문장 이내. "
               "1) 질문에 대한 답 2) 어떤 테이블·조건으로 셌는지 3) 담당자가 확인할 가정을 씁니다. "
               "결과에 없는 숫자는 만들지 않습니다.",
        messages=[{"role": "user", "content": f"질문: {question}\nSQL:\n{sql}\n가정: {assumptions}\n"
                                              f"컬럼: {cols}\n결과(최대 20행):\n{sample}"}],
    )
    return text_of(msg)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("question")
    ap.add_argument("--base-dt", default=dt.date.today().strftime("%Y%m%d"), help="기준일 YYYYMMDD")
    args = ap.parse_args()

    glossary, examples = load_json("glossary.json"), load_json("examples.json")
    with connect() as conn:
        tables = load_tables(conn)
        names, terms = link_schema(args.question, tables, glossary)
        schema = schema_text(tables, names)
        allowed = set(tables)                               # 이 계정이 볼 수 있는 DW 테이블만

        error, safe, out = None, None, {}
        for attempt in range(2):                            # 거부되면 이유를 붙여 한 번만 재시도
            out = ask_json(SYSTEM, build_prompt(args.question, args.base_dt, schema, terms, examples, error))
            try:
                safe = check_sql(out["sql"], allowed)
                break
            except UnsafeSQL as e:
                error = str(e)
                print(f"[검증 거부 {attempt + 1}] {error}")
        if safe is None:
            raise SystemExit("안전한 SQL 을 만들지 못했습니다. 질문을 구체적으로 바꿔 주세요.")

        needed = bind_names(safe)                           # SQL 에 실제로 있는 바인드만 넘깁니다
        binds = {k: v for k, v in (out.get("binds") or {}).items() if k in needed}
        if needed - set(binds):
            raise SystemExit(f"바인드 값이 빠졌습니다: {sorted(needed - set(binds))}")
        cost = plan_cost(conn, safe, binds)
        if cost is not None and cost > MAX_COST:
            raise SystemExit(f"예상 비용 {cost} 이 상한 {MAX_COST} 를 넘습니다. 기간·조건을 줄여 주세요.")
        cols, rows = run(conn, safe, binds)

    print("— SQL\n" + safe + f"\n— binds {binds}  · 예상 비용 {cost}\n")
    print(" | ".join(cols))
    for r in rows[:30]:
        print(" | ".join(map(str, r)))
    print(f"({len(rows)}행{', 상한 도달' if len(rows) == MAX_ROWS else ''})\n")
    summary = explain(args.question, safe, cols, rows, out.get("assumptions", []))
    print("— 설명\n" + summary)

    with open("review_log.jsonl", "a", encoding="utf-8") as f:
        f.write(json.dumps({"at": dt.datetime.now().isoformat(timespec="seconds"), "question": args.question,
                            "base_dt": args.base_dt, "tables": names, "sql": safe, "binds": binds,
                            "assumptions": out.get("assumptions", []), "rows": len(rows), "cost": cost,
                            "status": "검토대기"}, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
