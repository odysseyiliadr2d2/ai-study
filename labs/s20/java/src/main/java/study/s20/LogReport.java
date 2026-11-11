package study.s20;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 관측 로그 요약 — 요청 수, 종료 유형, 모델 호출·토큰·비용, 요청 지연 p50/p95, 도구별 오류율, 가드레일 차단.
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s20.LogReport -Dexec.args=logs/agent-20261029.jsonl
 * Python 트랙 traced_agent.py 의 로그도 같은 필드라서 그대로 읽힙니다.
 */
public final class LogReport {

    public static void main(String[] args) throws IOException {
        ObjectMapper json = new ObjectMapper();
        TypeReference<Map<String, Object>> type = new TypeReference<>() {};
        List<Map<String, Object>> evs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) evs.add(json.readValue(line, type));
        }
        Map<String, List<Map<String, Object>>> runs = evs.stream()
                .collect(Collectors.groupingBy(e -> str(e, "run_id"), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> finals = of(evs, "final"), tools = of(evs, "tool_call"),
                guards = of(evs, "guard"), llm = of(evs, "llm_call");

        List<Long> runLatency = runs.values().stream()
                .map(r -> r.stream().filter(e -> e.get("event").equals("llm_call") || e.get("event").equals("tool_call"))
                        .mapToLong(e -> num(e, "latency_ms")).sum())
                .sorted().collect(Collectors.toList());
        long maxSteps = finals.stream().filter(f -> "max_steps".equals(f.get("stop"))).count();

        System.out.printf("요청 %d건 · 정상 종료 %d · 최대 반복 도달 %d%n", runs.size(), finals.size() - maxSteps, maxSteps);
        System.out.printf("모델 호출 %d회 (요청당 평균 %.1f) · 토큰 in %,d / out %,d · 추정 비용 $%.4f%n",
                llm.size(), llm.size() / (double) Math.max(1, runs.size()),
                llm.stream().mapToLong(e -> num(e, "in_tok")).sum(), llm.stream().mapToLong(e -> num(e, "out_tok")).sum(),
                llm.stream().mapToDouble(e -> ((Number) e.getOrDefault("cost_usd", 0)).doubleValue()).sum());
        System.out.printf("요청 지연 p50 %.1fs · p95 %.1fs%n", pct(runLatency, 50) / 1000.0, pct(runLatency, 95) / 1000.0);

        Map<String, long[]> byTool = new TreeMap<>();   // [호출, 오류]
        for (Map<String, Object> e : tools) {
            long[] c = byTool.computeIfAbsent(str(e, "tool"), k -> new long[2]);
            c[0]++;
            if (Boolean.TRUE.equals(e.get("is_error"))) c[1]++;
        }
        byTool.forEach((t, c) -> System.out.printf("  도구 %s: %d회 · 오류 %d회 (%.0f%%)%n", t, c[0], c[1], 100.0 * c[1] / c[0]));
        Map<String, Long> guardKinds = guards.stream().collect(Collectors.groupingBy(g -> str(g, "kind"), TreeMap::new, Collectors.counting()));
        System.out.printf("가드레일 %d건: %s%n", guards.size(), guardKinds);
    }

    static List<Map<String, Object>> of(List<Map<String, Object>> evs, String event) {
        return evs.stream().filter(e -> event.equals(e.get("event"))).collect(Collectors.toList());
    }

    static String str(Map<String, Object> e, String k) {
        return String.valueOf(e.getOrDefault(k, "-"));
    }

    static long num(Map<String, Object> e, String k) {
        return ((Number) e.getOrDefault(k, 0)).longValue();
    }

    /** 가장 가까운 순위 방식 백분위 (Python 트랙 pct 와 같은 계산). */
    static long pct(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int i = (int) Math.rint(p / 100.0 * (sorted.size() - 1));   // 반올림 규칙도 Python round 와 같게(짝수 쪽)
        return sorted.get(Math.min(sorted.size() - 1, i));
    }
}
