package study.s06;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.time.Duration;
import java.util.List;

/**
 * 2·3단계: 구조화 출력 + 검증 + 재시도.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s06.ExtractInquiry
 */
public class ExtractInquiry {
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");

    // timeout: 요청 하나의 상한 / maxRetries: SDK 가 429 · 5xx · 연결 오류를 자동 재시도하는 횟수 (기본 2)
    static final AnthropicClient CLIENT = AnthropicOkHttpClient.builder()
            .fromEnv()
            .timeout(Duration.ofSeconds(30))
            .maxRetries(3)
            .build();

    static final String SYSTEM = """
            당신은 SI 회사 고객지원팀의 문의 분류기입니다.
            - 메일 본문에 있는 사실만 추출합니다. 없는 값은 null 로 둡니다.
            - 오류 코드·시스템 이름은 본문 표기를 그대로 옮깁니다.""";

    static final String SAMPLE = """
            제목: [긴급] 정산 보고서 화면 오류
            안녕하세요. 오늘 아침부터 '월별 정산 보고서' 화면에서 조회 버튼을 누르면
            ERR-5031 오류가 나고 결과가 안 나옵니다. 내일(2026-11-05) 오전 결재에 필요해서
            그 전까지 꼭 확인 부탁드립니다. — 재무팀 담당자""";

    /** Inquiry record 에서 만든 스키마를 output_config.format 으로 보내고, 응답을 Inquiry 로 받아 업무 규칙까지 검사합니다. */
    public static Inquiry extract(String mail, int maxAttempts) {
        String base = "다음 문의 메일을 분류하세요.\n\n" + mail;
        String prompt = base;
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            StructuredMessageCreateParams<Inquiry> params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(4096)                       // thinking 토큰도 이 안에 포함됩니다
                    .system(SYSTEM)
                    .addUserMessage(prompt)
                    .outputConfig(StructuredOutputConfig.<Inquiry>builder()
                            .effort(OutputConfig.Effort.LOW)   // 분류는 깊은 생각이 필요 없음
                            .format(Inquiry.class)             // record → JSON Schema
                            .build())
                    .build();
            try {
                StructuredMessage<Inquiry> resp = CLIENT.messages().create(params);
                if (resp.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
                    throw new IllegalStateException("출력이 max_tokens 에서 잘렸습니다");
                }
                Inquiry result = resp.content().stream()
                        .flatMap(b -> b.text().stream())       // thinking 블록은 건너뜁니다
                        .map(StructuredTextBlock::text)        // JSON → Inquiry (Jackson)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("파싱 결과 없음 (stop_reason="
                                + resp.stopReason().map(StopReason::asString).orElse("-") + ")"));
                List<String> problems = InquiryRules.problems(result);
                if (problems.isEmpty()) return result;
                lastError = String.join("; ", problems);   // 형식은 맞지만 업무 규칙에 어긋남
            } catch (AnthropicInvalidDataException | IllegalStateException e) {
                lastError = e.getMessage();                // JSON 을 record 로 못 바꿈, 잘림 등
            } catch (RateLimitException e) {
                lastError = "429 Too Many Requests";
                sleep(5_000L * attempt);                   // SDK 재시도 후에도 429 면 조금 더 기다립니다
                continue;
            }
            // 오류 내용을 알려 주고 다시 요청합니다
            prompt = base + "\n\n직전 결과가 검증에 실패했습니다: " + lastError + "\n규칙을 지켜 다시 분류하세요.";
        }
        throw new IllegalStateException(maxAttempts + "회 시도 후 실패: " + lastError);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        Inquiry result = extract(SAMPLE, 2);
        // Optional 필드를 값 또는 null 로 쓰려면 Jdk8Module 이 필요합니다
        ObjectMapper json = new ObjectMapper().registerModule(new Jdk8Module());
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
