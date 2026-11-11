package study.s10;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import study.s09.Ask.Hit;
import study.s09.Indexer.Chunk;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 리랭킹: 후보 청크를 cross-encoder(bge-reranker-v2-m3, ONNX)로 다시 채점.
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s10.Reranker -Dexec.args="배치 실패하면 처음부터 다시 돌려요?"
 */
public final class Reranker implements AutoCloseable {

    private final OnnxScoringModel model;

    public Reranker() throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("RERANK_DIR", "models/bge-reranker-v2-m3"));
        // 마지막 인자 true = 시그모이드 적용 → 0~1 점수 (Python 트랙 CrossEncoder 와 같은 척도)
        this.model = new OnnxScoringModel(dir.resolve("model.onnx").toString(), new OrtSession.SessionOptions(),
                dir.resolve("tokenizer.json").toString(), 510, true);
    }

    /** 질문과 청크를 한 쌍으로 모델에 넣어 관련도를 받고, 높은 순으로 top 개만 남깁니다. */
    public List<Hit> rerank(String question, List<Chunk> candidates, int top) {
        if (candidates.isEmpty()) return List.of();
        List<TextSegment> segs = candidates.stream().map(c -> TextSegment.from(c.text())).toList();
        List<Double> scores = model.scoreAll(segs, question).content();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) hits.add(new Hit(scores.get(i), candidates.get(i)));
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.subList(0, Math.min(top, hits.size()));
    }

    @Override
    public void close() {
        model.close();                 // 네이티브(ONNX Runtime) 자원 해제
    }

    public static void main(String[] args) throws Exception {
        String q = args.length > 0 ? String.join(" ", args) : "배치가 30분 넘게 안 돌아오면 누구한테 전화하나요?";
        Hybrid h = new Hybrid();
        try (Reranker r = new Reranker()) {
            for (Hit hit : r.rerank(q, h.hybridSearch(q, 20, null), 4)) {
                System.out.printf("%.3f  %s › %s%n", hit.score(), hit.chunk().doc(), hit.chunk().section());
            }
        }
    }
}
