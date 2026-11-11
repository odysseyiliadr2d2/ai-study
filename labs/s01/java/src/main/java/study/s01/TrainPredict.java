package study.s01;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * (a) 고전 ML: 정답 달린 티켓으로 학습하고, 새 티켓을 예측합니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s01.TrainPredict
 */
public class TrainPredict {
    static final Path DATA = Path.of(System.getProperty("tickets", "../tickets.csv"));   // Python 트랙과 같은 파일
    static final Path MODEL_FILE = Path.of("ticket_model.json");
    static final long SEED = Long.getLong("seed", 42);   // -Dseed=7 처럼 바꿔 분할을 달리해 보세요

    public static void main(String[] args) throws Exception {
        List<TicketModel.Ticket> rows = TicketModel.loadTickets(DATA);

        // 1) 학습용과 평가용을 나눕니다. 평가용은 학습에 절대 쓰지 않습니다.
        TicketModel.Split split = TicketModel.split(rows, 0.25, SEED);

        // 2)·3) 문장을 숫자로 바꾸고, 가중치를 학습합니다.
        long t0 = System.nanoTime();
        TicketModel model = TicketModel.fit(split.train(), 2000, 5.0, 0.04);   // 반복 수 · 학습률 · L2 규제
        System.out.printf("학습 %d건 · 어휘 %d개 · %.0fms%n",
                split.train().size(), model.vocab().size(), (System.nanoTime() - t0) / 1e6);

        // 4) 평가: 처음 보는 문장으로 맞힌 비율을 잽니다.
        report(model, split.test());

        // 5) 저장: 학습 결과(가중치)는 파일 하나입니다. 추론 서버는 이 파일만 있으면 됩니다.
        model.save(MODEL_FILE);
        System.out.printf("저장: %s (%,d bytes)%n", MODEL_FILE, Files.size(MODEL_FILE));

        // 6) 추론: 새 문장에 대해 계산만 합니다.
        for (String text : List.of(
                "새벽 적재 작업이 ORA-00942 오류로 실패했습니다",
                "지점별 예금 잔고를 주간 단위로 보는 화면이 필요합니다")) {
            double confidence = Arrays.stream(model.predictProba(text)).max().orElse(0);
            System.out.printf("%-6s (확신도 %.2f)  %s%n", model.predict(text), confidence, text);
        }
    }

    /** classification_report 의 축약판: 유형별 정밀도 · 재현율과 전체 정확도 */
    static void report(TicketModel m, List<TicketModel.Ticket> test) {
        Map<String, int[]> counts = new TreeMap<>();   // 유형 → [맞힘, 그 유형으로 예측한 수, 실제 그 유형인 수]
        int correct = 0;
        for (TicketModel.Ticket t : test) {
            String p = m.predict(t.text());
            counts.computeIfAbsent(p, k -> new int[3])[1]++;
            counts.computeIfAbsent(t.label(), k -> new int[3])[2]++;
            if (p.equals(t.label())) {
                counts.get(p)[0]++;
                correct++;
            }
        }
        System.out.printf("%-8s %6s %6s %4s%n", "유형", "정밀도", "재현율", "건수");
        counts.forEach((label, v) -> System.out.printf("%-8s %6.2f %6.2f %4d%n", label,
                v[1] == 0 ? 0.0 : (double) v[0] / v[1], v[2] == 0 ? 0.0 : (double) v[0] / v[2], v[2]));
        System.out.printf("정확도 %.0f%% (%d/%d)%n", 100.0 * correct / test.size(), correct, test.size());
    }
}
