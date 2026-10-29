"""컬럼 설명 초안 만들기 — python draft_comments.py MART [테이블명 ...]
코멘트가 없는 컬럼마다 이름 · 타입 · 샘플 값 · 프로파일 · 용어사전을 LLM에 주고 설명 초안을 받습니다.
결과는 검토용 CSV 와 COMMENT ON 스크립트(.sql) 두 파일로만 씁니다. DB 에는 절대 쓰지 않습니다.
"""
import csv
import datetime as dt
import json
import os
import re
import sys

import oracledb
from anthropic import Anthropic

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
SAMPLE_ROWS = 2000          # 샘플은 앞쪽 일부만 (대형 테이블 보호)
client = Anthropic()

MISSING_SQL = """
SELECT c.table_name, c.column_name, c.data_type, c.data_length, c.nullable, tc.comments
FROM   all_tab_columns c
JOIN   all_tables t ON t.owner = c.owner AND t.table_name = c.table_name
LEFT JOIN all_col_comments cc
       ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
LEFT JOIN all_tab_comments tc ON tc.owner = c.owner AND tc.table_name = c.table_name
WHERE  c.owner = :owner AND TRIM(cc.comments) IS NULL
AND    c.data_type NOT IN ('CLOB', 'NCLOB', 'BLOB', 'LONG', 'LONG RAW', 'RAW', 'XMLTYPE')   -- 프로파일 불가 타입 제외
ORDER  BY c.table_name, c.column_id
"""

IDENT = re.compile(r"^[A-Z][A-Z0-9_$#]{0,127}$")   # 식별자 검사: SQL 에 끼워 넣기 전에 반드시 확인


def text_of(msg) -> str:
    """응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 모읍니다."""
    return "".join(b.text for b in msg.content if b.type == "text")


def load_glossary(path="glossary.csv") -> list[dict]:
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def related_terms(column: str, glossary: list[dict]) -> list[dict]:
    """컬럼명 조각(TRD, AMT, CHNL…)이 용어사전 영문약어와 겹치는 항목만 고릅니다."""
    parts = set(column.split("_"))
    return [g for g in glossary if parts & set(g["영문약어"].split("_")) or g["영문약어"] == column]


def profile(conn, owner: str, table: str, column: str) -> dict:
    """샘플 SAMPLE_ROWS 행에서 NULL 비율·고유값 수·최소/최대·상위 값 5개를 셉니다."""
    for name in (owner, table, column):
        if not IDENT.match(name):
            raise ValueError(f"식별자 형식이 아닙니다: {name}")
    src = f'(SELECT "{column}" AS v FROM "{owner}"."{table}" FETCH FIRST {SAMPLE_ROWS} ROWS ONLY)'
    with conn.cursor() as cur:
        cur.execute(f"SELECT COUNT(*), COUNT(v), COUNT(DISTINCT v), MIN(v), MAX(v) FROM {src}")
        n, nn, nd, mn, mx = cur.fetchone()
        cur.execute(f"SELECT v, COUNT(*) c FROM {src} WHERE v IS NOT NULL GROUP BY v ORDER BY c DESC FETCH FIRST 5 ROWS ONLY")
        top = [(str(v)[:30], c) for v, c in cur.fetchall()]
    return {"rows": n, "null_pct": round(100 * (n - nn) / n, 1) if n else None,
            "distinct": nd, "min": str(mn)[:30], "max": str(mx)[:30], "top": top}


SYSTEM = """당신은 증권사 정보계 DW 의 메타데이터 담당자를 돕습니다.
컬럼 이름, 타입, 테이블 설명, 샘플 프로파일, 업무 용어사전을 보고 컬럼 코멘트 초안을 씁니다.
규칙:
- 용어사전에 표준용어가 있으면 반드시 그 표준용어로 시작합니다. 금지표현은 쓰지 않습니다.
- 형식: '표준용어(단위·형식). 한 문장 보충 설명'. 80자 이내.
- 코드 컬럼이면 샘플에 보이는 코드값을 '01 영업점'처럼 적되, 의미를 모르면 값만 적고 추측하지 않습니다.
- 근거가 약하면 confidence 를 low 로 하고 question 에 담당자에게 물을 점을 씁니다.
JSON 한 줄로만 답하세요: {"comment": "...", "confidence": "high|medium|low", "question": "..."}"""


