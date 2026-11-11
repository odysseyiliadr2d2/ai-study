package study.s02;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 임베딩 코사인 유사도. 로컬 Ollama 의 임베딩 API(POST /api/embed)와 다국어 모델 bge-m3 를 씁니다.
 * 폐쇄망에서는 모델 파일을 반입한 사내 Ollama 서버 주소를 OLLAMA_HOST 로 지정합니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s02.EmbedSimilarity
 */
public class EmbedSimilarity {
    static final String HOST = System.getenv().getOrDefault("OLLAMA_HOST", "http://localhost:11434");
    static final String MODEL = System.getenv().getOrDefault("EMBED_MODEL", "bge-m3");

    record EmbedRequest(String model, List<String> input) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbedResponse(List<double[]> embeddings) {}

    record Scored(double score, String text) {}

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    /** 문장 여러 개를 한 번에 벡터로 바꿉니다 (입력 순서대로 돌아옴). */
    static List<double[]> embed(List<String> texts) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(HOST + "/api/embed"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(new EmbedRequest(MODEL, texts))))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
        return JSON.readValue(resp.body(), EmbedResponse.class).embeddings();
    }

    /** 코사인 유사도 = 내적 / (길이 × 길이) */
    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static void main(String[] args) throws Exception {
        String query = "야간 적재 작업이 실패했을 때 대응 절차";
        List<String> docs = List.of(
                "배치 장애 발생 시 운영 당직자에게 알리고 재처리합니다",        // 뜻이 가까움
                "ETL job failure: notify the on-call operator and rerun",       // 영어지만 뜻이 가까움
                "적재 실패 건은 콘솔에서 해당 블록부터 다시 실행합니다",        // 단어는 다르지만 관련
                "신규 입사자 DW 조회 계정 신청 방법",                           // 관련 없음
                "구내식당 점심 메뉴 안내");                                     // 전혀 관련 없음

        // bge-m3 는 e5 계열과 달리 "query: " / "passage: " 접두어가 필요 없습니다
        double[] q = embed(List.of(query)).get(0);
        List<double[]> d = embed(docs);
        System.out.println("벡터 차원: " + q.length);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) scored.add(new Scored(cosine(q, d.get(i)), docs.get(i)));
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        for (Scored s : scored) System.out.printf("%.3f  %s%n", s.score(), s.text());
    }
}
