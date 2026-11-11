package study.s18;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 읽기 도구 3개 (모의 데이터판). 세션 17 의 OpsTools 를 그대로 감쌉니다 — Python 의 ops_server.py 와 같은 구조.
 * 메서드가 던진 RuntimeException(OpsTools.ToolError 포함)은 Spring AI 가 isError 결과로 바꿔 모델에 전달합니다.
 */
@Component
@Profile("!oracle")
public class OpsReadTools {

    private final OpsTools ops;

    public OpsReadTools(OpsTools ops) {
        this.ops = ops;
    }

    @McpTool(name = "get_batch_log",
            description = "정보계 야간 배치 작업의 실행 로그를 시간순으로 조회합니다 (읽기 전용). "
                    + "배치 실패·지연 원인을 찾을 때 가장 먼저 씁니다. '어젯밤 배치'는 보통 어제 날짜가 기준일자입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getBatchLog(
            @McpToolParam(description = "배치 작업 ID. 예: ADW_ACCT_TXN_DAILY") String jobName,
            @McpToolParam(description = "처리 기준일자 YYYYMMDD. 예: 20261028") String runDate,
            @McpToolParam(description = "ALL, ERROR, WARN, INFO 중 하나. 기본 ALL", required = false) String level,
            @McpToolParam(description = "최대 행 수 1~100. 기본 20", required = false) Integer limit) {
        return ops.getBatchLog(jobName, runDate,
                level == null || level.isBlank() ? "ALL" : level,
                limit == null ? 20 : limit);
    }

    @McpTool(name = "get_row_count",
            description = "DW 테이블의 기준일자 적재 건수와 직전 기준일 건수를 돌려줍니다 (읽기 전용). "
                    + "tableName 은 '스키마.테이블' 형식 (예: STG.STG_ACCT_TXN). 모르면 search_table_spec 먼저.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String getRowCount(
            @McpToolParam(description = "스키마.테이블. 예: STG.STG_ACCT_TXN") String tableName,
            @McpToolParam(description = "기준일자 YYYYMMDD") String baseDate) {
        return ops.getRowCount(tableName, baseDate);
    }

    @McpTool(name = "search_table_spec",
            description = "테이블 정의서를 키워드로 검색해 컬럼 타입·길이, 적재 작업, 원천, 비고를 돌려줍니다 (읽기 전용). "
                    + "결과는 최대 5개.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public String searchTableSpec(
            @McpToolParam(description = "테이블명, 컬럼명, 한글 업무 용어 (2자 이상). 예: BRNCH_CD, 지점코드") String keyword) {
        return ops.searchTableSpec(keyword);
    }
}
