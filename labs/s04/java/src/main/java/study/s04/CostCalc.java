package study.s04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 토큰 비용 계산기. 가격은 코드에 넣지 않고 prices.json(Python 트랙과 같은 파일)에서 읽습니다.
 * 값이 비어 있는(null) 모델은 계산하지 않고 무엇을 채워야 하는지 알려 줍니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s04.CostCalc
 */
public class CostCalc {
    static final double M = 1_000_000;

    /** 요청 한 건의 토큰 사용량 */
    public record Usage(long inputTokens,        // 캐시를 거치지 않은 입력
                        long outputTokens,
                        long cacheReadTokens,    // 캐시에서 읽은 입력
                        long cacheWriteTokens) { // 이번 요청에서 캐시에 쓴 입력
        static Usage of(long input, long output) {
            return new Usage(input, output, 0, 0);
        }
    }

    final Map<String, JsonNode> prices = new LinkedHashMap<>();

    CostCalc(Path file) throws Exception {
        JsonNode root = new ObjectMapper().readTree(file.toFile());
        root.properties().forEach(e -> {
            if (!e.getKey().startsWith("_")) prices.put(e.getKey(), e.getValue());   // _안내 · _출처 는 건너뜀
        });
    }

    /** null 이거나 없는 값은 계산에 쓰지 않습니다 */
    static Double num(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : v.asDouble();
    }

    double requestCost(String model, Usage u) {
        JsonNode p = prices.get(model);
        List<String> missing = new ArrayList<>();
        for (String k : List.of("input", "output", "cache_write", "cache_read")) if (num(p, k) == null) missing.add(k);
        if (!missing.isEmpty()) throw new IllegalStateException(model + ": prices.json 에 " + missing + " 값을 채우세요");

        double inMult = 1.0, outMult = 1.0;
        JsonNode lc = p.get("long_context");
        long promptLen = u.inputTokens() + u.cacheReadTokens() + u.cacheWriteTokens();
        if (lc != null && promptLen > lc.get("threshold").asLong()) {
            Double im = num(lc, "input_mult"), om = num(lc, "output_mult");
            if (im == null || om == null) {
                throw new IllegalStateException(String.format("%s: 긴 입력(%,d토큰) 단가 배수를 채우세요", model, promptLen));
            }
            inMult = im;
            outMult = om;
        }
        return (u.inputTokens() * num(p, "input") * inMult
                + u.cacheReadTokens() * num(p, "cache_read") * inMult
                + u.cacheWriteTokens() * num(p, "cache_write") * inMult
                + u.outputTokens() * num(p, "output") * outMult) / M;
    }

    double monthly(String model, Usage perRequest, int requestsPerDay, int days) {
        return requestCost(model, perRequest) * requestsPerDay * days;
    }

    public static void main(String[] args) throws Exception {
        CostCalc calc = new CostCalc(Path.of(System.getProperty("prices", "../prices.json")));

        // 시나리오: 장애 로그 요약 — 고정 지시 · 정의서 8,000토큰(캐시) + 로그 1,500토큰 → 요약 800토큰
        Map<String, Usage> scenarios = new LinkedHashMap<>();
        scenarios.put("캐시 없음", Usage.of(9_500, 800));
        scenarios.put("캐시 적중", new Usage(1_500, 800, 8_000, 0));
        int perDay = 200;   // 팀 전체 하루 호출 수

        System.out.printf("%-20s %-8s %10s %12s%n", "모델", "시나리오", "건당($)", "월($, 21일)");
        for (String model : calc.prices.keySet()) {
            for (var s : scenarios.entrySet()) {
                try {
                    System.out.printf("%-20s %-8s %10.4f %12.2f%n", model, s.getKey(),
                            calc.requestCost(model, s.getValue()), calc.monthly(model, s.getValue(), perDay, 21));
                } catch (IllegalStateException e) {
                    System.out.printf("%-20s 건너뜀 — %s%n", model, e.getMessage());
                    break;
                }
            }
        }
    }
}
