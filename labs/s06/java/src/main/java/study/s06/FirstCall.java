package study.s06;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import java.util.stream.Collectors;

/**
 * 1단계: 첫 호출 — 메시지 구조와 응답 읽기.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s06.FirstCall
 */
public class FirstCall {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");

    /** 응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 골라 잇습니다. */
    static String textOf(Message msg) {
        return msg.content().stream().flatMap(b -> b.text().stream()).map(TextBlock::text).collect(Collectors.joining());
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();   // ANTHROPIC_API_KEY
        OutputConfig lowEffort = OutputConfig.builder().effort(OutputConfig.Effort.LOW).build();   // 간단한 일은 생각을 줄여 빠르고 싸게

        Message msg = client.messages().create(MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048)          // 생각(thinking) + 답을 합친 상한 (넘으면 stop_reason = max_tokens)
                .outputConfig(lowEffort)
                .system("당신은 SI 회사 고객지원팀의 분류 담당자입니다. 한 문장으로 답합니다.")
                .addUserMessage("로그인이 안 됩니다. 비밀번호를 바꿔도 같아요. 어느 팀에 물어봐야 하나요?")
                .build());

        System.out.println("블록 종류: " + msg.content().stream()
                .map(b -> b.isThinking() ? "thinking" : b.isText() ? "text" : "기타").toList());   // 예: [thinking, text]
        System.out.println("답변: " + textOf(msg));
        System.out.println("stop_reason: " + msg.stopReason().map(StopReason::asString).orElse("-"));
        System.out.println("토큰: " + msg.usage().inputTokens() + " → " + msg.usage().outputTokens());

        // 대화를 이어 가려면 이전 답을 assistant 로 넣고 다시 보냅니다 (API 는 상태를 기억하지 않음)
        Message follow = client.messages().create(MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048)
                .outputConfig(lowEffort)
                .addUserMessage("로그인이 안 됩니다. 어느 팀에 물어봐야 하나요?")
                .addAssistantMessage(textOf(msg))
                .addUserMessage("그 팀에 보낼 문의 제목을 한 줄로 써 주세요.")
                .build());
        System.out.println("후속: " + textOf(follow));
    }
}
