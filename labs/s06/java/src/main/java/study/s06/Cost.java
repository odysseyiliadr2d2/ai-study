package study.s06;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.Usage;

/**
 * 5단계: 토큰 · 비용 계산.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s06.Cost
 */
public class Cost {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    // 1백만 토큰당 달러 — Python 트랙과 같은 기본값. 반드시 공식 가격 페이지에서 최신 값을 확인하세요
    static final double PRICE_IN = Double.parseDouble(System.getenv().getOrDefault("PRICE_IN_PER_MTOK", "2.0"));
    static final double PRICE_OUT = Double.parseDouble(System.getenv().getOrDefault("PRICE_OUT_PER_MTOK", "10.0"));

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        // 보내기 전에 입력 토큰 수를 셉니다 (호출 비용 없음)
        long expected = client.messages().countTokens(MessageCountTokensParams.builder()
                .model(MODEL)
                .system(ExtractInquiry.SYSTEM)
                .addUserMessage(ExtractInquiry.SAMPLE)
                .build()).inputTokens();
        System.out.println("입력 토큰(예상): " + expected);

        // 실제 호출 후에는 usage 로 정확한 값을 받습니다
        Message msg = client.messages().create(MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048)
                .system(ExtractInquiry.SYSTEM)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(ExtractInquiry.SAMPLE + "\n\n한 줄로 요약하세요.")
                .build());
        // 주의: outputTokens 에는 thinking 토큰도 포함되어 출력 단가로 과금됩니다
        Usage u = msg.usage();
        double cost = u.inputTokens() / 1e6 * PRICE_IN + u.outputTokens() / 1e6 * PRICE_OUT;
        System.out.printf("입력 %d · 출력 %d 토큰 → $%.5f%n", u.inputTokens(), u.outputTokens(), cost);

        // 월 비용 추정: 하루 300통 × 22일
        double monthly = cost * 300 * 22;
        System.out.printf("하루 300통, 월 22일 기준 약 $%.2f/월 (환율 1,400원이면 약 %,.0f원)%n", monthly, monthly * 1400);
    }
}
