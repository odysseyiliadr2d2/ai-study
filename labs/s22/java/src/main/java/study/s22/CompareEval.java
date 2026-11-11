package study.s22;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 같은 eval.jsonl 로 세 방식을 비교합니다 — 모두 사내 vLLM(OpenAI 호환) 한 대에서.
 *   base    : 기본 모델 (학습 데이터와 같은 system + 메모)
 *   fewshot : 기본 모델 + 규칙 + train.jsonl 에서 비슷한 옛 보고서 3건 (검색으로 붙이는 동적 few-shot = RAG 방식)
 *   lora    : 기본 모델 + LoRA 어댑터 (요청의 model 을 어댑터 이름으로)
 * 지표: 형식 준수율(필수 항목 5개), 정답과의 글자 겹침(간이). 최종 판단은 사람의 블라인드 비교로 합니다.
 *
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s22.CompareEval
 */
public final class CompareEval {

    static final String BASE_URL = env("LLM_BASE_URL", "http://localhost:8000/v1");
    static final String API_KEY = env("LLM_API_KEY", "change-me");
    static final String BASE_MODEL = env("BASE_MODEL_NAME", "qwen3-0.6b");     // --served-model-name
    static final String LORA_MODEL = env("LORA_MODEL_NAME", "report-style");   // --lora-modules 의 이름
    static final List<String> REQUIRED = List.of("■ 개요", "■ 원인", "■ 조치", "■ 영향", "■ 후속");
    static final String RULES = "사내 장애 보고서 문체로 요약합니다. 반드시 '■ 개요 / ■ 원인 / ■ 조치 / ■ 영향 / ■ 후속' "
            + "다섯 줄로 쓰고, 메모에 없는 사실은 쓰지 않습니다. 시각은 HH:MM 으로 씁니다.";

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record Example(String system, String memo, String answer) {}

    static String chat(String model, ArrayNode messages) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 400);
        body.put("temperature", 0);                       // 비교 실험은 결정적으로
        body.set("messages", messages);
        // Qwen3 계열은 기본으로 사고(thinking) 출력을 붙입니다. vLLM 의 chat_template_kwargs 로 끕니다 (모델 카드 확인)
        body.putObject("chat_template_kwargs").put("enable_thinking", false);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IOException(model + " HTTP " + res.statusCode() + ": " + res.body());
        return JSON.readTree(res.body()).at("/choices/0/message/content").asText().strip();
    }

    static ArrayNode messages(String system) {
        ArrayNode a = JSON.createArrayNode();
        a.addObject().put("role", "system").put("content", system);
        return a;
    }

    static ArrayNode turn(ArrayNode a, String role, String content) {
        a.addObject().put("role", role).put("content", content);
        return a;
    }

    /** 검색: 글자 2-gram 자카드 유사도 상위 k 개. Python 트랙 fewshot.py 는 multilingual-e5-small 임베딩을 씁니다. */
    static List<Example> similar(List<Example> pool, String memo, int k) {
        Set<String> q = bigrams(memo);
        return pool.stream()
                .sorted(Comparator.comparingDouble((Example e) -> -jaccard(q, bigrams(e.memo()))))
                .limit(k)
                .toList();
    }

    static Set<String> bigrams(String s) {
        String t = s.replaceAll("\\s+", "");
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 2 <= t.length(); i++) out.add(t.substring(i, i + 2));
        return out;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return inter.size() / (double) (a.size() + b.size() - inter.size());
    }

    /** Python 트랙 char_f1 과 같은 간이 지표: 공백 뺀 글자 집합의 겹침 */
    static double charF1(String pred, String ref) {
        Set<Integer> p = new HashSet<>(), r = new HashSet<>();
        pred.replace(" ", "").codePoints().forEach(p::add);
        ref.replace(" ", "").codePoints().forEach(r::add);
        if (p.isEmpty() || r.isEmpty()) return 0;
        Set<Integer> inter = new HashSet<>(p);
        inter.retainAll(r);
        return 2.0 * inter.size() / (p.size() + r.size());
    }

    static List<Example> load(Path file) throws IOException {
        List<Example> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode m = JSON.readTree(line).path("messages");
            int n = m.size();
            String system = m.get(0).path("role").asText().equals("system") ? m.get(0).path("content").asText() : RULES;
            out.add(new Example(system, m.get(n - 2).path("content").asText(), m.get(n - 1).path("content").asText()));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        List<Example> train = load(Path.of("train.jsonl"));
        List<Example> eval = load(Path.of("eval.jsonl"));
        String[] names = {"base", "fewshot", "lora"};
        int[] fmt = new int[3];
        double[] f1 = new double[3];
        List<String> lines = new ArrayList<>();

        for (Example ex : eval) {
            String base = chat(BASE_MODEL, turn(messages(ex.system()), "user", ex.memo()));
            ArrayNode shots = messages(RULES);
            for (Example s : similar(train, ex.memo(), 3)) {          // 비슷한 예시를 대화로 붙임
                turn(shots, "user", s.memo());
                turn(shots, "assistant", s.answer());
            }
            String fewshot = chat(BASE_MODEL, turn(shots, "user", ex.memo()));
            String lora = chat(LORA_MODEL, turn(messages(ex.system()), "user", ex.memo()));

            String[] outs = {base, fewshot, lora};
            for (int i = 0; i < 3; i++) {
                String o = outs[i];
                if (REQUIRED.stream().allMatch(o::contains)) fmt[i]++;
                f1[i] += charF1(o, ex.answer());
            }
            ObjectNode row = JSON.createObjectNode();
            row.put("input", ex.memo()).put("base", base).put("fewshot", fewshot).put("lora", lora).put("ref", ex.answer());
            lines.add(row.toString());
        }
        int n = Math.max(1, eval.size());
        System.out.printf("평가 %d건 (기본 %s · 어댑터 %s)%n", eval.size(), BASE_MODEL, LORA_MODEL);
        IntStream.range(0, 3).forEach(i -> System.out.printf("  %-8s 형식 준수율 %3.0f%% · 글자 겹침 %.2f%n",
                names[i], 100.0 * fmt[i] / n, f1[i] / n));
        Files.write(Path.of("compare.jsonl"), lines, StandardCharsets.UTF_8);
        System.out.println("compare.jsonl 저장 — 검토자 2명이 방식 이름을 가린 채 선호를 표시합니다");
    }

    static String env(String k, String def) {
        return System.getenv().getOrDefault(k, def);
    }
}
