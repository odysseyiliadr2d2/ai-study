package study.s03;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 템플릿 하나를 실행합니다. 두 번째 인자로 준 파일 내용이 템플릿의 '주 입력' 변수에 들어가고,
 * 나머지 변수는 아래 기본값을 씁니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s03.RunTemplate -Dexec.args="log_summary ../sample_log.txt"
 */
public class RunTemplate {
    // 템플릿별 주 입력 변수
    static final Map<String, String> MAIN_VAR = Map.of(
            "column_doc", "columns", "log_summary", "log", "action_items", "notes",
            "sql_review", "sql", "report_draft", "memo");

    // 나머지 변수의 실습용 기본값
    static final Map<String, Map<String, String>> DEFAULTS = Map.of(
            "column_doc", Map.of("schema", "ADW", "table", "TB_ADW_TRD_DAILY", "table_desc", "일별 거래 집계"),
            "log_summary", Map.of("base_date", "2026-10-28"),
            "action_items", Map.of("meeting", "정보계 차세대 주간 회의", "date", "2026-10-28", "attendees", "PM, DW팀, ETL팀"),
            "sql_review", Map.of("purpose", "월별 지점 실적 집계", "context", "TB_TRD_DAILY 는 일 200만 건, BASE_DT 에 파티션"),
            "report_draft", Map.of("period", "10/20~10/24", "metrics", "배치 성공률 99.2% (지난주 98.7%)\n장애 2건 (지난주 4건)"));

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("사용법: -Dexec.args=\"템플릿이름 입력파일\"  예) log_summary ../sample_log.txt");
            System.exit(1);
        }
        String name = args[0];
        Map<String, String> values = new HashMap<>(DEFAULTS.getOrDefault(name, Map.of()));
        values.put(MAIN_VAR.get(name), Files.readString(Path.of(args[1])));

        Templates.Rendered r = Templates.render(name, values);
        Claude.Answer a = Claude.ask(r.system(), r.user(), 2048);
        System.out.println(a.text());
        System.out.printf("%n[토큰] 입력 %d · 출력 %d%n", a.inputTokens(), a.outputTokens());
    }
}
