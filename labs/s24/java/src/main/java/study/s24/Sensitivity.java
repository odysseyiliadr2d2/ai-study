package study.s24;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 민감도 분석 — 활용률과 절감 시간이 예상보다 낮으면 회수 기간이 어떻게 바뀌나 (Python 트랙 sensitivity.py 와 같은 표).
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s24.Sensitivity -Dexec.args="usecases.yaml 장애_보고서_초안_자동_작성"
 * 이름의 공백은 exec.args 에서 나뉘므로 밑줄(_)로 적어도 됩니다.
 */
public final class Sensitivity {

    public static final double[] RATES = {0.3, 0.5, 0.7, 0.9};
    public static final double[] SCALES = {0.5, 0.75, 1.0};

    /** [활용률][절감 분 배율] 별 회수 기간(개월) */
    public static double[][] grid(Roi.UseCase uc, Roi.Assumptions a) {
        double[][] g = new double[RATES.length][SCALES.length];
        for (int i = 0; i < RATES.length; i++) {
            for (int j = 0; j < SCALES.length; j++) {
                g[i][j] = Roi.evaluate(uc.scaleMinutes(SCALES[j]), a.withAdoption(RATES[i])).payback();
            }
        }
        return g;
    }

    public static String markdown(Roi.UseCase uc, Roi.Assumptions a) {
        double[][] g = grid(uc, a);
        StringBuilder sb = new StringBuilder("| 활용률 \\ 절감 분 | ×0.5 | ×0.75 | ×1.0 |\n|---:|---:|---:|---:|\n");
        for (int i = 0; i < RATES.length; i++) {
            sb.append("| ").append(Roi.pct(RATES[i]));
            for (double pb : g[i]) sb.append(" | ").append(Double.isInfinite(pb) ? "불가" : String.format("%.1f", pb));
            sb.append(" |\n");
        }
        return sb.toString();
    }

    public static Roi.UseCase find(List<Roi.UseCase> ucs, String name) {
        String key = name.replace('_', ' ');
        return ucs.stream().filter(u -> u.name().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("유스케이스 없음: " + key));
    }

    public static void main(String[] args) throws IOException {
        Roi.Config cfg = Roi.load(Path.of(args.length > 0 ? args[0] : "usecases.yaml"));
        Roi.UseCase uc = args.length > 1 ? find(cfg.usecases(), String.join(" ", List.of(args).subList(1, args.length)))
                : cfg.usecases().get(0);
        double[][] g = grid(uc, cfg.assumptions());
        System.out.println("[" + uc.name() + "] 회수 기간(개월) — 행: 활용률, 열: 하루 절감 분 배율");
        System.out.println("활용률  ×0.5   ×0.75  ×1.0");
        for (int i = 0; i < RATES.length; i++) {
            StringBuilder line = new StringBuilder(String.format("%4.0f%%   ", RATES[i] * 100));
            for (double pb : g[i]) line.append(Double.isInfinite(pb) ? "  불가  " : String.format("%5.1f  ", pb));
            System.out.println(line);
        }
    }
}
