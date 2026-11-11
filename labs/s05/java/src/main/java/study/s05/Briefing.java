package study.s05;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 아침 브리핑: digest → Claude 요약 → 파일 저장 · CSV 기록.
 * n8n 의 ⑥ Basic LLM Chain ⑦ Gmail Send ⑧ Google Sheets Append 를 코드로 옮긴 것입니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s05.Briefing -Dnow=2026-11-13T07:30:00+09:00
 */
public class Briefing {
    // 요약은 claude-haiku-5-5 로도 충분한지 비교해 보세요
    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final Path OUT = Path.of(System.getProperty("out", "out"));

    // n8n 실습의 prompt.txt 와 같은 문구 (%s · %d 자리에 값이 들어갑니다)
    static final String PROMPT = """
            아래는 %s 아침까지 받은 메일 %d건과
            RSS 새 글 %d건입니다.

            다음 형식으로 한국어 브리핑을 작성하세요.
            1. 오늘 꼭 처리할 일 (최대 3개, 보낸 사람 표시)
            2. 참고만 할 메일 (한 줄씩)
            3. 기술 소식 (RSS, 제목과 한 줄 요약)
            - 원문에 없는 내용은 쓰지 않습니다.
            - 전체 15줄 이내.

            ---
            %s""";

    static String summarize(Digest d) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();   // ANTHROPIC_API_KEY — 코드와 파일에는 키를 두지 않습니다
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048)                 // temperature · top_p 는 넣지 않습니다 (Claude 5.5 는 400 오류)
                .addUserMessage(PROMPT.formatted(d.date(), d.mailCount(), d.feedCount(), d.digest()))
                .build();
        Message msg = client.messages().create(params);
        return msg.content().stream().flatMap(b -> b.text().stream())   // thinking 블록 건너뛰기
                .map(TextBlock::text).collect(Collectors.joining()).strip();
    }

    /** CSV 한 칸: 따옴표로 감싸고 안의 따옴표는 두 번 씁니다 (쉼표 · 줄바꿈이 있어도 한 칸). */
    static String csv(Object v) {
        return "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"";
    }

    /** ⑦ 대신 Markdown 파일, ⑧ 대신 CSV 한 줄. 엑셀에서 한글이 깨지지 않게 새 CSV 는 BOM 을 붙입니다. */
    static Path deliver(Digest d, String summary) throws Exception {
        Files.createDirectories(OUT);
        Path md = OUT.resolve("briefing-" + d.date() + ".md");
        Files.writeString(md, "# [아침 브리핑] " + d.date() + "\n\n" + summary + "\n", StandardCharsets.UTF_8);

        Path log = OUT.resolve("briefing_log.csv");
        String row = Stream.of(d.date(), d.mailCount(), d.feedCount(), summary.substring(0, Math.min(500, summary.length())))
                .map(Briefing::csv).collect(Collectors.joining(",")) + "\r\n";
        if (Files.notExists(log)) row = "\uFEFFdate,mails,feeds,summary\r\n" + row;
        Files.writeString(log, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return md;
    }

    public static void main(String[] args) throws Exception {
        Digest d = Digest.build(Digest.now());
        String summary = summarize(d);     // 메일 0건이어도 '메일 0건' 브리핑을 만듭니다
        Path md = deliver(d, summary);
        System.out.println(summary);
        System.out.println("\n저장: " + md + " · 기록: " + OUT.resolve("briefing_log.csv"));
    }
}
