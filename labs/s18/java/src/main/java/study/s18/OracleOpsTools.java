package study.s18;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Oracle 읽기 전용판 (--spring.profiles.active=oracle). 도구 이름 · 의미는 모의판과 같아 클라이언트는 바꿀 것이 없습니다.
 * Python 트랙 ops_server_oracle.py 와 같은 SQL · 같은 상한(10초 · 50행).
 */
@Component
@Profile("oracle")
public class OracleOpsTools {

    // 테이블명은 바인드 변수로 넣을 수 없으므로 허용 목록을 통과한 이름만 SQL 에 들어갑니다
    private static final Set<String> ALLOWED_TABLES =
            Set.of("ODS.ODS_ACCT_TXN", "STG.STG_ACCT_TXN", "ADW.ADW_ACCT_TXN_D", "ADW.ADW_BRNCH_DIM");
    private static final Pattern DATE = Pattern.compile("^\\d{8}$");

    private final JdbcTemplate jdbc;

    public OracleOpsTools(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);   // 이 도구 전용 설정 (공용 JdbcTemplate 은 건드리지 않음)
        this.jdbc.setQueryTimeout(10);              // 초
        this.jdbc.setMaxRows(50);
    }

    @McpTool(name = "get_batch_log",
            description = "정보계 야간 배치 작업의 실행 로그를 시간순으로 조회합니다 (읽기 전용). runDate 는 처리 기준일자 YYYYMMDD.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getBatchLog(
            @McpToolParam(description = "배치 작업 ID. 예: ADW_ACCT_TXN_DAILY") String jobName,
            @McpToolParam(description = "처리 기준일자 YYYYMMDD") String runDate,
            @McpToolParam(description = "ALL, ERROR, WARN, INFO 중 하나", required = false) String level,
            @McpToolParam(description = "최대 행 수 1~100. 기본 20", required = false) Integer limit) {
        requireDate(runDate);
        String lvl = (level == null || level.isBlank()) ? "ALL" : level;
        int n = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT TO_CHAR(LOG_TS, 'YYYY-MM-DD"T"HH24:MI:SS') TS, LOG_LEVEL, STEP_NM, SUBSTR(LOG_MSG, 1, 500) MSG
                  FROM OPS.BATCH_RUN_LOG
                 WHERE JOB_ID = ? AND RUN_DT = ? AND (? = 'ALL' OR LOG_LEVEL = ?)
                 ORDER BY LOG_TS
                 FETCH FIRST ? ROWS ONLY""", jobName.toUpperCase(), runDate, lvl, lvl, n);
        if (rows.isEmpty()) return "로그 없음";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            sb.append(r.get("TS")).append(" [").append(r.get("LOG_LEVEL")).append("] ")
              .append(r.get("STEP_NM")).append(": ").append(r.get("MSG")).append('\n');
        }
        return sb.toString().strip();
    }

    @McpTool(name = "get_row_count",
            description = "DW 테이블의 기준일자 적재 건수와 직전 기준일 건수를 돌려줍니다 (읽기 전용). "
                    + "tableName 은 '스키마.테이블' 형식이며 허용 목록에 있는 테이블만 조회합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getRowCount(
            @McpToolParam(description = "스키마.테이블. 예: STG.STG_ACCT_TXN") String tableName,
            @McpToolParam(description = "기준일자 YYYYMMDD") String baseDate) {
        String t = tableName.trim().toUpperCase();
        if (!ALLOWED_TABLES.contains(t)) {
            // 예외 메시지는 isError 결과로 모델에게 전달됩니다 → 고쳐서 다시 부를 수 있게 구체적으로
            throw new IllegalArgumentException(t + " 은 조회 허용 목록에 없습니다. 허용: " + ALLOWED_TABLES);
        }
        requireDate(baseDate);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT BASE_DT, COUNT(*) CNT FROM " + t
                + " WHERE BASE_DT IN (?, (SELECT MAX(BASE_DT) FROM " + t + " WHERE BASE_DT < ?))"
                + " GROUP BY BASE_DT ORDER BY BASE_DT DESC", baseDate, baseDate);
        if (rows.isEmpty()) return t + " " + baseDate + ": 적재 이력 없음";
        return String.join(" / ", rows.stream()
                .map(r -> t + " " + r.get("BASE_DT") + ": "
                        + String.format("%,d", ((Number) r.get("CNT")).longValue()) + "건")
                .toList());
    }

    @McpTool(name = "search_table_spec",
            description = "Oracle 데이터 사전(ALL_TAB_COLUMNS · 주석)에서 테이블 · 컬럼 정의를 찾습니다 (읽기 전용).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String searchTableSpec(
            @McpToolParam(description = "테이블명 또는 컬럼명 일부 (2자 이상)") String keyword) {
        String k = keyword == null ? "" : keyword.strip().toUpperCase();
        if (k.length() < 2) throw new IllegalArgumentException("keyword 는 2자 이상");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.OWNER || '.' || c.TABLE_NAME TBL, c.COLUMN_NAME COL,
                       c.DATA_TYPE || '(' || NVL(TO_CHAR(c.CHAR_LENGTH), TO_CHAR(c.DATA_PRECISION)) || ')' TY,
                       m.COMMENTS CM
                  FROM ALL_TAB_COLUMNS c
                  LEFT JOIN ALL_COL_COMMENTS m
                    ON m.OWNER = c.OWNER AND m.TABLE_NAME = c.TABLE_NAME AND m.COLUMN_NAME = c.COLUMN_NAME
                 WHERE c.OWNER IN ('ODS', 'STG', 'ADW')
                   AND (c.TABLE_NAME LIKE '%' || ? || '%' OR c.COLUMN_NAME LIKE '%' || ? || '%')
                 ORDER BY 1, c.COLUMN_ID
                 FETCH FIRST 40 ROWS ONLY""", k, k);
        if (rows.isEmpty()) return "일치 항목 없음";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            Object cm = r.get("CM");
            sb.append(r.get("TBL")).append('.').append(r.get("COL")).append(' ').append(r.get("TY"))
              .append(' ').append(cm == null ? "" : cm).append('\n');
        }
        return sb.toString().strip();
    }

    private static void requireDate(String d) {
        if (d == null || !DATE.matcher(d).matches()) {
            throw new IllegalArgumentException("날짜는 YYYYMMDD 8자리입니다. 받은 값: " + d);
        }
    }
}
