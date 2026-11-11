package study.s03;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.util.stream.Collectors;

/** RunTemplate · Compare 가 함께 쓰는 Claude 호출 한 곳. 사내 승인 모델로 바꿀 때 이 파일만 고칩니다. */
public final class Claude {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    private static final AnthropicClient CLIENT = AnthropicOkHttpClient.fromEnv();   // ANTHROPIC_API_KEY

    /** 답 텍스트와 토큰 수 */
    public record Answer(String text, long inputTokens, long outputTokens) {}

    private Claude() {}

    /** system 이 빈 문자열이면 system 없이 보냅니다 (나쁜 프롬프트 비교용). */
    public static Answer ask(String system, String user, long maxTokens) {
        var builder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(maxTokens)
                .addUserMessage(user);
        if (!system.isEmpty()) builder.system(system);
        Message msg = CLIENT.messages().create(builder.build());
        // 응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 골라 잇습니다
        String text = msg.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return new Answer(text, msg.usage().inputTokens(), msg.usage().outputTokens());
    }
}
