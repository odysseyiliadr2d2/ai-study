package study.s24;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AX 유스케이스 ROI 계산기 — Python 트랙 roi.py 와 같은 식, 같은 usecases.yaml.
 *   ./mvnw -q compile exec:java -Dexec.args="usecases.yaml --md"
 *
 * 연간 절감 시간 = 사용자 × 하루 절감 분 ÷ 60 × 근무일 × 활용률 × (1 − 검토 비율)
 * 연간 순효과   = 절감 시간 × 시간당 인건비 − 연간 운영비
 * 회수 기간(월) = 초기 구축비 ÷ (연간 순효과 ÷ 12)
 * 3년 ROI      = (3 × 연간 순효과 − 초기 구축비) ÷ 초기 구축비
 * 결과는 입력값에 따른 '추정'입니다. 파일럿 실측값으로 다시 계산하세요.
 */
public final class Roi {

    public record Assumptions(double hourlyCost, double workDays, double adoptionRate, double reviewRatio) {
        Assumptions withAdoption(double rate) {
            return new Assumptions(hourlyCost, workDays, rate, reviewRatio);
        }
    }

    public record UseCase(String name, double users, double minutesSaved, double oneTimeCost, double annualRunCost,
                          int effect, int difficulty, String source) {
        UseCase scaleMinutes(double s) {
            return new UseCase(name, users, minutesSaved * s, oneTimeCost, annualRunCost, effect, difficulty, source);
        }
    }

    public record Row(UseCase uc, double hours, double saving, double net, double payback, double roi3, String quadrant) {}

    public static Row evaluate(UseCase u, Assumptions a) {
        double hours = u.users() * u.minutesSaved() / 60 * a.workDays() * a.adoptionRate() * (1 - a.reviewRatio());
        double saving = hours * a.hourlyCost();
        double net = saving - u.annualRunCost();
        double payback = net > 0 ? u.oneTimeCost() / (net / 12) : Double.POSITIVE_INFINITY;
        double roi3 = (3 * net - u.oneTimeCost()) / u.oneTimeCost();
        String quadrant = u.effect() >= 4 && u.difficulty() <= 3 ? "먼저 할 것"
                : u.effect() >= 4 ? "전략 과제"
                : u.difficulty() <= 3 ? "빠른 개선" : "보류";
        return new Row(u, hours, saving, net, payback, roi3, quadrant);
    }

    /** 회수 기간이 짧은 순, 같으면 3년 ROI 가 큰 순 */
    public static List<Row> rank(List<UseCase> ucs, Assumptions a) {
        List<Row> rows = new ArrayList<>();
        for (UseCase u : ucs) rows.add(evaluate(u, a));
        rows.sort(Comparator.comparingDouble(Row::payback).thenComparing(Comparator.comparingDouble(Row::roi3).reversed()));
        return rows;
    }

    // ── 입력 읽기
    public record Config(Assumptions assumptions, List<UseCase> usecases) {}

    @SuppressWarnings("unchecked")
    public static Config load(Path file) throws IOException {
        Map<String, Object> cfg;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            cfg = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
        }
        Map<String, Object> a = (Map<String, Object>) cfg.get("assumptions");
        Assumptions as = new Assumptions(num(a, "hourly_cost_krw"), num(a, "work_days_per_year"),
                num(a, "adoption_rate"), num(a, "quality_review_ratio"));
        List<UseCase> ucs = new ArrayList<>();
        for (Map<String, Object> u : (List<Map<String, Object>>) cfg.get("usecases")) {
            ucs.add(new UseCase(String.valueOf(u.get("name")), num(u, "users"), num(u, "minutes_saved_per_user_per_day"),
                    num(u, "one_time_cost_krw"), num(u, "annual_run_cost_krw"),
                    (int) num(u, "effect"), (int) num(u, "difficulty"),
                    String.valueOf(u.getOrDefault("source", "추정(예시)"))));   // 숫자의 근거: 실측·인터뷰·견적·가정
        }
        return new Config(as, ucs);
    }

    static double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (!(v instanceof Number n)) throw new IllegalArgumentException(k + " 값이 숫자가 아닙니다: " + v);
        return n.doubleValue();
    }

    // ── 출력 형식
    public static String won(double x) {
        return String.format("%,.1f백만", x / 1e6);
    }

    public static String months(double payback) {
        return Double.isInfinite(payback) ? "회수 불가" : String.format("%.1f개월", payback);
    }

    public static String pct(double x) {
        return String.format("%.0f%%", x * 100);
    }

    public static String markdownTable(List<Row> rows) {
        StringBuilder sb = new StringBuilder("| 순위 | 유스케이스 | 사분면 | 연간 절감 시간 | 연간 순효과(원) | 회수 기간 | 3년 ROI | 근거 |\n");
        sb.append("|---:|---|---|---:|---:|---:|---:|---|\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            sb.append(String.format("| %d | %s | %s | %,.0fh | %s | %s | %s | %s |%n", i + 1, r.uc().name(), r.quadrant(),
                    r.hours(), won(r.net()), months(r.payback()), pct(r.roi3()), r.uc().source()));
        }
        return sb.toString();
    }

    public static String assumptionsLine(Assumptions a) {
        return String.format("가정: 시간당 %,.0f원 · 근무일 %.0f일 · 활용률 %s · 검토 비율 %s (예시 값)",
                a.hourlyCost(), a.workDays(), pct(a.adoptionRate()), pct(a.reviewRatio()));
    }

    public static void main(String[] args) throws IOException {
        List<String> files = new ArrayList<>();
        boolean md = false;
        for (String x : args) {
            if (x.equals("--md")) md = true;
            else files.add(x);
        }
        Config cfg = load(Path.of(files.isEmpty() ? "usecases.yaml" : files.get(0)));
        List<Row> rows = rank(cfg.usecases(), cfg.assumptions());
        if (md) {
            System.out.print(markdownTable(rows));
        } else {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                System.out.printf("%d. %s [%s]%n", i + 1, r.uc().name(), r.quadrant());
                System.out.printf("   절감 %,.0f시간/년 · 순효과 %s원/년 · 회수 %s · 3년 ROI %s%n",
                        r.hours(), won(r.net()), months(r.payback()), pct(r.roi3()));
            }
        }
        System.out.println("\n" + assumptionsLine(cfg.assumptions()));
    }
}
