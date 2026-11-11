package study.s21;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 최소 LLM 게이트웨이 — 사내 클라이언트는 이 주소 하나만 알고, 뒤의 vLLM·Ollama 는 바꿔 끼웁니다.
 *  - 10초마다 각 백엔드의 GET /v1/models 로 상태 확인, 요청은 살아 있는 첫 백엔드로 전달 (순서 = 우선순위)
 *  - 클라이언트 키 검사(GATEWAY_KEY), 감사 로그는 본문 대신 길이·해시만 남김
 *  - 스트리밍 응답은 그대로 흘려보냄
 *
 *   export LLM_BACKENDS="vllm=http://gpu01:8000/v1|change-me,ollama=http://localhost:11434/v1|ollama"
 *   export GATEWAY_KEY=team-key
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s21.MiniGateway      # http://0.0.0.0:9000/v1
 *
 * 학습용 최소 구현입니다. 운영에서는 사내 표준 API 게이트웨이(인증·속도 제한·로그 수집)를 앞에 둡니다.
 */
public final class MiniGateway {

    record Backend(String name, String baseUrl, String apiKey) {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final Map<String, Boolean> HEALTHY = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        List<Backend> backends = parse(System.getenv().getOrDefault("LLM_BACKENDS",
                "vllm=http://localhost:8000/v1|change-me,ollama=http://localhost:11434/v1|ollama"));
        String gatewayKey = System.getenv().getOrDefault("GATEWAY_KEY", "");
        int port = Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT", "9000"));

        ScheduledExecutorService health = Executors.newSingleThreadScheduledExecutor();
        health.scheduleAtFixedRate(() -> backends.forEach(b -> {
            boolean ok = isHealthy(b);
            Boolean before = HEALTHY.put(b.name(), ok);
            if (before == null || before != ok) System.err.printf("%s [health] %s → %s%n", LocalDateTime.now(), b.name(), ok ? "UP" : "DOWN");
        }), 0, 10, TimeUnit.SECONDS);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/healthz", ex -> reply(ex, 200, HEALTHY.toString()));
        server.createContext("/v1/", ex -> {
            if (!gatewayKey.isEmpty() && !("Bearer " + gatewayKey).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                reply(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Backend b = backends.stream().filter(x -> HEALTHY.getOrDefault(x.name(), false)).findFirst().orElse(null);
            if (b == null) {
                reply(ex, 503, "{\"error\":\"no healthy backend\"}");
                return;
            }
            forward(ex, b);
        });
        server.start();
        System.out.println("게이트웨이 http://0.0.0.0:" + port + "/v1 → "
                + backends.stream().map(b -> b.name() + "=" + b.baseUrl()).toList());   // 키는 출력하지 않음
    }

    static void forward(HttpExchange ex, Backend b) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        String path = ex.getRequestURI().getPath().substring("/v1".length());   // /chat/completions 등
        long t0 = System.nanoTime();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(b.baseUrl() + path))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + b.apiKey());
        req = "GET".equals(ex.getRequestMethod()) ? req.GET() : req.POST(HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<InputStream> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            res.headers().firstValue("Content-Type").ifPresent(ct -> ex.getResponseHeaders().set("Content-Type", ct));
            ex.sendResponseHeaders(res.statusCode(), 0);           // 0 = 길이 미정(청크 전송) → 스트리밍 통과
            try (InputStream in = res.body(); OutputStream out = ex.getResponseBody()) {
                in.transferTo(out);
            }
            // 감사 로그: 프롬프트 원문 대신 길이와 해시만 (원문 보관은 사내 정책에 따름)
            System.out.printf("%s %s %s → %s %d %dms req=%dB sha256=%s%n", LocalDateTime.now(), ex.getRemoteAddress().getAddress().getHostAddress(),
                    path, b.name(), res.statusCode(), (System.nanoTime() - t0) / 1_000_000, body.length, sha256(body).substring(0, 16));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply(ex, 502, "{\"error\":\"interrupted\"}");
        } catch (IOException e) {
            HEALTHY.put(b.name(), false);                          // 다음 요청부터 다른 백엔드로
            reply(ex, 502, "{\"error\":\"backend " + b.name() + " failed\"}");
        }
    }

    static boolean isHealthy(Backend b) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(b.baseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + b.apiKey()).GET().build();
            return HTTP.send(r, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** "이름=주소|키,이름=주소|키" 형식 */
    static List<Backend> parse(String spec) {
        List<Backend> out = new ArrayList<>();
        for (String item : spec.split(",")) {
            String[] nameRest = item.strip().split("=", 2);
            String[] urlKey = nameRest[1].split("\\|", 2);
            out.add(new Backend(nameRest[0], urlKey[0], urlKey.length > 1 ? urlKey[1] : "none"));
        }
        return out;
    }

    static void reply(HttpExchange ex, int status, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    static String sha256(byte[] data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte x : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "-";
        }
    }
}
