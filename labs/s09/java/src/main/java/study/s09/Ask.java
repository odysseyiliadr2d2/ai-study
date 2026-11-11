package study.s09;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 3·4단계: 검색하고 근거와 함께 답하기 — ... -Dexec.mainClass=study.s09.Ask -Dexec.args="질문" */
public final class Ask {

    public static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final int TOP_K = 4;

    public record Hit(double score, Indexer.Chunk chunk) {}

    public static final String SYSTEM = """
            당신은 사내 문서 Q&A 도우미입니다.
            - 반드시 <근거> 안의 내용만으로 답합니다.
            - 문장 끝에 사용한 근거 번호를 [1]처럼 붙입니다.
            - 근거로 답할 수 없으면 "문서에서 찾지 못했습니다"라고만 답합니다.""";

    public static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    /** 정규화된 벡터이므로 내적 = 코사인 유사도. 청크 수천 개까지는 전수 비교로 충분합니다. */
    static List<Hit> search(EmbeddingModel model, List<Indexer.Chunk> index, String question, int k) {
        float[] q = model.embed("query: " + question).content().vector();
        return index.stream()
                .map(c -> new Hit(dot(q, c.vector()), c))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(k)
                .toList();
    }

    public static void main(String[] args) throws Exception {
        String question = args.length > 0 ? String.join(" ", args) : "야간 배치가 실패하면 누구에게 연락하나요?";
        List<Hit> hits = search(Indexer.model(), Indexer.load(), question, TOP_K);

        List<String> blocks = new ArrayList<>();
        for (int n = 0; n < hits.size(); n++) {
            Indexer.Chunk c = hits.get(n).chunk();
            blocks.add("[" + (n + 1) + "] (" + c.doc() + " · " + c.section() + ")\n" + c.text());
        }
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();     // ANTHROPIC_API_KEY
        Message msg = client.messages().create(MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(800)
                .system(SYSTEM)
                .addUserMessage("<근거>\n" + String.join("\n\n", blocks) + "\n</근거>\n\n질문: " + question)
                .build());
        // thinking 블록이 앞에 올 수 있어 text 블록만 골라 읽습니다
        System.out.println(msg.content().stream()
                .flatMap(b -> b.text().stream()).map(TextBlock::text).collect(Collectors.joining()));
        System.out.println("\n— 출처");
        for (int n = 0; n < hits.size(); n++) {
            Hit h = hits.get(n);
            System.out.printf("[%d] %s › %s (유사도 %.2f)%n", n + 1, h.chunk().doc(), h.chunk().section(), h.score());
        }
    }
}
