package study.s23;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트 개인정보 마스킹 — Python 트랙 pii_mask.py 와 같은 패턴 · 같은 토큰 형식([PHONE_1]).
 *   echo "010-1234-5678 로 연락" | ./mvnw -q compile exec:java -Dexec.mainClass=study.s23.PiiMasker
 *
 * 주의: 아래 정규식은 학습용 '예시 패턴'입니다. 실제 계좌번호 형식은 금융회사마다 다르고,
 * 이름·주소처럼 정규식으로 잡기 어려운 정보도 있습니다. 운영에서는 사내 표준 패턴과 검증된 탐지 솔루션을 함께 씁니다.
 */
public final class PiiMasker {

    record Rule(String label, Pattern pattern) {}

    /** 마스킹 결과와 복원표. 복원표는 사내에만 두고 LLM 으로 보내지 않습니다. */
    public record Masked(String text, Map<String, String> table) {
        public Map<String, Integer> counts() {
            Map<String, Integer> c = new LinkedHashMap<>();
            table.keySet().forEach(t -> c.merge(t.substring(1, t.lastIndexOf('_')), 1, Integer::sum));
            return c;
        }
    }

    // 순서가 중요합니다: 더 구체적인 패턴(주민번호)을 숫자열 패턴보다 먼저 적용
    static final List<Rule> RULES = List.of(
            new Rule("RRN", Pattern.compile("(?<!\\d)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])-?[1-8]\\d{6}(?!\\d)")),   // 주민·외국인등록번호 형태
            new Rule("PHONE", Pattern.compile("(?<!\\d)01[016789]-?\\d{3,4}-?\\d{4}(?!\\d)")),                                   // 휴대전화
            new Rule("TEL", Pattern.compile("(?<!\\d)0(?:2|[3-6][1-5])-\\d{3,4}-\\d{4}(?!\\d)")),                                 // 지역번호 유선전화
            new Rule("CARD", Pattern.compile("(?<!\\d)\\d{4}-\\d{4}-\\d{4}-\\d{4}(?!\\d)")),                                       // 카드번호 형태
            // 하이픈 계좌번호 형태(예시). 2026-10-29 같은 날짜는 제외
            new Rule("ACCOUNT", Pattern.compile("(?<!\\d)(?!(?:19|20)\\d{2}-[01]\\d-[0-3]\\d(?!\\d))\\d{2,6}-\\d{2,6}-\\d{2,8}(?:-\\d{1,3})?(?!\\d)")),
            new Rule("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")));

    public static Masked mask(String text) {
        Map<String, String> table = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        String out = text;
        for (Rule r : RULES) {
            out = r.pattern().matcher(out).replaceAll(m -> {
                int n = counters.merge(r.label(), 1, Integer::sum);
                String token = "[" + r.label() + "_" + n + "]";
                table.put(token, m.group());
                return Matcher.quoteReplacement(token);
            });
        }
        return new Masked(out, table);
    }

    public static String unmask(String text, Map<String, String> table) {
        String out = text;
        for (var e : table.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }

    public static void main(String[] args) throws Exception {
        String src = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (src.isBlank()) {
            src = "고객 문의: 010-1234-5678 로 연락 부탁. 주민번호 900101-1234567, "
                    + "계좌 123-456-789012 이체 오류. 메일 test@example.com, 사무실 02-123-4567.";
        }
        Masked m = mask(src);
        System.out.println(m.text());
        System.out.println("\n마스킹 " + m.table().size() + "건: " + String.join(", ", m.table().keySet()));
    }
}
