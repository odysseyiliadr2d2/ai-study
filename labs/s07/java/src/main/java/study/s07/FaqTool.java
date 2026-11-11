package study.s07;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FAQ 조회 도구: 모델에게 줄 도구 정의와 실제 실행 코드. 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s07.FaqTool */
public final class FaqTool {

    public record Faq(String faqId, String category, String question, String answer, String updatedAt) {}

    private static final Pattern CODE = Pattern.compile("[a-z0-9-]{2,}");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]+");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<Faq> faqs;

    public FaqTool(Path csv) throws IOException {
        // 실습 파일은 값 안에 쉼표가 없습니다. 따옴표가 들어간 CSV 를 다루려면 Apache Commons CSV 같은 라이브러리를 씁니다.
        this.faqs = Files.readAllLines(csv, StandardCharsets.UTF_8).stream()
                .skip(1).filter(l -> !l.isBlank())
                .map(l -> l.split(",", 5))
                .map(c -> new Faq(c[0], c[1], c[2], c[3], c[4]))
                .toList();
    }

    /** 모델은 description 을 읽고 언제 이 도구를 쓸지 판단합니다. */
    public Tool definition() {
        return Tool.builder()
                .name("faq_lookup")
                .description("고객지원 FAQ 에서 문의와 관련된 항목을 찾습니다. 답장을 쓰기 전에 반드시 먼저 호출합니다. "
                        + "결과가 빈 배열이면 FAQ 에 근거가 없다는 뜻입니다.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "query", Map.of("type", "string",
                                        "description", "문의의 핵심 표현 (예: 'ERR-5031 정산 보고서')"),
                                "category", Map.of("type", "string", "description", "분류 (선택)",
                                        "enum", List.of("계정·로그인", "요금·계약", "장애·오류", "기능 요청", "기타")))))
                        .required(List.of("query"))
                        .build())
                .build();
    }

    /** 영문·숫자 코드(ERR-5031)는 통째로, 한글은 2글자씩 잘라 조사 차이를 흡수합니다. */
    static Set<String> tokens(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        Matcher m = CODE.matcher(t);
        while (m.find()) out.add(m.group());
        Matcher h = HANGUL.matcher(t);
        while (h.find()) {
            String w = h.group();
            for (int i = 0; i + 2 <= w.length(); i++) out.add(w.substring(i, i + 2));
        }
        return out;
    }

    public List<Faq> lookup(String query, String category, int top) {
        Set<String> q = tokens(query);
        record Scored(int score, Faq faq) {}
        return faqs.stream()
                .map(f -> {
                    Set<String> common = new HashSet<>(q);
                    common.retainAll(tokens(f.question() + " " + f.answer()));
                    int score = common.size() + (f.category().equals(category) ? 1 : 0);
                    return new Scored(score, f);
                })
                .filter(s -> s.score() >= 2)               // 한두 글자만 겹치는 우연한 일치는 버립니다
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(top)
                .map(Scored::faq)
                .toList();
    }

    /** 모델이 요청한 도구를 실행하고 결과를 JSON 문자열로 돌려줍니다. 모르는 도구는 실행하지 않습니다. */
    public String call(String name, JsonValue input) {
        if (!"faq_lookup".equals(name)) return "알 수 없는 도구입니다: " + name;
        Map<String, JsonValue> args = input.asObject().orElse(Map.of());
        String query = Optional.ofNullable(args.get("query")).flatMap(JsonValue::asString).orElse("");
        String category = Optional.ofNullable(args.get("category")).flatMap(JsonValue::asString).orElse(null);
        try {
            return JSON.writeValueAsString(lookup(query, category, 3));
        } catch (IOException e) {
            return "[]";
        }
    }

    public static void main(String[] args) throws IOException {
        FaqTool tool = new FaqTool(Path.of("faq.csv"));
        System.out.println(tool.lookup("정산 보고서 ERR-5031 오류", null, 3));
    }
}
