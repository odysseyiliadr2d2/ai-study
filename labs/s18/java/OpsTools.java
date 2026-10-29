package com.example.opsmcp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 정보계 운영 도구 (읽기 전용). Python 서버(ops_server_oracle.py)와 같은 도구 이름·의미를 유지합니다. */
@Component
public class OpsTools {

    private static final Set<String> ALLOWED_TABLES =
            Set.of("ODS.ODS_ACCT_TXN", "STG.STG_ACCT_TXN", "ADW.ADW_ACCT_TXN_D", "ADW.ADW_BRNCH_DIM");
    private static final Pattern DATE = Pattern.compile("^\\d{8}$");

    private final JdbcTemplate jdbc;

    public OpsTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.jdbc.setQueryTimeout(10);   // 초
        this.jdbc.setMaxRows(100);
    }

    @McpTool(name = "get_batch_log",
            description = "정보계 야간 배치 작업의 실행 로그를 시간순으로 조회합니다 (읽기 전용). "
                    + "배치 실패·지연 원인을 찾을 때 가장 먼저 씁니다. run_date 는 처리 기준일자 YYYYMMDD 입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getBatchLog(
            @McpToolParam(description = "배치 작업 ID. 예: ADW_ACCT_TXN_DAILY") String jobName,
            @McpToolParam(description = "처리 기준일자 YYYYMMDD") String runDate,
            @McpToolParam(description = "ALL, ERROR, WARN, INFO 중 하나", required = false) String level) {
        requireDate(runDate);
        String lvl = (level == null || level.isBlank()) ? "ALL" : level;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT TO_CHAR(LOG_TS, 'YYYY-MM-DD"T"HH24:MI:SS') TS, LOG_LEVEL, STEP_NM, SUBSTR(LOG_MSG, 1, 500) MSG
                  FROM OPS.BATCH_RUN_LOG
                 WHERE JOB_ID = ? AND RUN_DT = ? AND (? = 'ALL' OR LOG_LEVEL = ?)
                 ORDER BY LOG_TS
                 FETCH FIRST 50 ROWS ONLY""", jobName.toUpperCase(), runDate, lvl, lvl);
        if (rows.isEmpty()) return jobName + " / " + runDate + " 로그 없음";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            sb.append(r.get("TS")).append(" [").append(r.get("LOG_LEVEL")).append("] ")
              .append(r.get("STEP_NM")).append(": ").append(r.get("MSG")).append('\n');
        }
        return sb.toString();
    }

    @McpTool(name = "get_row_count",
            description = "DW 테이블의 기준일자 적재 건수와 직전 기준일 건수를 돌려줍니다 (읽기 전용). "
                    + "table_name 은 '스키마.테이블' 형식이며 허용 목록에 있는 테이블만 조회합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getRowCount(
            @McpToolParam(description = "스키마.테이블. 예: STG.STG_ACCT_TXN") String tableName,
            @McpToolParam(description = "기준일자 YYYYMMDD") String baseDate) {
        String t = tableName.trim().toUpperCase();
        if (!ALLOWED_TABLES.contains(t)) {
            // 예외 메시지는 is_error 결과로 모델에게 전달됩니다 → 고쳐서 다시 부를 수 있게 구체적으로
            throw new IllegalArgumentException(t + " 은 조회 허용 목록에 없습니다. 허용: " + ALLOWED_TABLES);
        }
        requireDate(baseDate);
        // 테이블명은 허용 목록을 통과한 값만 들어가고, 날짜는 바인드 변수입니다
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT BASE_DT, COUNT(*) CNT FROM " + t
                + " WHERE BASE_DT IN (?, (SELECT MAX(BASE_DT) FROM " + t + " WHERE BASE_DT < ?))"
                + " GROUP BY BASE_DT ORDER BY BASE_DT DESC", baseDate, baseDate);
        if (rows.isEmpty()) return t + " " + baseDate + ": 적재 이력 없음";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            sb.append(t).append(' ').append(r.get("BASE_DT")).append(": ")
              .append(String.format("%,d", ((Number) r.get("CNT")).longValue())).append("건 / ");
        }
        return sb.substring(0, sb.length() - 3);
    }

    private static void requireDate(String d) {
        if (d == null || !DATE.matcher(d).matches()) {
            throw new IllegalArgumentException("날짜는 YYYYMMDD 8자리입니다. 받은 값: " + d);
        }
    }
}
