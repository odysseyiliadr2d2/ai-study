package study.s24;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사업 계획 숫자 보고서(Markdown) — 제안서 템플릿 1장(한 줄 요약) · 4장(숫자) · 5장(위험 등급)에 붙일 내용을 한 파일로 만듭니다.
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s24.BusinessCaseReport \
 *     -Dexec.args="usecases.yaml ../../s23/java/review.md"  > business_case.md
 * 두 번째 인자(세션 23 의 review.md)가 있으면 위험 점수·등급과 규제 확인 일자를 읽어 넣습니다.
 */
public final class BusinessCaseReport {

    static final Pattern RISK = Pattern.compile("위험 점수: \\*\\*(\\d+)점 → (\\S+)\\*\\*");
    static final Pattern REG_DATE = Pattern.compile("근거 규정은 (\\d{4}-\\d{2}-\\d{2}) 기준");

    public static String build(Roi.Config cfg, String review, LocalDate today) {
        List<Roi.Row> rows = Roi.rank(cfg.usecases(), cfg.assumptions());
        Roi.Row top = rows.get(0);
        double[][] g = Sensitivity.grid(top.uc(), cfg.assumptions());
        double worst = g[1][0];   // 활용률 50% · 절감 분 ×0.5
        StringBuilder sb = new StringBuilder();
        sb.append("# AX 사업 계획 숫자 — ").append(top.uc().name()).append("\n\n");
        sb.append("> 작성일: ").append(today).append(" · 모든 값은 입력에 따른 추정입니다. 파일럿 실측 후 다시 계산합니다.\n\n");

        sb.append("## 1. 한 줄 요약 (제안서 1장)\n");
        sb.append(String.format("연간 약 %,.0f시간 절감, 회수 기간 약 %s(추정, 파일럿에서 실측 예정).%n",
                top.hours(), Roi.months(top.payback())));
        sb.append(String.format("활용률 50%%·절감 시간 절반에서도 회수 기간 %s 입니다.%n%n", Roi.months(worst)));

        sb.append("## 2. 유스케이스 순위 (제안서 4장)\n");
        sb.append(Roi.markdownTable(rows)).append('\n');
        sb.append("- ").append(Roi.assumptionsLine(cfg.assumptions())).append('\n');
        sb.append("- 근거 열이 '추정(예시)' 인 숫자는 실측·인터뷰·견적으로 바꾼 뒤 usecases.yaml 의 source 에 적습니다.\n\n");

        sb.append("## 3. 2×2 배치 (효과 × 난이도)\n");
        for (String q : List.of("먼저 할 것", "전략 과제", "빠른 개선", "보류")) {
            List<String> names = rows.stream().filter(r -> r.quadrant().equals(q)).map(r -> r.uc().name()).toList();
            sb.append("- ").append(q).append(": ").append(names.isEmpty() ? "없음" : String.join(", ", names)).append('\n');
        }
        if (!top.quadrant().equals("먼저 할 것")) {
            sb.append("- 주의: 회수 기간 1순위가 '먼저 할 것' 칸이 아닙니다. 발표에서 이유를 설명합니다.\n");
        }

        sb.append("\n## 4. 민감도 — ").append(top.uc().name()).append(" 회수 기간(개월)\n");
        sb.append(Sensitivity.markdown(top.uc(), cfg.assumptions())).append('\n');

        sb.append("## 5. 위험 등급 (제안서 5장, 세션 23)\n");
        Matcher risk = review == null ? null : RISK.matcher(review);
        Matcher reg = review == null ? null : REG_DATE.matcher(review);
        if (risk != null && risk.find()) {
            sb.append("- 위험 점수 ").append(risk.group(1)).append("점 → ").append(risk.group(2)).append('\n');
            sb.append("- 규제 확인 일자: ").append(reg.find() ? reg.group(1) : "확인 필요").append(" (심의 전 최신 규정 재확인)\n");
        } else {
            sb.append("- 세션 23 의 risk_assess 결과(review.md)를 두 번째 인자로 주면 여기에 들어갑니다.\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        Roi.Config cfg = Roi.load(Path.of(args.length > 0 ? args[0] : "usecases.yaml"));
        String review = args.length > 1 ? Files.readString(Path.of(args[1]), StandardCharsets.UTF_8) : null;
        System.out.print(build(cfg, review, LocalDate.now()));
    }
}
