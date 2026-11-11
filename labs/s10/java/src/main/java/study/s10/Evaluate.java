package study.s10;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import study.s09.Indexer.Chunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * 검색 평가: recall@k 와 MRR 을 세 가지 설정 × 질문 유형별로 비교 (LLM 호출 없음).
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s10.Evaluate -Dexec.args=eval.jsonl
 */
public final class Evaluate {

    static final int K = 4;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String type, String question, String answer, List<String> evidence,
                       String category, boolean answerable) {}

    static final ObjectMapper JSON = new ObjectMapper();

    public static List<Item> load(Path path) throws IOException {
        List<Item> items = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) items.add(JSON.readValue(line, Item.class));
        }
        return items;
    }

    /** 같은 섹션의 청크가 여러 개면 첫 번째만 셉니다 (근거 단위가 섹션이므로). */
    static List<String> dedupKeys(List<Chunk> chunks) {
        return new ArrayList<>(new LinkedHashSet<>(chunks.stream().map(c -> c.doc() + "#" + c.section()).toList()));
    }

    /** 정답 근거 중 상위 k개 안에 들어온 비율. */
    static double recallAtK(List<String> ranked, Set<String> gold) {
        Set<String> top = new HashSet<>(ranked.subList(0, Math.min(K, ranked.size())));
        return gold.stream().filter(top::contains).count() / (double) gold.size();
    }

    /** 첫 정답 근거의 순위 역수. 1위면 1.0, 3위면 0.33, 못 찾으면 0. */
    static double reciprocalRank(List<String> ranked, Set<String> gold) {
        for (int i = 0; i < ranked.size(); i++) if (gold.contains(ranked.get(i))) return 1.0 / (i + 1);
        return 0.0;
    }

    interface Retriever {
        List<String> apply(String q) throws IOException;
    }

    public static void main(String[] args) throws Exception {
        List<Item> items = load(Path.of(args.length > 0 ? args[0] : "eval.jsonl")).stream()
                .filter(it -> it.answerable() && !it.evidence().isEmpty())   // 검색 평가는 답이 있는 문항만
                .toList();
        Hybrid h = new Hybrid();
        try (Reranker rr = new Reranker()) {
            Map<String, Retriever> retrievers = new LinkedHashMap<>();
            retrievers.put("vector", q -> dedupKeys(h.vectorSearch(q, 20)));
            retrievers.put("hybrid", q -> dedupKeys(h.hybridSearch(q, 20, null)));
            retrievers.put("hybrid+rerank", q -> dedupKeys(rr.rerank(q, h.hybridSearch(q, 20, null), 10)
                    .stream().map(x -> x.chunk()).toList()));

            List<String> types = items.stream().map(Item::type).distinct().sorted().toList();
            List<String> groups = new ArrayList<>(List.of("전체"));
            groups.addAll(types);
            System.out.printf("%-14s%s%n", "설정", String.join("", groups.stream().map(g -> "%15s".formatted(g)).toList()));
            List<Map<String, Object>> rows = new ArrayList<>();

            for (var e : retrievers.entrySet()) {
                Map<String, List<Double>> rec = new HashMap<>(), mrr = new HashMap<>();
                for (Item it : items) {
                    List<String> ranked = e.getValue().apply(it.question());
                    Set<String> gold = new HashSet<>(it.evidence());
                    for (String g : List.of("전체", it.type())) {
                        rec.computeIfAbsent(g, x -> new ArrayList<>()).add(recallAtK(ranked, gold));
                        mrr.computeIfAbsent(g, x -> new ArrayList<>()).add(reciprocalRank(ranked, gold));
                    }
                    if (e.getKey().equals("hybrid+rerank")) {
                        int hit = -1;
                        for (int i = 0; i < ranked.size() && hit < 0; i++) if (gold.contains(ranked.get(i))) hit = i + 1;
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", it.id());
                        row.put("type", it.type());
                        row.put("category", it.category());
                        row.put("rank", hit < 0 ? null : hit);
                        row.put("top", ranked.subList(0, Math.min(K, ranked.size())));
                        rows.add(row);
                    }
                }
                Function<String, String> cell = g -> "R%.2f M%.2f".formatted(avg(rec.get(g)), avg(mrr.get(g)));
                System.out.printf("%-14s%s%n", e.getKey(), String.join("", groups.stream().map(g -> "%15s".formatted(cell.apply(g))).toList()));
            }
            System.out.printf("(R = recall@%d, M = MRR, 답이 있는 문항 %d개)%n", K, items.size());
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> row : rows) lines.add(JSON.writeValueAsString(row));
            Files.write(Path.of("eval_results.jsonl"), lines, StandardCharsets.UTF_8);
            System.out.println("문항별 결과 → eval_results.jsonl (rank 가 비었거나 4보다 큰 문항부터 원인을 봅니다)");
        }
    }

    static double avg(List<Double> xs) {
        return xs == null || xs.isEmpty() ? 0 : xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
