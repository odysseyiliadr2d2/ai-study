"""메타데이터 읽기와 스키마 링킹 — python metadata.py "질문" 으로 링킹 결과만 확인"""
import json
import os
import re
import sys

import oracledb

OWNER = os.getenv("DW_OWNER", "DW")

COLS_SQL = """
SELECT c.table_name, c.column_name, c.data_type, c.data_length, c.nullable,
       NVL(cc.comments, '(설명 없음)') AS col_comment,
       NVL(tc.comments, '(설명 없음)') AS tab_comment
FROM   all_tab_columns c
LEFT JOIN all_col_comments cc
       ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
LEFT JOIN all_tab_comments tc
       ON tc.owner = c.owner AND tc.table_name = c.table_name
WHERE  c.owner = :owner
ORDER  BY c.table_name, c.column_id
"""


def connect():
    """thin 모드(기본) 접속. 읽기 전용 계정 정보는 환경변수로만 받습니다."""
    conn = oracledb.connect(user=os.environ["ORA_USER"], password=os.environ["ORA_PASSWORD"],
                            dsn=os.environ.get("ORA_DSN", "localhost:1521/FREEPDB1"))
    conn.call_timeout = 30_000          # 한 번의 DB 호출이 30초를 넘으면 끊습니다 (밀리초)
    return conn


def load_tables(conn) -> dict:
    """{테이블명: {"comment": ..., "columns": [(컬럼, 타입, 설명), ...]}}"""
    tables: dict = {}
    with conn.cursor() as cur:
        cur.execute(COLS_SQL, owner=OWNER)
        for tname, col, dtype, dlen, nullable, ccom, tcom in cur:
            t = tables.setdefault(f"{OWNER}.{tname}", {"comment": tcom, "columns": []})
            t["columns"].append((col, f"{dtype}({dlen})" if dtype.startswith(("VARCHAR", "CHAR")) else dtype, ccom))
    return tables


def load_json(path: str):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def tokens(text: str) -> set[str]:
    """한글은 2글자 조각, 영문·숫자는 단어 단위. 조사 차이를 흡수하려는 단순한 방식입니다."""
    text = text.lower()
    out = set(re.findall(r"[a-z0-9_]{2,}", text))
    for w in re.findall(r"[가-힣]+", text):
        out |= {w[i:i + 2] for i in range(len(w) - 1)}
    return out


def link_schema(question: str, tables: dict, glossary: dict, max_tables: int = 4):
    """질문과 겹치는 용어사전 항목을 고르고, 테이블 설명·컬럼 설명·용어 정의와 겹치는 정도로 테이블 점수를 매깁니다."""
    q = tokens(question)
    terms = {k: v for k, v in glossary.items() if tokens(k) & q or k in question}
    term_text = " ".join(terms.values()).upper()
    scored = []
    for name, t in tables.items():
        doc = t["comment"] + " " + " ".join(f"{c} {d}" for c, _, d in t["columns"])
        score = len(q & tokens(doc))
        if name in term_text or name.split(".")[1] in term_text:
            score += 5                          # 용어사전이 직접 가리키는 테이블은 가산점
        scored.append((score, name))
    scored.sort(reverse=True)
    picked = [n for s, n in scored if s > 0][:max_tables]
    if "DW.CMN_CODE_D" not in picked and re.search(r"코드|채널|분류|지역|비대면", question):
        picked.append("DW.CMN_CODE_D")      # 코드명을 보여 달라는 질문이면 공통코드를 함께
    return picked, terms


def schema_text(tables: dict, names: list[str]) -> str:
    lines = []
    for n in names:
        t = tables[n]
        lines.append(f"테이블 {n} -- {t['comment']}")
        lines += [f"  {c} {typ} -- {d}" for c, typ, d in t["columns"]]
    return "\n".join(lines)


if __name__ == "__main__":
    question = " ".join(sys.argv[1:]) or "지난달 영업일 기준 채널별 거래금액"
    with connect() as conn:
        tabs = load_tables(conn)
    names, terms = link_schema(question, tabs, load_json("glossary.json"))
    print("고른 테이블:", names)
    print("관련 용어:", list(terms))
    print(schema_text(tabs, names))
