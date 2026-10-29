"""3단계: 메타데이터를 문장으로 풀어 임베딩하고 저장 — python load.py meta.csv"""
import csv
import os
import sys

import psycopg
from pgvector.psycopg import register_vector
from sentence_transformers import SentenceTransformer

PGURL = os.getenv("PGURL", "postgresql://postgres:study@localhost:5432/meta")
model = SentenceTransformer("intfloat/multilingual-e5-small")


def to_sentence(r: dict) -> str:
    """표의 한 행을 검색이 잘 되는 문장으로 바꿉니다 (물리명 + 한글명 + 설명)."""
    if r["kind"] == "column":
        return (f"테이블 {r['table_name']} 의 컬럼 {r['column_name']} ({r['title']}): "
                f"{r['description']}")
    return f"용어 {r['title']}: {r['description']}"


def main(path: str):
    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    texts = [to_sentence(r) for r in rows]
    vecs = model.encode([f"passage: {t}" for t in texts],
                        normalize_embeddings=True, batch_size=64, show_progress_bar=True)

    with psycopg.connect(PGURL) as conn:
        register_vector(conn)                 # numpy 배열 ↔ vector 타입 변환 등록
        with conn.cursor() as cur:
            cur.execute("TRUNCATE meta_chunk")
            for r, text, vec in zip(rows, texts, vecs):
                cur.execute(
                    """INSERT INTO meta_chunk (kind, table_name, column_name, title, content, embedding)
                       VALUES (%s, %s, %s, %s, %s, %s)""",
                    (r["kind"], r["table_name"] or None, r["column_name"] or None,
                     r["title"], text, vec),
                )
        conn.commit()
    print(f"{len(rows)}건 저장 완료")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "meta.csv")
