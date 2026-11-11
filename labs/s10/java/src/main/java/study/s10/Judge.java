package study.s10;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import study.s09.Ask;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 답변 평가: LLM 판정(기준표) + 사람 검수 목록 (24문항 기준 LLM 호출 약 70회).
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s10.Judge -Dexec.args=eval.jsonl
 */
public final class Judge {

    static final String JUDGE_MODEL = System.getenv().getOrDefault("JUDGE_MODEL", Ask.MODEL);

    static final String RUBRIC = """
            당신은 사내 문서 Q&A 봇의 답변을 채점하는 평가자입니다. 아래 기준으로만 채점하세요.
            - correct (0~2): 정답 요지와 비교. 2=핵심이 모두 맞음, 1=일부만 맞거나 불필요한 내용 섞임, 0=틀림.
              정답 요지가 비어 있으면 '문서에 없는 질문'이므로, 답하지 않고 모른다고 하면 2, 지어내면 0.
            - faithful (0~2): 답변의 모든 문장이 제공된 근거로 뒷받침되는가. 2=전부, 1=일부 근거 밖, 0=대부분 근거 밖.
            - cited (0~1): 답변 문장에 [번호] 출처가 붙어 있는가. 거절 답변이면 1.
            JSON 한 줄로만 답하세요: {"correct": n, "faithful": n, "cited": n, "reason": "한 문장"}""";

    static String csv(Object v) {
        String s = v == null ? "" : v.toString();
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        List<Evaluate.Item> items = Evaluate.load(Path.of(args.length > 0 ? args[0] : "eval.jsonl"));
        ObjectMapper json = new ObjectMapper();
        List<Map<String, Object>> results = new ArrayList<>();

        try (Guard guard = new Guard()) {
            for (Evaluate.Item it : items) {
                Map<String, Object> out = guard.answer(it.question(), null);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sources = (List<Map<String, Object>>) out.get("sources");
                StringBuilder src = new StringBuilder();
                for (Map<String, Object> s : sources) src.append("- ").append(s.get("doc")).append(" › ").append(s.get("section")).append("\n");
                String prompt = "질문: " + it.question() + "\n정답 요지: " + it.answer() + "\n사용된 근거:\n"
                        + (src.isEmpty() ? "(없음)\n" : src) + "\n봇 답변:\n" + out.get("answer");
                // Claude 5.5 계열은 temperature 를 받지 않습니다. 대신 기준표를 구체적으로 적어 흔들림을 줄입니다.
                String text = Guard.textOf(guard.client.messages().create(com.anthropic.models.messages.MessageCreateParams.builder()
                        .model(JUDGE_MODEL).maxTokens(1000).system(RUBRIC).addUserMessage(prompt).build()));

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", it.id());
                row.put("category", it.category());
                row.put("answerable", it.answerable());
                row.put("bot", out.get("answer"));
                row.put("grounded", out.get("grounded"));
                try {
                    JsonNode score = json.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
                    row.put("correct", score.path("correct").asInt());
                    row.put("faithful", score.path("faithful").asInt());
                    row.put("cited", score.path("cited").asInt());
                    row.put("reason", score.path("reason").asText());
                } catch (Exception e) {
                    row.put("correct", null);
                    row.put("faithful", null);
                    row.put("cited", null);
                    row.put("reason", "판정 파싱 실패: " + text.substring(0, Math.min(80, text.length())));
                }
                results.add(row);
                System.out.printf("%s correct=%s faithful=%s%n", it.id(), row.get("correct"), row.get("faithful"));
            }
        }

        List<Map<String, Object>> ok = results.stream().filter(r -> r.get("correct") != null).toList();
        double acc = ok.stream().filter(r -> Integer.valueOf(2).equals(r.get("correct"))).count() / (double) Math.max(ok.size(), 1);
        double faith = ok.stream().filter(r -> Integer.valueOf(2).equals(r.get("faithful"))).count() / (double) Math.max(ok.size(), 1);
        System.out.printf("정답률 %.0f%% · 근거 충실 %.0f%% · 채점 %d/%d건%n", acc * 100, faith * 100, ok.size(), results.size());

        List<String> lines = new ArrayList<>();
        for (Map<String, Object> r : results) lines.add(json.writeValueAsString(r));
        Files.write(Path.of("judge_results.jsonl"), lines, StandardCharsets.UTF_8);

        // 사람 검수: 낮은 점수·파싱 실패는 전부, 나머지는 무작위 20%
        Random random = new Random(42);
        List<String> fields = List.of("id", "category", "bot", "correct", "faithful", "reason", "사람판정", "메모");
        StringBuilder sheet = new StringBuilder("﻿" + String.join(",", fields) + "\n");   // 엑셀용 BOM
        int n = 0;
        for (Map<String, Object> r : results) {
            if (!Integer.valueOf(2).equals(r.get("correct")) || random.nextDouble() < 0.2) {
                sheet.append(String.join(",", fields.stream().map(f -> csv(r.getOrDefault(f, ""))).toList())).append("\n");
                n++;
            }
        }
        Files.writeString(Path.of("human_review.csv"), sheet, StandardCharsets.UTF_8);
        System.out.println("사람 검수 대상 " + n + "건 → human_review.csv (엑셀로 열어 '사람판정' 열을 채웁니다)");
    }
}
