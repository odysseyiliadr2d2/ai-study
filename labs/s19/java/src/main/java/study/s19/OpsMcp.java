package study.s19;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 세션 18 MCP 서버 연결 (MCP Java SDK 2.x 동기 클라이언트).
 *
 * 기본은 stdio: Python 서버를 자식 프로세스로 띄웁니다. OPS_MCP_URL 이 있으면 Streamable HTTP 로 붙습니다.
 * MCP 도구 정의(inputSchema)를 Anthropic Messages API 의 Tool 로 바꿔 주는 일도 여기서 합니다.
 */
public final class OpsMcp implements AutoCloseable {

    /** 도구 실행 결과. error 가 true 면 tool_result 의 is_error 로 모델에 전달합니다. */
    public record Result(String text, boolean error) {}

    private final McpSyncClient client;
    private final Map<String, McpSchema.Tool> tools = new LinkedHashMap<>();

    private OpsMcp(McpClientTransport transport) {
        this.client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("s19-ops-agent-java", "1.0.0").build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        for (McpSchema.Tool t : client.listTools().tools()) {
            tools.put(t.name(), t);
        }
    }

    /** serverEnv: stdio 서버에 줄 환경변수 (예: OPS_ENABLE_WRITE=1). HTTP 연결이면 서버 쪽 설정을 따릅니다. */
    public static OpsMcp connect(Map<String, String> serverEnv) {
        String url = System.getenv("OPS_MCP_URL");
        if (url != null && !url.isBlank()) {
            URI u = URI.create(url);
            String token = System.getenv().getOrDefault("OPS_MCP_TOKEN", "");
            var http = HttpClientStreamableHttpTransport.builder(u.getScheme() + "://" + u.getAuthority())
                    .endpoint(u.getPath());
            if (!token.isBlank()) {
                http.addHttpRequestCustomizer((req, method, endpoint, body, ctx) ->
                        req.header("Authorization", "Bearer " + token));
            }
            return new OpsMcp(http.build());
        }
        ServerParameters params = ServerParameters.builder(System.getenv().getOrDefault("OPS_PYTHON", ".venv/bin/python"))
                .args(System.getenv().getOrDefault("OPS_SERVER", "ops_server.py"))
                .env(serverEnv)
                .build();
        // stdio 에서는 서버의 stdout 이 프로토콜 전용입니다. 서버 로그는 stderr 로 넘어옵니다.
        return new OpsMcp(new StdioClientTransport(params, McpJsonDefaults.getMapper()));
    }

    public Set<String> toolNames() {
        return tools.keySet();
    }

    /** 허용 목록에 있는 도구만 Anthropic Tool 로 바꿔 돌려줍니다. 목록 밖 도구는 모델에게 보이지 않습니다. */
    public List<Tool> anthropicTools(Set<String> allow) {
        return tools.values().stream()
                .filter(t -> allow.contains(t.name()))
                .map(OpsMcp::toAnthropic)
                .collect(Collectors.toList());
    }

    static Tool toAnthropic(McpSchema.Tool t) {
        Map<String, Object> schema = t.inputSchema() == null ? Map.of() : t.inputSchema();
        var props = Tool.InputSchema.Properties.builder();
        Object p = schema.getOrDefault("properties", Map.of());
        if (p instanceof Map<?, ?> m) {
            m.forEach((k, v) -> props.putAdditionalProperty(String.valueOf(k), JsonValue.from(v)));
        }
        List<String> required = schema.get("required") instanceof List<?> r
                ? r.stream().map(String::valueOf).collect(Collectors.toList())
                : List.of();
        return Tool.builder()
                .name(t.name())
                .description(Objects.requireNonNullElse(t.description(), t.name()))
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    /** MCP tools/call. 서버가 isError 로 돌려준 입력 오류도 문장 그대로 모델에 넘깁니다. */
    public Result call(String name, Map<String, Object> args) {
        McpSchema.CallToolResult r = client.callTool(McpSchema.CallToolRequest.builder(name).arguments(args).build());
        String text = r.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .collect(Collectors.joining("\n"));
        return new Result(text, Boolean.TRUE.equals(r.isError()));
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
