package study.s01;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * (b) LLM 제로샷: 학습 없이 유형 설명만 주고 분류합니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s01.ZeroShot
 */
public class ZeroShot {
    // 대량 분류는 작고 빠른 모델이 맞습니다
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-haiku-5-5");
    static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("배치장애", "야간·정기 배치, ETL, 적재 작업이 실패하거나 지연됨");
        LABELS.put("데이터문의", "숫자·코드·컬럼 값이 이상하거나 의미를 묻는 질문");
        LABELS.put("권한요청", "계정·조회·접속 권한의 생성, 추가, 회수");
        LABELS.put("보고서요청", "새 보고서·대시보드 또는 기존 보고서 변경 요청");
    }
    static final String SYSTEM = "당신은 증권사 정보계 운영팀의 티켓 분류 담당입니다.\n"
            + LABELS.entrySet().stream().map(e -> "- " + e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n"))
            + "\n티켓 문장을 읽고 위 유형 이름 중 하나만 출력하세요. 다른 말은 쓰지 않습니다.";

    private static AnthropicClient client;   // 처음 쓸 때 만듭니다 (ANTHROPIC_API_KEY 환경변수)

    static AnthropicClient client() {
        if (client == null) client = AnthropicOkHttpClient.fromEnv();
        return client;
    }

    /** 응답 content 에는 thinking 블록이 먼저 올 수 있으므로 text 블록만 모읍니다. */
    static String textOf(Message msg) {
        return msg.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining())
                .strip();
    }

    public static String classify(String ticket) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(1024)
                .system(SYSTEM)
                .addUserMessage(ticket)
                .build();
        String answer = textOf(client().messages().create(params));
        // LLM 출력은 반드시 허용 목록으로 검증합니다
        return LABELS.containsKey(answer) ? answer
                : "분류불가(" + answer.substring(0, Math.min(20, answer.length())) + ")";
    }

    public static void main(String[] args) {
        for (String t : List.of(
                "새벽 적재 작업이 ORA-00942 오류로 실패했습니다",
                "지점별 예금 잔고를 주간 단위로 보는 화면이 필요합니다",
                "외부 감사인이 다음 주까지 DW 조회만 할 수 있게 해 주세요")) {
            System.out.printf("%-8s %s%n", classify(t), t);
        }
    }
}
