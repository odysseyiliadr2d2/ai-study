package study.s18;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * ops-dw MCP 서버. 도구는 OpsReadTools · RerunTool(쓰기) · OracleOpsTools(oracle 프로파일)에 있고,
 * 이 클래스에는 리소스 · 프롬프트를 둡니다. proxyBeanMethods = false 라 프록시 없이 스캔됩니다.
 */
@SpringBootApplication(proxyBeanMethods = false)
public class OpsMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsMcpApplication.class, args);
    }

    /** 세션 17 의 도구 함수 (OPS_MOCK_DIR · OPS_OUTBOX · OPS_TODAY 환경변수를 그대로 읽음) */
    @Bean
    OpsTools opsTools() {
        return new OpsTools();
    }

    /** 정의서에 등록된 테이블 목록 — 클라이언트가 컨텍스트로 붙여 쓰는 읽기 자료 */
    @McpResource(uri = "ops://tables", name = "tables", mimeType = "application/json",
            description = "정의서에 등록된 테이블 목록 (테이블 · 한글명 · 적재 작업)")
    public String tableList() {
        try {
            String dir = System.getenv().getOrDefault("OPS_MOCK_DIR", "mock");
            ObjectMapper json = new ObjectMapper();
            JsonNode specs = json.readTree(Files.readString(Path.of(dir, "table_specs.json"), StandardCharsets.UTF_8));
            ArrayNode out = json.createArrayNode();
            specs.forEach(t -> out.addObject()
                    .put("table", t.path("table").asText())
                    .put("name", t.path("name").asText())
                    .put("load_job", t.path("load_job").asText()));
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 배치 장애 분석 보고서 프롬프트 템플릿 (사용자가 골라 쓰는 정형 질문) */
    @McpPrompt(name = "incident_report", description = "배치 장애 분석 보고서 프롬프트 템플릿")
    public String incidentReport(
            @McpArg(name = "job_name", description = "배치 작업 ID", required = true) String jobName,
            @McpArg(name = "run_date", description = "처리 기준일자 YYYYMMDD", required = true) String runDate) {
        return jobName + " 작업의 " + runDate + " 기준 실행이 실패했습니다. 로그, 관련 테이블 건수, 테이블 정의서를 확인해 "
                + "① 현상 ② 원인(근거 로그·수치) ③ 조치안 ④ 재실행 필요 여부 형식으로 정리하세요.";
    }
}
