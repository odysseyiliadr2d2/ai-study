package study.s20;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 평가 하네스 — 시나리오(YAML) × 반복 N 회를 JUnit 동적 테스트로 돌립니다.
 *
 *   ./mvnw test -Deval=true -Dtest=AgentEvalTest                       # 전체 1회
 *   ./mvnw test -Deval=true -Dtest=AgentEvalTest -Deval.repeat=3       # 같은 시나리오 3번
 *   ./mvnw test -Deval=true -Dtest=AgentEvalTest -Deval.only=I01,I02   # 인젝션만
 *
 * 채점: ① 필수 도구 호출 ② 금지 도구 시도 ③ 필수 핵심어 ④ 금지 패턴 ⑤ 정상 종료 (Python 트랙 run_eval.py 와 같음)
 * 결과: eval_out/results.jsonl, eval_out/report.md, eval_out/review.csv (사람 검수용)
 */
@EnabledIfSystemProperty(named = "eval", matches = "true")   // API 를 부르므로 평소 ./mvnw test 에서는 건너뜀
class AgentEvalTest {

    static final Path BASE = Path.of("").toAbsolutePath();       // surefire 작업 폴더 = labs/s20/java
    static final Path OUT = BASE.resolve("eval_out");
    static final List<Map<String, Object>> ROWS = Collections.synchronizedList(new ArrayList<>());
    static final Map<String, TracedAgent> AGENTS = new ConcurrentHashMap<>();   // 데이터 묶음별 MCP 서버 1개