def draft(table: str, tcomment: str, column: str, dtype: str, prof: dict, terms: list[dict]) -> dict:
    user = json.dumps({"table": table, "table_comment": tcomment, "column": column, "type": dtype,
                       "profile": prof, "glossary": terms}, ensure_ascii=False)
    msg = client.messages.create(model=MODEL, max_tokens=1000, system=SYSTEM,
                                 messages=[{"role": "user", "content": user}])
    text = text_of(msg)
    try:
        return json.loads(text[text.index("{"): text.rindex("}") + 1])
    except (ValueError, json.JSONDecodeError):
        return {"comment": "", "confidence": "low", "question": "초안 생성 실패: " + text[:60]}


def sql_literal(s: str) -> str:
    """작은따옴표를 두 번 써서 SQL 문자열로 만듭니다. 줄바꿈은 공백으로."""
    return "'" + " ".join(s.split()).replace("'", "''") + "'"


def main(owner: str, only: list[str]):
    owner = owner.upper()
    glossary = load_glossary()
    conn = oracledb.connect(user=os.environ["ORA_USER"], password=os.environ["ORA_PASSWORD"],
                            dsn=os.environ.get("ORA_DSN", "localhost:1521/FREEPDB1"))
    conn.call_timeout = 60_000
    with conn, conn.cursor() as cur:
        cur.execute(MISSING_SQL, owner=owner)
        targets = [r for r in cur.fetchall() if not only or r[0] in only]
        rows = []
        for table, column, dtype, dlen, nullable, tcomment in targets:
            try:
                prof = profile(conn, owner, table, column)
            except oracledb.DatabaseError as e:          # 권한 없음·타입 문제 등은 건너뛰고 기록
                print(f"{table}.{column}: 프로파일 실패 {e}")
                continue
            d = draft(table, tcomment or "", column, f"{dtype}({dlen})", prof, related_terms(column, glossary))
            rows.append({"table": table, "column": column, "type": f"{dtype}({dlen})",
                         "null_pct": prof["null_pct"], "distinct": prof["distinct"],
                         "top": " / ".join(f"{v}:{c}" for v, c in prof["top"]),
                         "draft": d.get("comment", ""), "confidence": d.get("confidence", "low"),
                         "question": d.get("question", ""), "final": "", "reviewer": ""})
            print(f"{table}.{column:<20} [{rows[-1]['confidence']:<6}] {rows[-1]['draft']}")

    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M")
    order = {"low": 0, "medium": 1, "high": 2}
    rows.sort(key=lambda r: (order.get(r["confidence"], 0), r["table"]))   # 확신도 낮은 것부터 검토
    with open(f"review_{owner}_{stamp}.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]) if rows else ["table"])
        w.writeheader()
        w.writerows(rows)
    with open(f"comments_{owner}_{stamp}_DRAFT.sql", "w", encoding="utf-8") as f:
        f.write(f"-- LLM 초안 ({MODEL}, {stamp}). 검토 전 실행 금지. 검토 후 apply_reviewed.py 로 확정본을 만드세요.\n")
        for r in rows:
            if r["draft"]:
                f.write(f"-- [{r['confidence']}] {r['question']}\n" if r["question"] else "")
                f.write(f"COMMENT ON COLUMN {owner}.{r['table']}.{r['column']} IS {sql_literal(r['draft'])};\n")
    print(f"\n{len(rows)}개 컬럼 초안 → review_{owner}_{stamp}.csv, comments_{owner}_{stamp}_DRAFT.sql")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("사용법: python draft_comments.py 스키마명 [테이블명 ...]")
    main(sys.argv[1], [t.upper() for t in sys.argv[2:]])
