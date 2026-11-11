package study.s02;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * temperature 실험. 최신 Claude 모델(예: Sonnet 5.5)은 temperature 를 보내면 400 오류를 돌려주므로,
 * temperature 를 받는 로컬 Ollama 모델로 실험합니다 (OpenAI 호환 엔드포인트를 HttpClient + Jackson 으로 호출).
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s02.Temperature
 */
public class Temperature {
    static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "gemma4:e4b");
    static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434/v1");
    static final String PROMPT = "정보계 야간 배치 장애 보고서의 제목을 15자 이내로 하나만 지어 주세요. 제목만 출력하세요.";
    static final int RUNS = 5;

    // 요청 · 응답 JSON 을 record 로 정의합니다 (필요한 필드만)
    record ChatMessage(String role, String content) {}

    record ChatRequest(String model, List<ChatMessage> messages, double temperature,
                       @JsonProperty("max_tokens") int maxTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(ChatMessage message) {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final ObjectMapper JSON = new ObjectMapper();

    static String sample(double temperature) throws Exception {
        var body = new ChatRequest(MODEL, List.of(new ChatMessage("user", PROMPT)), temperature, 40);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ollama")   // Ollama 는 키를 검사하지 않습니다
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return JSON.readValue(resp.body(), ChatResponse.class).choices().get(0).message().content().strip();
    }

    public static void main(String[] args) throws Exception {
        for (double t : new double[] {0.0, 0.7, 1.5}) {
            Map<String, Integer> kinds = new LinkedHashMap<>();
            for (int i = 0; i < RUNS; i++) kinds.merge(sample(t), 1, Integer::sum);
            System.out.printf("%n[temperature=%.1f] 서로 다른 답 %d개 / %d회%n", t, kinds.size(), RUNS);
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(kinds.entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            for (var e : sorted) System.out.printf("  %d회  %s%n", e.getValue(), e.getKey());
        }
    }
}
