-- 2단계: 확장 · 테이블 · 인덱스  (docker exec -i pgvector psql -U postgres -d meta < schema.sql)
CREATE EXTENSION IF NOT EXISTS vector;      -- 확장 이름은 pgvector 가 아니라 vector

DROP TABLE IF EXISTS meta_chunk;
CREATE TABLE meta_chunk (
    id          bigserial PRIMARY KEY,
    kind        text        NOT NULL,        -- 'column' | 'term'
    table_name  text,                        -- 물리 테이블명 (용어는 NULL)
    column_name text,                        -- 물리 컬럼명
    title       text        NOT NULL,        -- 화면에 보여 줄 이름
    content     text        NOT NULL,        -- 임베딩한 원문 문장
    updated_at  date        NOT NULL DEFAULT current_date,
    embedding   vector(384) NOT NULL         -- multilingual-e5-small 차원
);

-- 근사 최근접 인덱스: 코사인 거리(<=>) 로 찾을 것이므로 vector_cosine_ops
-- m(기본 16) · ef_construction(기본 64) 는 기본값으로 시작
CREATE INDEX meta_chunk_embedding_hnsw
    ON meta_chunk USING hnsw (embedding vector_cosine_ops);

-- 필터에 자주 쓰는 컬럼은 일반 인덱스
CREATE INDEX meta_chunk_kind_idx ON meta_chunk (kind);
