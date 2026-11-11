package study.s10;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import study.s09.Ask;
import study.s09.Indexer;
import study.s09.Indexer.Chunk;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * 하이브리드 검색: Lucene BM25 + 벡터를 RRF 로 합치기.
 * 실행: ./mvnw -q compile exec:java -Dexec.mainClass=study.s10.Hybrid -Dexec.args="ORA-00054"
 */
public final class Hybrid {

    static final int RRF_K = 60;     // RRF 상수. 원 논문(Cormack 외, 2009) 값
    static final int POOL = 50;      // 각 검색기에서 가져올 후보 수

    public final List<Chunk> records;
    final EmbeddingModel embedder;
    final Analyzer analyzer = new KoreanAnalyzer();   // Nori: 조사·어미를 떼고 명사·동사 어간만 색인
    final IndexSearcher searcher;

    public Hybrid() throws IOException {
        records = Indexer.load();
        embedder = Indexer.model();
        // rank_bm25(Python 트랙)의 기본값 k1=1.5, b=0.75 에 맞춥니다. Lucene 기본값은 k1=1.2
        BM25Similarity bm25 = new BM25Similarity(1.5f, 0.75f);
        Directory dir = new ByteBuffersDirectory();       // 메모리 색인 (청크 수만 개까지 충분)
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer).setSimilarity(bm25))) {
            for (int i = 0; i < records.size(); i++) {
                Document d = new Document();
                d.add(new StoredField("idx", i));
                d.add(new TextField("text", records.get(i).text(), Field.Store.NO));
                w.addDocument(d);
            }
        }
        searcher = new IndexSearcher(DirectoryReader.open(dir));
        searcher.setSimilarity(bm25);
    }

    List<Integer> vectorRank(String question, int n) {
        float[] q = embedder.embed("query: " + question).content().vector();
        double[] s = records.stream().mapToDouble(c -> Ask.dot(q, c.vector())).toArray();
        return IntStream.range(0, s.length).boxed()
                .sorted((a, b) -> Double.compare(s[b], s[a])).limit(n).toList();
    }

    List<Integer> bm25Rank(String question, int n) throws IOException {
        Query query;
        try {
            // ORA-00054 의 '-' 같은 연산자 글자를 이스케이프해 질문을 그대로 검색어로 씁니다 (기본 OR)
            query = new QueryParser("text", analyzer).parse(QueryParser.escape(question));
        } catch (ParseException e) {
            return List.of();                          // 분석 후 남는 낱말이 없으면 BM25 후보 없음
        }
        StoredFields stored = searcher.storedFields();
        List<Integer> out = new ArrayList<>();
        for (ScoreDoc sd : searcher.search(query, n).scoreDocs) {
            out.add(stored.document(sd.doc).getField("idx").numericValue().intValue());
        }
        return out;
    }

    /** 순위만 보고 합칩니다: 점수 = Σ 1 / (k + 순위). 점수 척도가 달라도 섞을 수 있습니다. */
    public static List<Integer> rrf(List<List<Integer>> rankings) {
        Map<Integer, Double> fused = new HashMap<>();
        for (List<Integer> ranking : rankings) {
            for (int r = 0; r < ranking.size(); r++) fused.merge(ranking.get(r), 1.0 / (RRF_K + r + 1), Double::sum);
        }
        return fused.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }

    public List<Integer> hybridIds(String question, int n, Predicate<Chunk> where) throws IOException {
        return rrf(List.of(vectorRank(question, POOL), bm25Rank(question, POOL))).stream()
                .filter(i -> where == null || where.test(records.get(i)))   // 메타데이터 필터 (예: 구판 제외)
                .limit(n).toList();
    }

    public List<Chunk> hybridSearch(String question, int n, Predicate<Chunk> where) throws IOException {
        return hybridIds(question, n, where).stream().map(records::get).toList();
    }

    public List<Chunk> vectorSearch(String question, int n) {
        return vectorRank(question, n).stream().map(records::get).toList();
    }

    /** EXCLUDE_DOC=batch-ops-manual-v2.md 처럼 환경변수로 문서를 빼는 필터 */
    public static Predicate<Chunk> whereFromEnv() {
        String ex = System.getenv("EXCLUDE_DOC");
        return ex == null ? null : c -> !c.doc().equals(ex);
    }

    static List<String> keys(List<Chunk> cs) {
        return cs.stream().map(c -> c.doc() + "#" + c.section()).toList();
    }

    public static void main(String[] args) throws IOException {
        String q = args.length > 0 ? String.join(" ", args) : "Terrastream 콘솔에서 재실행";
        Hybrid h = new Hybrid();
        System.out.println("벡터만   : " + keys(h.vectorSearch(q, 5)));
        System.out.println("하이브리드: " + keys(h.hybridSearch(q, 5, whereFromEnv())));
    }
}
