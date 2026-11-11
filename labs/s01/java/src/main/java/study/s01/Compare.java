package study.s01;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 두 방식을 같은 평가셋으로 비교합니다. TrainPredict 를 먼저 실행해 ticket_model.json 을 만들어 두세요.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s01.Compare
 */
public class Compare {
    public static void main(String[] args) throws Exception {
        var rows = TicketModel.loadTickets(TrainPredict.DATA);
        // TrainPredict 와 같은 seed 로 나누므로 같은 평가용 8건이 나옵니다
        List<TicketModel.Ticket> test = TicketModel.split(rows, 0.25, TrainPredict.SEED).test();
        TicketModel ml = TicketModel.read(TrainPredict.MODEL_FILE);

        run("고전 ML", ml::predict, test);
        run("LLM 제로샷", ZeroShot::classify, test);
    }

    static void run(String name, UnaryOperator<String> classifier, List<TicketModel.Ticket> test) {
        long start = System.nanoTime();
        List<String> preds = test.stream().map(t -> classifier.apply(t.text())).toList();
        double sec = (System.nanoTime() - start) / 1e9;
        long hit = 0;
        for (int i = 0; i < test.size(); i++) if (preds.get(i).equals(test.get(i).label())) hit++;
        System.out.printf("%-10s 정확도 %.0f%%  전체 %.2f초  건당 %.0fms%n",
                name, 100.0 * hit / test.size(), sec, sec / test.size() * 1000);
        for (int i = 0; i < test.size(); i++) {
            var t = test.get(i);
            if (!preds.get(i).equals(t.label())) {
                String head = t.text().substring(0, Math.min(30, t.text().length()));
                System.out.printf("   오답: %s… → %s (정답 %s)%n", head, preds.get(i), t.label());
            }
        }
    }
}
