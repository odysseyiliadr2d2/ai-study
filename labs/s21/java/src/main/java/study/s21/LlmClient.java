package study.s21;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * 사내 vLLM · Ollama · llama.cpp 의 OpenAI 호환 /v1/chat/completions 를 JDK HttpClient + Jackson 으로 부릅니다.
 * LLM_BASE_URL, LLM_API_KEY, LLM_MODEL 만 바꾸면 같은 코드로 대상을 바꿉니다. (외부 SDK 없이 폐쇄망 반입이 쉬움)
 *
 *   ./mvnw -q compile exec:java -Dexec.args="야간 배치 재처리 절차를 요약해 주세요"
 *   LLM_STREAM=0 ./mvnw -q compile exec:java      # 스트리밍 없이 한 번에
 */
public final class LlmClient {

    public static final String BASE_URL = env("LLM_BASE_URL", "http://localhost:8000/v1");
    public static final String API_KEY = env("LLM_API_KEY", "change-me");
    public static final String MODEL = env("LLM_MODEL", "qwen3.5-9b");   // vllm serve --served-model-name 과 같게

    static final ObjectMapper JSON = new ObjectMapper();
    static final String SYSTEM = "당신은 증권사 정보계 운영 문서를 요약하는 도우미입니다. 한국어로, 5줄 이내로 답합니다.";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 요청 본문. 로컬 오픈웨이트 모델이므로 temperature 를 직접 정합니다 (Claude 5.5 와 달리 허용). */
    static ObjectNode body(String model, String system, String user, int maxTokens, double temperature, boolean stream) {
        ObjectNode b = JSON.createObjectNode();
        b.put("model", model);
        b.put("max_tokens", maxTokens);
        b.put("temperature", temperature);
        b.put("stream", stream);
        ArrayNode msgs = b.putArray("messages");
        if (system != null) msgs.addObject().put("role", "system").put("content", system);
        msgs.addObject().put("role", "user").put("content", user);
        return b;
    }

    HttpRequest request(ObjectNode body) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /** 한 번에 받기 */
    public String ask(String question) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(request(body(MODEL, SYSTEM, question, 600, 0.2, false)),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        return JSON.readTree(res.body()).at("/choices/0/message/content").asText();
    }

    /**
     * 스트리밍(SSE): 'data: {json}' 줄마다 choices[0].delta.content 조각을 onToken 으로 넘깁니다. 'data: [DONE]' 에서 끝.
     * 돌려주는 값은 받은 조각 수 (출력 토큰 수의 근사치).
     */
    public int stream(ObjectNode body, Consumer<String> onToken) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = http.send(request(body), HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            try (InputStream in = res.body()) {
                throw new IOException("HTTP " + res.statusCode() + ": " + new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        int chunks = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("data:")) continue;          // 빈 줄 · 주석 줄 건너뜀
                String data = line.substring(5).strip();
                if (data.equals("[DONE]")) break;
                JsonNode delta = JSON.readTree(data).at("/choices/0/delta/content");
                if (delta.isTextual() && !delta.asText().isEmpty()) {
                    chunks++;
                    onToken.accept(delta.asText());
                }
            }
        }
        return chunks;
    }

    static String env(String k, String def) {
        return System.getenv().getOrDefault(k, def);
    }

    public static void main(String[] args) throws Exception {
        String q = args.length > 0 ? String.join(" ", args) : "야간 배치 재처리 절차를 신규 담당자에게 설명하듯 요약해 주세요.";
        LlmClient c = new LlmClient();
        System.out.println("[" + BASE_URL + " · " + MODEL + "]");
        if ("0".equals(env("LLM_STREAM", "1"))) {
            System.out.println(c.ask(q));
        } else {
            c.stream(body(MODEL, SYSTEM, q, 600, 0.2, true), t -> {
                System.out.print(t);
                System.out.flush();
            });
            System.out.println();
        }
    }
}
