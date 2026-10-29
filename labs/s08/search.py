"""4단계: 의미 검색 — python search.py "고객 등급이 바뀐 이력은 어디에 있나요?" """
import os
import sys

import psycopg
from pgvector.psycopg import register_vector
from sentence_transformers import SentenceTransformer

PGURL = os.getenv("PGURL", "postgresql://postgres:study@localhost:5432/meta")
model = SentenceTransformer("intfloat/multilingual-e5-small")

SQL = """
SELECT kind, table_name, column_name, title,
       1 - (embedding <=> %(q)s) AS similarity      -- <=> 는 코사인 '거리', 1 - 거리 = 유사도
FROM meta_chunk
WHERE (%(kind)s::text IS NULL OR kind = %(kind)s)
ORDER BY embedding <=> %(q)s                         -- 거리 오름차순 = 가까운 순
LIMIT %(k)s
"""


def search(question: str, k: int = 5, kind: str | None = None):
    q = model.encode(f"query: {question}", normalize_embeddings=True)
    with psycopg.connect(PGURL) as conn:
        register_vector(conn)
        conn.execute("SET hnsw.ef_search = 100")    # 후보 목록 크기 (기본 40) — 크면 정확, 느림
        return conn.execute(SQL, {"q": q, "k": k, "kind": kind}).fetchall()


if __name__ == "__main__":
    question = " ".join(sys.argv[1:]) or "고객 등급이 바뀐 이력은 어디에 있나요?"
    for kind, table, column, title, sim in search(question):
        where = f"{table}.{column}" if table else "(용어)"
        print(f"{sim:.3f}  {where:<28} {title}")
