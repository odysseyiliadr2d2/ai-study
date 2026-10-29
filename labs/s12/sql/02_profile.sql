-- 02_profile.sql : 컬럼 프로파일링 (NULL 비율 · 고유값 수 · 최소/최대 · 상위 값)
-- 1) 통계 정보로 빠르게: 테이블을 읽지 않고 옵티마이저 통계(ALL_TAB_COL_STATISTICS)를 봅니다.
--    LAST_ANALYZED 가 오래됐으면 숫자도 오래된 것입니다. LOW/HIGH_VALUE 는 RAW 라 사람이 읽기 어렵습니다.
SELECT s.table_name, s.column_name,
       t.num_rows,
       s.num_nulls,
       ROUND(100 * s.num_nulls / NULLIF(t.num_rows, 0), 1) AS null_pct,
       s.num_distinct,
       s.last_analyzed
FROM   all_tab_col_statistics s
JOIN   all_tables t ON t.owner = s.owner AND t.table_name = s.table_name
WHERE  s.owner = :owner
AND    s.table_name = :table_name
ORDER  BY s.column_name;

-- 2) 컬럼 하나를 직접 세기: 대형 테이블은 기간 조건이나 SAMPLE 절을 꼭 붙입니다.
--    아래는 MART.TRD_CHNL_DAY_M 의 CHNL_CD 를 최근 1개월만 보는 예입니다.
SELECT COUNT(*)                                        AS row_cnt,
       COUNT(chnl_cd)                                  AS non_null_cnt,
       ROUND(100 * (COUNT(*) - COUNT(chnl_cd)) / NULLIF(COUNT(*), 0), 2) AS null_pct,
       COUNT(DISTINCT chnl_cd)                         AS distinct_cnt,
       MIN(chnl_cd)                                    AS min_val,
       MAX(chnl_cd)                                    AS max_val
FROM   mart.trd_chnl_day_m
WHERE  base_dt >= TO_CHAR(ADD_MONTHS(SYSDATE, -1), 'YYYYMMDD');

-- 3) 상위 값 분포 (코드값 사전과 비교할 때)
SELECT chnl_cd, COUNT(*) AS cnt,
       ROUND(100 * RATIO_TO_REPORT(COUNT(*)) OVER (), 2) AS pct
FROM   mart.trd_chnl_day_m SAMPLE (5)          -- 5% 표본. 작은 테이블이면 SAMPLE 절을 뺍니다
GROUP  BY chnl_cd
ORDER  BY cnt DESC
FETCH FIRST 20 ROWS ONLY;

-- 4) 품질 지표 예시: 유효성(코드값 사전에 없는 값) · 일관성(마트와 DW 합계 비교) · 적시성(최근 적재일)
SELECT 'VALIDITY'    AS rule, COUNT(*) AS bad_cnt
FROM   mart.trd_chnl_day_m m
WHERE  NOT EXISTS (SELECT 1 FROM dw.cmn_code_d c WHERE c.code_grp = 'CHNL' AND c.code_val = m.chnl_cd)
UNION ALL
SELECT 'CONSISTENCY', ABS(NVL((SELECT SUM(trd_amt) FROM mart.trd_chnl_day_m WHERE base_dt = :base_dt), 0)
                        - NVL((SELECT SUM(trd_amt) FROM dw.trd_day_f      WHERE base_dt = :base_dt), 0))
FROM   dual
UNION ALL
SELECT 'TIMELINESS_DAYS', TRUNC(SYSDATE) - TO_DATE(MAX(base_dt), 'YYYYMMDD')
FROM   mart.trd_chnl_day_m;