    @TestFactory
    Stream<DynamicTest> scenarios() throws IOException {
        int repeat = Integer.parseInt(System.getProperty("eval.repeat", "1"));
        String only = System.getProperty("eval.only", "");
        Set<String> keep = only.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(only.split(",")));
        return loadScenarios(BASE.resolve("scenarios.yaml")).stream()
                .filter(sc -> keep.isEmpty() || keep.contains(String.valueOf(sc.get("id"))))
                .flatMap(sc -> IntStream.rangeClosed(1, repeat).mapToObj(k ->
                        DynamicTest.dynamicTest(sc.get("id") + "#" + k, () -> runOne(sc, k))));
    }

    void runOne(Map<String, Object> sc, int k) {
        String data = String.valueOf(sc.getOrDefault("data", "mock"));      // 시나리오별 데이터 묶음 교체
        TracedAgent agent = AGENTS.computeIfAbsent(data, d -> new TracedAgent(BASE.resolve(d)));
        TracedAgent.Trace tr = agent.run(String.valueOf(sc.get("question")), TracedAgent.DENY_ALL, date(sc.get("today")));
        List<String> fails = grade(sc, tr);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sc.get("id"));
        row.put("category", sc.get("category"));
        row.put("try", k);
        row.put("pass", fails.isEmpty());
        row.put("fails", fails);
        row.put("tools", tr.toolNames());
        row.put("blocked", tr.blocked.size());
        row.put("tokens_in", tr.tokensIn);
        row.put("tokens_out", tr.tokensOut);
        row.put("cost_usd", tr.costUsd);
        row.put("latency_ms", tr.latencyMs);
        row.put("run_id", tr.runId);
        row.put("answer", tr.answer);
        ROWS.add(row);
        System.out.printf("%s %s#%d %s %s%n", fails.isEmpty() ? "PASS" : "FAIL", sc.get("id"), k, tr.toolNames(), String.join("; ", fails));
        assertTrue(fails.isEmpty(), String.join("; ", fails));
    }

    /** 실패 사유 목록. 비어 있으면 통과. */
    @SuppressWarnings("unchecked")
    static List<String> grade(Map<String, Object> sc, TracedAgent.Trace tr) {
        Map<String, Object> exp = (Map<String, Object>) sc.getOrDefault("expect", Map.of());
        Map<String, Object> forb = (Map<String, Object>) sc.getOrDefault("forbid", Map.of());
        List<String> called = tr.toolNames();
        List<String> fails = new ArrayList<>();
        for (Object t : list(exp.get("tools_all"))) if (!called.contains(String.valueOf(t))) fails.add("필수 도구 미호출: " + t);
        for (Object t : list(forb.get("tools"))) if (called.contains(String.valueOf(t))) fails.add("금지 도구 시도: " + t);
        for (Object kw : list(exp.get("contains"))) if (!tr.answer.contains(String.valueOf(kw))) fails.add("핵심어 없음: " + kw);
        for (Object p : list(forb.get("patterns"))) if (Pattern.compile(String.valueOf(p)).matcher(tr.answer).find()) fails.add("금지 패턴: " + p);
        if (!"end".equals(tr.stop)) fails.add("비정상 종료: " + tr.stop);
        return fails;
    }

    @AfterAll
    static void writeReports() throws IOException {
        AGENTS.values().forEach(TracedAgent::close);
        if (ROWS.isEmpty()) return;
        Files.createDirectories(OUT);
        StringBuilder jsonl = new StringBuilder();
        for (Map<String, Object> r : ROWS) jsonl.append(TracedAgent.JSON.writeValueAsString(r)).append('\n');
        Files.writeString(OUT.resolve("results.jsonl"), jsonl, StandardCharsets.UTF_8);

        Map<String, List<Map<String, Object>>> byCat = ROWS.stream()
                .collect(Collectors.groupingBy(r -> String.valueOf(r.get("category")), TreeMap::new, Collectors.toList()));
        List<String> md = new ArrayList<>(List.of("# 운영 에이전트 평가 결과 (" + LocalDate.now() + ")", "",
                "| 유형 | 실행 | 통과 | 통과율 | 평균 토큰(in/out) | 평균 지연(s) |", "|---|---|---|---|---|---|"));
        byCat.forEach((cat, rs) -> {
            int n = rs.size();
            long ok = rs.stream().filter(r -> (Boolean) r.get("pass")).count();
            md.add(String.format("| %s | %d | %d | %.0f%% | %d/%d | %.1f |", cat, n, ok, 100.0 * ok / n,
                    rs.stream().mapToLong(r -> (Long) r.get("tokens_in")).sum() / n,
                    rs.stream().mapToLong(r -> (Long) r.get("tokens_out")).sum() / n,
                    rs.stream().mapToLong(r -> (Long) r.get("latency_ms")).sum() / (double) n / 1000));
        });
        long total = ROWS.stream().filter(r -> (Boolean) r.get("pass")).count();
        double cost = ROWS.stream().mapToDouble(r -> (Double) r.get("cost_usd")).sum();
        md.add("");
        md.add(String.format("전체 %d/%d (%.0f%%) · 추정 비용 $%.4f", total, ROWS.size(), 100.0 * total / ROWS.size(), cost));
        md.add("");
        md.add("## 실패 목록");
        for (Map<String, Object> r : ROWS) {
            if (!(Boolean) r.get("pass")) md.add("- " + r.get("id") + "#" + r.get("try") + ": " + String.join("; ", strings(r.get("fails"))));
        }
        Files.writeString(OUT.resolve("report.md"), String.join("\n", md) + "\n", StandardCharsets.UTF_8);

        // 사람 검수: 실패 전부 + 통과 중 safety·injection 전부 (키워드 채점이 놓치는 것을 사람이 봄)
        StringBuilder csv = new StringBuilder("﻿id,category,try,auto_pass,fails,answer,human_ok(Y/N),comment\n");   // 엑셀용 BOM
        for (Map<String, Object> r : ROWS) {
            String cat = String.valueOf(r.get("category"));
            if (!(Boolean) r.get("pass") || cat.equals("safety") || cat.equals("injection")) {
                csv.append(String.join(",", q(r.get("id")), q(cat), q(r.get("try")), q(r.get("pass")),
                        q(String.join("; ", strings(r.get("fails")))), q(r.get("answer")), "", "")).append('\n');
            }
        }
        Files.writeString(OUT.resolve("review.csv"), csv, StandardCharsets.UTF_8);
        System.out.printf("%n전체 %d/%d 통과 → %s%n", total, ROWS.size(), OUT.resolve("report.md"));
    }

    static List<Map<String, Object>> loadScenarios(Path file) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));   // 임의 클래스 생성을 막는 안전 로더
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return yaml.load(r);
        }
    }

    static LocalDate date(Object v) {
        if (v instanceof Date d) return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();   // 따옴표 없는 날짜
        return v == null ? LocalDate.parse("2026-10-29") : LocalDate.parse(String.valueOf(v));
    }

    static List<?> list(Object v) {
        return v instanceof List<?> l ? l : List.of();
    }

    static List<String> strings(Object v) {
        return list(v).stream().map(String::valueOf).collect(Collectors.toList());
    }

    static String q(Object v) {
        return "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"";
    }
}
