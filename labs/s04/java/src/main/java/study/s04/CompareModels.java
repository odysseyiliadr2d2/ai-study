package study.s04;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 같은 프롬프트를 Claude · OpenAI · 로컬(Ollama) 에 보내 답 · 지연 · 토큰을 비교합니다.
 * API 키가 없거나 로컬 서버가 꺼져 있으면 그 항목은 건너뜁니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s04.CompareModels
 */
public class CompareModels {
    static final String PROMPT = "Oracle 에서 파티션 테이블의 오래된 파티션을 정리할 때 주의할 점 3가지를 한 줄씩 알려 주세요.";

    record Reply(String text, Long inputTokens, Long outputTokens) {}

    record Target(String name, Function<String, Reply> ask, String requiredEnv) {}

    record Row(String name, double sec, Long in, Long out) {}

    static String env(String key, String def) {
        return System.getenv().getOrDefault(key, def);
    }

    /** Claude: Messages API */
    static Reply askClaude(String prompt) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        Message msg = client.messages().create(MessageCreateParams.builder()
                .model(env("CLAUDE_MODEL", "claude-sonnet-5-5"))
                .maxTokens(1024)
                .addUserMessage(prompt)
                .build());
        String text = msg.content().stream().flatMap(b -> b.text().stream())   // thinking 블록 건너뛰기
                .map(TextBlock::text).collect(Collectors.joining());
        return new Reply(text, msg.usage().inputTokens(), msg.usage().outputTokens());
    }

    /** OpenAI: Responses API */
    static Reply askOpenAI(String prompt) {
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();   // OPENAI_API_KEY
        Response resp = client.responses().create(ResponseCreateParams.builder()
                .model(env("OPENAI_MODEL", "gpt-5.5"))
                .input(prompt)
                .build());
        String text = resp.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(m -> m.content().stream())
                .flatMap(c -> c.outputText().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        return new Reply(text,
                resp.usage().map(u -> u.inputTokens()).orElse(null),
                resp.usage().map(u -> u.outputTokens()).orElse(null));
    }

    /** 로컬: Ollama 의 OpenAI 호환 Chat Completions. 키는 검사하지 않지만 값이 필요합니다. */
    static Reply askLocal(String prompt) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(env("LOCAL_BASE_URL", "http://localhost:11434/v1"))
                .apiKey("ollama")
                .build();
        ChatCompletion c = client.chat().completions().create(ChatCompletionCreateParams.builder()
                .model(env("LOCAL_MODEL", "gemma4:e4b"))
                .addUserMessage(prompt)
                .build());
        return new Reply(c.choices().get(0).message().content().orElse(""),
                c.usage().map(u -> u.promptTokens()).orElse(null),
                c.usage().map(u -> u.completionTokens()).orElse(null));
    }

    public static void main(String[] args) {
        List<Target> targets = List.of(
                new Target("Claude", CompareModels::askClaude, "ANTHROPIC_API_KEY"),
                new Target("OpenAI", CompareModels::askOpenAI, "OPENAI_API_KEY"),
                new Target("로컬(Ollama)", CompareModels::askLocal, null));

        List<Row> rows = new ArrayList<>();
        for (Target t : targets) {
            if (t.requiredEnv() != null && System.getenv(t.requiredEnv()) == null) {
                System.out.printf("[%s] %s 가 없어 건너뜁니다%n", t.name(), t.requiredEnv());
                continue;
            }
            long start = System.nanoTime();
            Reply r;
            try {
                r = t.ask().apply(PROMPT);
            } catch (RuntimeException e) {   // 연결 실패, 모델 없음 등
                System.out.printf("[%s] 실패: %s%n", t.name(), e.getMessage());
                continue;
            }
            double sec = (System.nanoTime() - start) / 1e9;
            rows.add(new Row(t.name(), sec, r.inputTokens(), r.outputTokens()));
            System.out.printf("%n===== %s (%.1f초) =====%n%s%n", t.name(), sec, r.text().strip());
        }

        System.out.printf("%n%-14s %8s %9s %9s%n", "대상", "지연(초)", "입력 토큰", "출력 토큰");
        for (Row r : rows) System.out.printf("%-14s %8.1f %9s %9s%n", r.name(), r.sec(), r.in(), r.out());
        System.out.println("\n같은 문장이라도 모델마다 토크나이저가 달라 입력 토큰 수가 다릅니다.");
    }
}
