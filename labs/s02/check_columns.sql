-- LLM 이 만든 SQL 의 컬럼이 실제로 있는지 확인 (읽기 전용 계정)
SELECT c.column_name,
       CASE WHEN t.column_name IS NULL THEN '없음' ELSE '있음' END AS exists_yn
  FROM (SELECT 'CUST_ID' AS column_name FROM dual UNION ALL
        SELECT 'GRD_CD'           FROM dual UNION ALL
        SELECT 'GRD_CHG_RSN_CD'   FROM dual) c
  LEFT JOIN all_tab_columns t
    ON t.owner = 'ADW'
   AND t.table_name = 'TB_ADW_CUST_GRD_HIST'
   AND t.column_name = c.column_name;
