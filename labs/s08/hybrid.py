"""실무형: 물리명 정확 일치 + 의미 검색 합치기 — python hybrid.py "CUST_GRD_CD 뜻" """
import re
import sys

import psycopg
from pgvector.psycopg import register_vector

from search import PGURL, search

CODE_RE = re.compile(r"\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\b")   # 밑줄 있는 물리명 (예: CUST_GRD_CD, TB_BATCH_LOG)


def exact(names: list[str]):
    if not names:
        return []
    with psycopg.connect(PGURL) as conn:
        register_vector(conn)
        return conn.execute(
            """SELECT kind, table_name, column_name, title, 1.0 AS similarity
               FROM meta_chunk
               WHERE upper(column_name) = ANY(%s) OR upper(table_name) = ANY(%s)
               LIMIT 10""",
            (names, names),
        ).fetchall()


def hybrid(question: str, k: int = 5, min_sim: float = 0.80):
    names = CODE_RE.findall(question.upper())
    hits = exact(names)                                   # 1) 물리명이 있으면 정확 일치가 먼저
    seen = {(h[1], h[2], h[3]) for h in hits}
    for h in search(question, k=k * 2):                   # 2) 의미 검색으로 나머지 채우기
        if h[4] >= min_sim and (h[1], h[2], h[3]) not in seen:
            hits.append(h)
            seen.add((h[1], h[2], h[3]))
    return hits[:k]


if __name__ == "__main__":
    q = " ".join(sys.argv[1:]) or "CUST_GRD_CD 코드값이 뭐예요?"
    rows = hybrid(q)
    if not rows:
        print("비슷한 항목이 없습니다. 데이터팀 메타데이터 담당자에게 문의하세요.")
    for kind, table, column, title, sim in rows:
        print(f"{sim:.3f}  {(table or '(용어)')}.{column or ''}  {title}")
