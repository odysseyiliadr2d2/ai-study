package study.s10;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import study.s09.Ask;
import study.s09.Ask.Hit;
import study.s09.Indexer.Chunk;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 질의 재작성 + 하이브리드 + 리랭킹 + 가드레일로 답하기.
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s10.Guard -Dexec.args="TSS 재처리는 어디서부터 하나요?"
 */
public final class Guard implements AutoCloseable {

    static final double MIN_RERANK = 0.30;   // 최상위 근거 점수가 이보다 낮으면 답하지 않음 (평가셋으로 조정)

    // 업무 용어사전: 약어 → 문서에 실제로 쓰인 표현 (세션 12에서 정비하는 그 사전)
    static final Map<String, String> GLOSSARY = new LinkedHashMap<>(Map.of(
            "TSS", "Terrastream", "당직", "운영 당직자", "재처리", "재실행", "임시 공간", "TEMP 테이블스페이스"));

    record Rule(Pattern pattern, String label) {}

    static final List<Rule> PII = List.of(
            new Rule(Pattern.compile("\\b\\d{6}-?[1-4]\\d{6}\\b"), "[주민번호]"),
            new Rule(Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b"), "[전화번호]"),
            new Rule(Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), "[이메일]"),
            new Rule(Pattern.compile("\\b\\d{3,6}-\\d{2,6}-\\d{4,8}\\b"), "[계좌번호]"));
    static final Pattern CITED = Pattern.compile("\\[\\d+\\]");

    final AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    final ObjectMapper json = new ObjectMapper();
    final Hybrid hybrid;
    final Reranker reranker;

    public Guard() throws Exception {
        hybrid = new Hybrid();
        reranker = new Reranker();
    }

    /** 응답에 thinking 블록이 먼저 올 수 있으므로 text 블록만 모읍니다. */
    static String textOf(Message msg) {
        return msg.content().stream().flatMap(b -> b.text().stream()).map(TextBlock::text).collect(Collectors.joining());
    }

    String ask(String system, String user, int maxTokens) {
        return textOf(client.messages().create(MessageCreateParams.builder()
                .model(Ask.MODEL).maxTokens(maxTokens).system(system).addUserMessage(user).build()));
    }

    static String mask(String text) {
        for (Rule r : PII) text = r.pattern().matcher(text).replaceAll(r.label());
        return text;
    }

    /** 원 질문 + 용어사전 치환본 + LLM 이 만든 검색어 2개. */
    List<String> rewrite(String question) {
        String expanded = question;
        for (var e : GLOSSARY.entrySet()) expanded = expanded.replace(e.getKey(), e.getKey() + "(" + e.getValue() + ")");
        String text = ask("사내 문서 검색어를 만듭니다. 질문을 문서에 나올 법한 표현으로 바꾼 검색어 2개를 "
                + "JSON 문자열 배열로만 출력하세요. 예: [\"...\", \"...\"]", expanded, 200);
        List<String> extra = List.of();
        try {
            extra = json.readValue(text.substring(text.indexOf('['), text.lastIndexOf(']') + 1),
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 파싱 실패 시 원 질문과 치환본만 씁니다
        }
        LinkedHashSet<String> qs = new LinkedHashSet<>(List.of(question, expanded));
        qs.addAll(extra.subList(0, Math.min(2, extra.size())));
        return new ArrayList<>(qs);
    }

    public Map<String, Object> answer(String rawQuestion, Predicate<Chunk> where) throws Exception {
        String question = mask(rawQuestion);                         // 1) 입력 마스킹: 외부 LLM 로 나가기 전에
        List<String> queries = rewrite(question);                    // 2) 질의 재작성
        List<List<Integer>> lists = new ArrayList<>();
        for (String q : queries) lists.add(hybrid.hybridIds(q, 20, where));   // 3) 검색어별 결과를 다시 RRF
        List<Chunk> pool = Hybrid.rrf(lists).stream().limit(20).map(hybrid.records::get).toList();
        List<Hit> picked = reranker.rerank(question, pool, 4);       // 4) 리랭킹

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", question);
        out.put("queries", queries);
        if (picked.isEmpty() || picked.get(0).score() < MIN_RERANK) { // 5) 근거 없음 처리
            out.put("grounded", false);
            out.put("sources", List.of());
            out.put("answer", "문서에서 확실한 근거를 찾지 못했습니다. 운영 당직자에게 문의해 주세요.");
            return out;
        }
        StringBuilder ctx = new StringBuilder();
        for (int n = 0; n < picked.size(); n++) {
            Chunk c = picked.get(n).chunk();
            ctx.append("[").append(n + 1).append("] (").append(c.doc()).append(" · ").append(c.section())
                    .append(")\n").append(c.text()).append("\n\n");
        }
        String text = mask(ask(Ask.SYSTEM, "<근거>\n" + ctx.toString().strip() + "\n</근거>\n\n질문: " + question, 800));  // 6) 출력 마스킹
        out.put("grounded", CITED.matcher(text).find());
        out.put("answer", text);
        out.put("sources", picked.stream().map(h -> Map.of("doc", h.chunk().doc(), "section", h.chunk().section(),
                "score", Math.round(h.score() * 1000) / 1000.0)).toList());
        return out;
    }

    @Override
    public void close() {
        reranker.close();
    }

    public static void main(String[] args) throws Exception {
        String q = args.length > 0 ? String.join(" ", args) : "TSS 재처리는 어디서부터 하나요?";
        try (Guard g = new Guard()) {
            System.out.println(g.json.writerWithDefaultPrettyPrinter().writeValueAsString(g.answer(q, Hybrid.whereFromEnv())));
        }
    }
}
