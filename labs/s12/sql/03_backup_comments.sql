-- 03_backup_comments.sql : 반영 전에 현재 코멘트를 되돌리기용 스크립트로 떠 둡니다
SELECT 'COMMENT ON COLUMN ' || cc.owner || '.' || cc.table_name || '.' || cc.column_name
       || ' IS ''' || REPLACE(NVL(cc.comments, ''), '''', '''''') || ''';' AS rollback_stmt
FROM   all_col_comments cc
JOIN   all_tables t ON t.owner = cc.owner AND t.table_name = cc.table_name
WHERE  cc.owner = :owner
ORDER  BY cc.table_name, cc.column_name;
