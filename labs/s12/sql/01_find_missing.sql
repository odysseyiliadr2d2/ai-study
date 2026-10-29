-- 01_find_missing.sql : 코멘트가 비어 있는 테이블·컬럼 찾기 (읽기 전용 계정으로 실행)
-- :owner 에 대상 스키마(예: 'MART')를 넣습니다. SQL Developer / SQL*Plus 에서는 바인드 변수로 입력합니다.

-- 1) 스키마별 코멘트 채움률 (보고용 한 줄)
SELECT c.owner,
       COUNT(*)                                             AS col_cnt,
       COUNT(CASE WHEN TRIM(cc.comments) IS NOT NULL THEN 1 END) AS commented_cnt,
       ROUND(100 * COUNT(CASE WHEN TRIM(cc.comments) IS NOT NULL THEN 1 END) / COUNT(*), 1) AS fill_pct
FROM   all_tab_columns c
JOIN   all_tables t
       ON t.owner = c.owner AND t.table_name = c.table_name      -- 뷰는 빼고 테이블만
LEFT JOIN all_col_comments cc
       ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
WHERE  c.owner = :owner
GROUP  BY c.owner;

-- 2) 테이블 코멘트가 없는 테이블
SELECT t.table_name, t.num_rows, t.last_analyzed
FROM   all_tables t
LEFT JOIN all_tab_comments tc
       ON tc.owner = t.owner AND tc.table_name = t.table_name
WHERE  t.owner = :owner
AND    TRIM(tc.comments) IS NULL
ORDER  BY t.table_name;

-- 3) 컬럼 코멘트가 없거나, 컬럼명을 그대로 옮겨 적은 '무늬만 코멘트'인 컬럼
SELECT c.table_name, c.column_id, c.column_name, c.data_type, c.data_length, c.nullable,
       cc.comments,
       CASE WHEN TRIM(cc.comments) IS NULL THEN '없음'
            WHEN UPPER(REPLACE(REPLACE(cc.comments, ' ', ''), '_', '')) = REPLACE(c.column_name, '_', '') THEN '컬럼명과 같음'
            WHEN LENGTH(TRIM(cc.comments)) < 3 THEN '너무 짧음'
       END AS issue
FROM   all_tab_columns c
JOIN   all_tables t
       ON t.owner = c.owner AND t.table_name = c.table_name
LEFT JOIN all_col_comments cc
       ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
WHERE  c.owner = :owner
AND   (TRIM(cc.comments) IS NULL
       OR UPPER(REPLACE(REPLACE(cc.comments, ' ', ''), '_', '')) = REPLACE(c.column_name, '_', '')
       OR LENGTH(TRIM(cc.comments)) < 3)
ORDER  BY c.table_name, c.column_id;

-- 4) 같은 컬럼명인데 테이블마다 설명이 다른 경우 (용어 불일치 후보, LISTAGG DISTINCT 는 19c 이상)
SELECT cc.column_name,
       COUNT(DISTINCT cc.comments) AS variants,
       LISTAGG(DISTINCT cc.table_name || ': ' || cc.comments, ' | ' ON OVERFLOW TRUNCATE)
         WITHIN GROUP (ORDER BY cc.table_name || ': ' || cc.comments) AS examples
FROM   all_col_comments cc
JOIN   all_tables t
       ON t.owner = cc.owner AND t.table_name = cc.table_name
WHERE  cc.owner = :owner
AND    TRIM(cc.comments) IS NOT NULL
GROUP  BY cc.column_name
HAVING COUNT(DISTINCT cc.comments) > 1
ORDER  BY variants DESC, cc.column_name;
