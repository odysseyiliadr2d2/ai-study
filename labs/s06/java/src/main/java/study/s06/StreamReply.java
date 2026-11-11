package study.s06;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;

/**
 * 4단계: 스트리밍 — 답장 초안을 글자가 생기는 대로 보여 주기.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s06.StreamReply
 */
public class StreamReply {
    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5"))
                .maxTokens(4096)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system("당신은 SI 회사 고객지원 담당자입니다. 정중한 합니다체로 5문장 이내 답장 초안을 씁니다.")
                .addUserMessage("월별 정산 보고서 화면 ERR-5031 오류 문의에 대한 1차 답장 초안을 써 주세요.")
                .build();

        // 이벤트를 화면에 내보내면서 동시에 모아 두면, 끝난 뒤 전체 메시지와 usage 를 얻습니다
        MessageAccumulator acc = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream()
                    .map(acc::accumulate)
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())   // text 조각만 (thinking 조각 제외)
                    .forEach(text -> {
                        System.out.print(text.text());
                        System.out.flush();
                    });
        }
        Message fin = acc.message();
        System.out.println();
        System.out.println("stop_reason: " + fin.stopReason().map(StopReason::asString).orElse("-")
                + " / 출력 토큰: " + fin.usage().outputTokens());
    }
}
