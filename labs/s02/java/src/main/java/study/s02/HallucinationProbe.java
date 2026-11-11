package study.s02;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 환각 탐지 실험: 존재하지 않는 사내 테이블을 물어봅니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s02.HallucinationProbe
 */
public class HallucinationProbe {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final String QUESTION = "우리 DW 의 TB_ADW_CUST_GRD_HIST 테이블 컬럼 목록과 각 컬럼 설명을 알려 주세요.";

    // 실습용 가상 정의서 발췌 (실제 테이블이 아닙니다)
    static final String SCHEMA = """
            테이블: TB_ADW_CUST_GRD_HIST (고객 등급 변경 이력)
            - CUST_ID      VARCHAR2(20)  고객 식별자(가명처리)
            - GRD_CD       CHAR(2)       고객 등급 코드
            - VALID_FROM   DATE          유효 시작일
            - VALID_TO     DATE          유효 종료일 (현재 유효 행은 9999-12-31)
            - LOAD_DTTM    DATE          적재 일시""";

    record Variant(String name, String system, String user) {}

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        List<Variant> variants = List.of(
                new Variant("A. 질문만", "", QUESTION),
                new Variant("B. 모르면 모른다고",
                        "사내 시스템에 대해 확실히 모르는 내용은 추측하지 말고 '확인할 수 없습니다'라고 답하세요.", QUESTION),
                new Variant("C. 정의서 + 근거 지시",
                        "아래 정의서에 있는 내용만으로 답하세요. 정의서에 없는 컬럼이나 의미는 '정의서에 없음'이라고 쓰세요.",
                        "<정의서>\n" + SCHEMA + "\n</정의서>\n\n" + QUESTION + " 등급 변경 사유 컬럼도 있나요?"));

        for (Variant v : variants) {
            var builder = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(1024)
                    .addUserMessage(v.user());
            if (!v.system().isEmpty()) builder.system(v.system());   // A 는 system 없이 보냅니다

            String answer = client.messages().create(builder.build()).content().stream()
                    .flatMap(b -> b.text().stream())     // thinking 블록은 건너뛰고 text 만
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            System.out.println("\n===== " + v.name() + " =====");
            System.out.println(answer);
        }
    }
}
