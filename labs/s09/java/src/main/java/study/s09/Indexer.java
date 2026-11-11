package study.s09;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 2단계: 청크를 임베딩해 저장 — ... -Dexec.mainClass=study.s09.Indexer -Dexec.args=chunks.jsonl → index-java.json */
public final class Indexer {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chunk(String doc, String section, int part, String text, float[] vector) {
        Chunk withVector(float[] v) {
            return new Chunk(doc, section, part, text, v);
        }
    }

    public static final Path INDEX = Path.of("index-java.json");
    static final ObjectMapper JSON = new ObjectMapper();

    /** Python 트랙과 같은 multilingual-e5-small (ONNX). 결과 벡터는 길이 1 로 정규화되어 나옵니다. */
    public static EmbeddingModel model() {
        Path dir = Path.of(System.getenv().getOrDefault("E5_DIR", "models/multilingual-e5-small"));
        return new OnnxEmbeddingModel(dir.resolve("model.onnx"), dir.resolve("tokenizer.json"), PoolingMode.MEAN);
    }

    public static List<Chunk> load() throws IOException {
        return JSON.readValue(INDEX.toFile(), new TypeReference<List<Chunk>>() {});
    }

    public static void main(String[] args) throws IOException {
        Path in = Path.of(args.length > 0 ? args[0] : "chunks.jsonl");
        List<Chunk> chunks = new ArrayList<>();
        for (String line : Files.readAllLines(in, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) chunks.add(JSON.readValue(line, Chunk.class));
        }
        // e5 계열은 문서 쪽에 "passage: " 를 붙이는 것이 규칙입니다
        List<TextSegment> segments = chunks.stream().map(c -> TextSegment.from("passage: " + c.text())).toList();
        List<Embedding> vecs = model().embedAll(segments).content();
        List<Chunk> indexed = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) indexed.add(chunks.get(i).withVector(vecs.get(i).vector()));
        JSON.writeValue(INDEX.toFile(), indexed);       // 원문·출처·벡터를 한 파일에 (청크 수천 개까지는 충분)
        System.out.println(indexed.size() + "개 청크 색인 완료 → " + INDEX);
    }
}
