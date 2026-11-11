package study.s01;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * TF-IDF(글자 2~3개 묶음) + 다항 로지스틱 회귀를 직접 구현한 작은 분류기.
 * scikit-learn 의 TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 3)) + LogisticRegression 과 같은 생각입니다.
 * 학습 결과(어휘 · IDF · 가중치)는 이 record 하나이고, JSON 파일 하나로 저장됩니다.
 */
public record TicketModel(List<String> labels, Map<String, Integer> vocab, double[] idf,
                          double[][] weights, double[] bias) {

    /** 정답이 달린 티켓 한 줄 */
    public record Ticket(String text, String label) {}

    /** 학습용 · 평가용 묶음 */
    public record Split(List<Ticket> train, List<Ticket> test) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    // ---------- 데이터 ----------
    public static List<Ticket> loadTickets(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<Ticket> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {          // 첫 줄은 헤더(text,label)
            if (line.isBlank()) continue;
            int comma = line.lastIndexOf(',');                         // 라벨에는 쉼표가 없으므로 마지막 쉼표로 나눕니다
            rows.add(new Ticket(line.substring(0, comma), line.substring(comma + 1).strip()));
        }
        return rows;
    }

    /** 유형별로 같은 비율을 평가용으로 떼어 둡니다(stratify). seed 가 같으면 늘 같은 분할입니다. */
    public static Split split(List<Ticket> rows, double testRatio, long seed) {
        Map<String, List<Ticket>> byLabel = new TreeMap<>();
        for (Ticket t : rows) byLabel.computeIfAbsent(t.label(), k -> new ArrayList<>()).add(t);
        Random rnd = new Random(seed);
        List<Ticket> train = new ArrayList<>();
        List<Ticket> test = new ArrayList<>();
        for (List<Ticket> group : byLabel.values()) {
            List<Ticket> g = new ArrayList<>(group);
            Collections.shuffle(g, rnd);
            int nTest = (int) Math.round(g.size() * testRatio);
            test.addAll(g.subList(0, nTest));
            train.addAll(g.subList(nTest, g.size()));
        }
        return new Split(train, test);
    }

    // ---------- 문장 → 숫자 ----------
    /** 한국어는 띄어쓰기가 불규칙하므로 단어 대신 글자 2~3개 묶음을 씁니다. 단어 앞뒤 공백으로 경계를 표시합니다(char_wb). */
    static List<String> grams(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (w.isEmpty()) continue;
            String p = " " + w + " ";
            for (int n = 2; n <= 3; n++) {
                for (int i = 0; i + n <= p.length(); i++) out.add(p.substring(i, i + n));
            }
        }
        return out;
    }

    /** TF-IDF 벡터(길이 1로 정규화). 학습 때 못 본 조각은 버립니다. */
    double[] vectorize(String text) {
        double[] v = new double[vocab.size()];
        for (String g : grams(text)) {
            Integer j = vocab.get(g);
            if (j != null) v[j] += 1.0;
        }
        double norm = 0;
        for (int j = 0; j < v.length; j++) {
            v[j] *= idf[j];
            norm += v[j] * v[j];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) for (int j = 0; j < v.length; j++) v[j] /= norm;
        return v;
    }

    // ---------- 학습 ----------
    /** 학습(training): 정답과 예측의 차이(교차 엔트로피)가 줄도록 가중치를 경사하강법으로 조금씩 고칩니다. */
    public static TicketModel fit(List<Ticket> train, int epochs, double lr, double l2) {
        // 1) 어휘와 IDF — 여러 문서에 흔한 조각일수록 가중치를 낮춥니다
        Map<String, Integer> vocab = new LinkedHashMap<>();
        Map<String, Integer> df = new HashMap<>();
        for (Ticket t : train) {
            for (String g : new HashSet<>(grams(t.text()))) {
                vocab.putIfAbsent(g, vocab.size());
                df.merge(g, 1, Integer::sum);
            }
        }
        int n = train.size();
        double[] idf = new double[vocab.size()];
        vocab.forEach((g, j) -> idf[j] = Math.log((1.0 + n) / (1.0 + df.get(g))) + 1.0);   // scikit-learn 기본식
        List<String> labels = train.stream().map(Ticket::label).distinct().sorted().toList();
        int k = labels.size();

        TicketModel m = new TicketModel(labels, vocab, idf, new double[k][vocab.size()], new double[k]);
        double[][] x = train.stream().map(t -> m.vectorize(t.text())).toArray(double[][]::new);
        int[] y = train.stream().mapToInt(t -> labels.indexOf(t.label())).toArray();

        // 2) 경사하강법: 기울기 = (예측 확률 - 정답) × 입력
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[][] gw = new double[k][vocab.size()];
            double[] gb = new double[k];
            for (int i = 0; i < n; i++) {
                double[] p = m.softmax(x[i]);
                for (int c = 0; c < k; c++) {
                    double err = p[c] - (y[i] == c ? 1.0 : 0.0);
                    gb[c] += err / n;
                    for (int j = 0; j < x[i].length; j++) {
                        if (x[i][j] != 0) gw[c][j] += err * x[i][j] / n;
                    }
                }
            }
            for (int c = 0; c < k; c++) {
                m.bias[c] -= lr * gb[c];
                for (int j = 0; j < vocab.size(); j++) {
                    m.weights[c][j] -= lr * (gw[c][j] + l2 * m.weights[c][j]);   // l2: 가중치가 너무 커지지 않게
                }
            }
        }
        return m;
    }

    // ---------- 추론 ----------
    double[] softmax(double[] x) {
        double[] z = new double[labels.size()];
        double max = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < z.length; c++) {
            double s = bias[c];
            for (int j = 0; j < x.length; j++) if (x[j] != 0) s += weights[c][j] * x[j];
            z[c] = s;
            max = Math.max(max, s);
        }
        double sum = 0;
        for (int c = 0; c < z.length; c++) {
            z[c] = Math.exp(z[c] - max);
            sum += z[c];
        }
        for (int c = 0; c < z.length; c++) z[c] /= sum;
        return z;
    }

    /** 추론(inference): 새 문장에 대해 곱하고 더하는 계산만 합니다. */
    public double[] predictProba(String text) {
        return softmax(vectorize(text));
    }

    public String predict(String text) {
        double[] p = predictProba(text);
        int best = 0;
        for (int c = 1; c < p.length; c++) if (p[c] > p[best]) best = c;
        return labels.get(best);
    }

    // ---------- 저장 · 불러오기 ----------
    public void save(Path file) throws IOException {
        JSON.writeValue(file.toFile(), this);
    }

    public static TicketModel read(Path file) throws IOException {
        return JSON.readValue(file.toFile(), TicketModel.class);
    }
}
