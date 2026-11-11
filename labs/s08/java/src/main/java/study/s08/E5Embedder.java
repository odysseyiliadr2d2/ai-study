package study.s08;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;

import java.nio.file.Path;
import java.util.List;

/**
 * multilingual-e5-small ONNX 모델을 같은 JVM 안에서 돌립니다 (API 키·외부 호출 없음).
 * 실행(유사도 감 잡기): ./mvnw -q compile exec:java -Dexec.mainClass=study.s08.E5Embedder
 */
public final class E5Embedder {

    private final EmbeddingModel model;

    public E5Embedder() {
        Path dir = Path.of(System.getenv().getOrDefault("E5_DIR", "models/multilingual-e5-small"));
        // e5 는 평균 풀링(mean pooling). LangChain4j 가 결과 벡터를 길이 1 로 정규화해 돌려줍니다.
        this.model = new OnnxEmbeddingModel(dir.resolve("model.onnx"), dir.resolve("tokenizer.json"), PoolingMode.MEAN);
    }

    /** e5 규칙: 저장할 문서는 "passage: " */
    public float[] passage(String text) {
        return model.embed("passage: " + text).content().vector();
    }

    /** e5 규칙: 질문은 "query: " */
    public float[] query(String text) {
        return model.embed("query: " + text).content().vector();
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    static double cosine(float[] a, float[] b) {
        return dot(a, b) / (Math.sqrt(dot(a, a)) * Math.sqrt(dot(b, b)));
    }

    public static void main(String[] args) {
        E5Embedder e5 = new E5Embedder();
        List<String> docs = List.of(
                "고객번호: 고객을 식별하는 고유 번호 (CUST_NO)",
                "고객 식별 번호는 계좌 개설 시 부여됩니다",
                "야간 배치가 실패하면 STG 적재부터 재처리합니다",
                "월말 기준 고객 등급 변경 이력을 보관하는 테이블");
        float[] q = e5.query("손님을 구별하는 번호가 뭐야?");
        System.out.println("벡터 차원: " + q.length);                 // 384
        for (String d : docs) {
            float[] v = e5.passage(d);
            // 정규화된 벡터이므로 내적과 코사인 유사도가 같습니다
            System.out.printf("%.3f  %.3f  %s%n", cosine(q, v), dot(q, v), d);
        }
    }
}
