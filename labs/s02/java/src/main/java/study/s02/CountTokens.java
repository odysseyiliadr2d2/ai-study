package study.s02;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCountTokensParams;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한국어와 영어의 토큰 수 비교.
 * Anthropic 토큰 카운팅 API(무료, 분당 요청 수 제한 있음)를 씁니다. 결과는 추정치입니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s02.CountTokens
 */
public class CountTokens {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();   // ANTHROPIC_API_KEY

        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("한국어", "야간 배치가 실패하면 운영 당직자에게 먼저 알리고, 30분 안에 복구되지 않으면 팀장에게 전화합니다.");
        samples.put("영어", "If the nightly batch fails, notify the on-call operator first, and call the team lead if it is not recovered within 30 minutes.");
        samples.put("SQL", "SELECT BASE_DT, BRNCH_CD, SUM(TRD_AMT) FROM ADW.TB_TRD_DAILY WHERE BASE_DT = :dt GROUP BY BASE_DT, BRNCH_CD");
        samples.put("테이블명", "TB_ADW_CUST_GRD_HIST");

        System.out.println("모델: " + MODEL);
        System.out.printf("%-8s %6s %6s %8s%n", "구분", "글자 수", "토큰 수", "글자/토큰");
        samples.forEach((name, text) -> {
            MessageCountTokensParams params = MessageCountTokensParams.builder()
                    .model(MODEL)
                    .addUserMessage(text)
                    .build();
            // 메시지를 보내지 않고 입력 토큰 수만 셉니다 (메시지 구조에 쓰이는 토큰 몇 개가 함께 셉니다)
            long n = client.messages().countTokens(params).inputTokens();
            System.out.printf("%-8s %6d %6d %8.2f%n", name, text.length(), n, (double) text.length() / n);
        });
    }
}
