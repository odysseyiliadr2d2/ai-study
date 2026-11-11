package study.s18;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 쓰기 도구. ops.enable-write=true (환경변수 OPS_ENABLE_WRITE=true) 일 때만 빈이 생겨 도구로 등록됩니다.
 * 승인 절차는 클라이언트(에이전트)에서 겁니다. Oracle 읽기 전용판에는 넣지 않습니다.
 */
@Component
@Profile("!oracle")
@ConditionalOnProperty(prefix = "ops", name = "enable-write", havingValue = "true")
public class RerunTool {

    private final OpsTools ops;

    public RerunTool(OpsTools ops) {
        this.ops = ops;
    }

    @McpTool(name = "request_rerun",
            description = "배치 재실행 요청을 운영 요청함에 등록합니다 (쓰기). 배치를 직접 실행하지는 않습니다. "
                    + "원인과 선행 조치가 확인된 뒤 사람 승인을 받고 호출합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public String requestRerun(
            @McpToolParam(description = "배치 작업 ID") String jobName,
            @McpToolParam(description = "처리 기준일자 YYYYMMDD") String runDate,
            @McpToolParam(description = "재시작 블록. 예: STG_LOAD") String fromStep,
            @McpToolParam(description = "원인과 선행 조치 요약 (10자 이상)") String reason) {
        return ops.requestRerun(jobName, runDate, fromStep, reason);
    }
}
