package study.s07;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 에이전트 루프: 도구 호출이 끝날 때까지 반복하고 JSON 결과를 돌려줍니다. */
public final class MailAgent {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mail(String id, @JsonProperty("thread_id") String threadId, String from,
                       @JsonProperty("received_at") String receivedAt, String subject, String body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String category, String urgency, String summary,
                         @JsonProperty("faq_used") String faqUsed,
                         @JsonProperty("needs_human") Boolean needsHuman,
                         @JsonProperty("reply_draft") String replyDraft) {}

    public record Run(Result result, List<String> toolCalls) {}

    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final int MAX_ITERATIONS = 5;         // 도구 호출이 끝없이 반복되지 않게 (n8n 의 Max Iterations)

    static final String SYSTEM = """
            당신은 SI 회사 고객지원팀의 1차 응대 담당입니다.
            1. 문의를 '계정·로그인 / 요금·계약 / 장애·오류 / 기능 요청 / 기타' 중 하나로 분류합니다.
            2. 답을 쓰기 전에 반드시 faq_lookup 도구로 관련 FAQ 를 찾습니다.
            3. FAQ 에 근거가 있으면 그 내용으로, 없으면 "담당자가 확인 후 회신드리겠습니다"로 답장 초안을 씁니다.
            4. 가격·계약 조건·장애 원인은 추측해서 쓰지 않습니다.
            5. 답장은 합니다체, 5문장 이내, 서명은 '고객지원팀'.
            6. 확신이 없으면 needs_human 을 true 로 둡니다.
            마지막 답은 JSON 하나로만 씁니다:
            {"category": "...", "urgency": "높음|보통|낮음", "summary": "...", "faq_used": "FAQ-012 또는 빈 문자열", "needs_human": false, "reply_draft": "..."}""";

    private final AnthropicClient client = AnthropicOkHttpClient.builder()
            .fromEnv()                 // ANTHROPIC_API_KEY
            .maxRetries(3)             // 429·5xx·네트워크 오류 재시도 (n8n 의 Retry On Fail)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final FaqTool faq;

    public MailAgent(FaqTool faq) {
        this.faq = faq;
    }

    public Run run(Mail mail) throws Exception {
        String body = mail.body().length() > 4000 ? mail.body().substring(0, 4000) : mail.body();
        // Claude 5.5: temperature 와 강제 tool_choice 는 넣지 않습니다. 도구 순서는 시스템 프롬프트로 지시
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2000)
                .system(SYSTEM)
                .addTool(faq.definition())
                .addUserMessage("다음 고객 문의 메일을 처리하세요.\n보낸 사람: " + mail.from()
                        + "\n제목: " + mail.subject() + "\n본문:\n" + body);
        List<String> calls = new ArrayList<>();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            Message msg = client.messages().create(params.build());
            boolean toolUse = msg.stopReason().map(StopReason.TOOL_USE::equals).orElse(false);
            if (toolUse) {
                params.addMessage(msg);                     // thinking·tool_use 블록을 그대로 assistant 차례로
                List<ContentBlockParam> results = new ArrayList<>();
                for (ContentBlock block : msg.content()) {
                    block.toolUse().ifPresent(tu -> {
                        String out = faq.call(tu.name(), tu._input());
                        calls.add(tu.name() + " " + tu._input());
                        results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                .toolUseId(tu.id())
                                .content(out)
                                .build()));
                    });
                }
                params.addUserMessageOfContentBlockParams(results);
                continue;
            }
            String text = msg.content().stream()              // thinking 블록은 건너뛰고 text 만
                    .flatMap(b -> b.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            return new Run(parse(text), calls);
        }
        throw new IllegalStateException("도구 호출이 " + MAX_ITERATIONS + "번을 넘었습니다");
    }

    /** 응답 텍스트에서 JSON 을 꺼내 필수 값을 확인합니다 (n8n 의 Structured Output Parser 역할). */
    Result parse(String text) throws Exception {
        Result r = json.readValue(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1), Result.class);
        if (r.category() == null || r.summary() == null || r.replyDraft() == null || r.needsHuman() == null) {
            throw new IllegalArgumentException("결과에 필수 값이 없습니다: " + r);
        }
        return r;
    }
}
