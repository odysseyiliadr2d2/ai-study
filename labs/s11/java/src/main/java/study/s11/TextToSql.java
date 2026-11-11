package study.s11;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.jdbc.OraclePreparedStatement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Text-to-SQL CLI — ... -Dexec.mainClass=study.s11.TextToSql -Dexec.args="'지난달 영업일 기준 채널별 거래금액' --base-dt 20261029"
 * 결과·SQL·설명을 보여 주고, review_log.jsonl 에 담당자 검토용 기록을 남깁니다.
 */
public final class TextToSql {

    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final long MAX_COST = Long.parseLong(System.getenv().getOrDefault("MAX_PLAN_COST", "100000"));
    static final ObjectMapper JSON = new ObjectMapper();
    static final AnthropicClient CLIENT = AnthropicOkHttpClient.fromEnv();

    static final String SYSTEM = """
            당신은 증권사 정보계 Oracle DW 의 SQL 작성 도우미입니다.
            규칙:
            - 주어진 <스키마> 의 테이블·컬럼만 씁니다. 테이블은 반드시 스키마명.테이블명 으로 씁니다.
            - Oracle 문법만 씁니다. 행 제한은 FETCH FIRST n ROWS ONLY 로 합니다 (LIMIT 금지).
            - 날짜·코드처럼 질문에서 온 값은 바인드 변수(:from_dt 등)로 두고 값은 binds 에 넣습니다.
            - 일자 컬럼은 'YYYYMMDD' 문자열입니다. 상대 날짜(지난달, 최근 5영업일)는 <기준일> 로 계산합니다.
            - 조회문(SELECT/WITH) 하나만 만듭니다. 데이터를 바꾸는 문장은 절대 만들지 않습니다.
            - 질문이 모호하면 가장 흔한 해석을 고르고 assumptions 에 적습니다.
            JSON 하나로만 답하세요: {"sql": "...", "binds": {"이름": "값"}, "assumptions": ["..."]}""";

    /** 응답 앞에 thinking 블록이 올 수 있으므로 text 블록만 모읍니다. */
    static String ask(String system, String user, int maxTokens) {
        Message msg = CLIENT.messages().create(MessageCreateParams.builder()
                .model(MODEL).maxTokens(maxTokens).system(system).addUserMessage(user).build());
        return msg.content().stream().flatMap(b -> b.text().stream()).map(TextBlock::text).collect(Collectors.joining());
    }

    static JsonNode askJson(String user) throws Exception {
        String text = ask(SYSTEM, user, 2000);
        return JSON.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
    }

    static String buildPrompt(String question, String baseDt, String schema, Map<String, String> terms,
                              JsonNode examples, String error) {
        List<String> shots = new ArrayList<>();
        for (JsonNode e : examples) {
            shots.add("질문: " + e.path("question").asText() + "\nSQL:\n" + e.path("sql").asText() + "\nbinds: " + e.path("binds"));
        }
        String glossary = terms.isEmpty() ? "- (해당 없음)"
                : terms.entrySet().stream().map(t -> "- " + t.getKey() + ": " + t.getValue()).collect(Collectors.joining("\n"));
        String prompt = "<기준일>" + baseDt + "</기준일>\n\n<스키마>\n" + schema + "\n</스키마>\n\n<업무용어>\n" + glossary
                + "\n</업무용어>\n\n<예시>\n" + String.join("\n\n", shots) + "\n</예시>\n\n질문: " + question;
        if (error != null) prompt += "\n\n직전 SQL 이 검증에서 거부되었습니다: " + error + "\n규칙을 지켜 다시 작성하세요.";
        return prompt;
    }

    /** EXPLAIN PLAN 으로 옵티마이저 예상 비용을 봅니다. 실패하면 null (실행은 계속, 로그에 남김).
     *  EXPLAIN PLAN 에는 바인드 값을 넘기지 않으므로, 확인용 SQL 에만 값을 작은따옴표 리터럴로 넣습니다. */
    static Long planCost(Connection conn, String sql, Map<String, String> binds) {
        String sid = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String literal = sql;
        for (var b : binds.entrySet()) {
            String quoted = "'" + b.getValue().replace("'", "''") + "'";   // 작은따옴표는 두 번 써서 이스케이프
            literal = literal.replaceAll(":" + b.getKey() + "\\b", java.util.regex.Matcher.quoteReplacement(quoted));
        }
        try (Statement st = conn.createStatement()) {
            st.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + sid + "' FOR " + literal);
            try (PreparedStatement ps = conn.prepareStatement("SELECT cost FROM plan_table WHERE statement_id = ? AND id = 0")) {
                ps.setString(1, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    Long cost = rs.next() ? (Long) rs.getObject(1, Long.class) : null;
                    conn.rollback();                   // PLAN_TABLE 은 세션 임시 테이블. 남기지 않습니다
                    return cost;
                }
            }
        } catch (SQLException e) {                     // 권한·버전에 따라 막힐 수 있음
            System.out.println("(실행 계획 확인 생략: " + e.getMessage() + ")");
            return null;
        }
    }

    record Result(List<String> cols, List<List<String>> rows) {}

    /** 이름 있는 바인드(:from_dt)를 그대로 쓰기 위해 Oracle 확장 API 의 setStringAtName 을 씁니다. */
    static Result run(Connection conn, String sql, Map<String, String> binds) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            ps.setMaxRows(SqlGuard.MAX_ROWS);
            OraclePreparedStatement ops = ps.unwrap(OraclePreparedStatement.class);
            for (var b : binds.entrySet()) ops.setStringAtName(b.getKey(), b.getValue());
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i));
                List<List<String>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i <= cols.size(); i++) row.add(String.valueOf(rs.getObject(i)));
                    rows.add(row);
                }
                return new Result(cols, rows);
            }
        }
    }

    static String explain(String question, String sql, Result r, JsonNode assumptions) {
        String sample = r.rows().stream().limit(20).map(row -> String.join(" | ", row)).collect(Collectors.joining("\n"));
        return ask("SQL 결과를 업무 담당자에게 설명합니다. 합니다체로 5문장 이내. "
                        + "1) 질문에 대한 답 2) 어떤 테이블·조건으로 셌는지 3) 담당자가 확인할 가정을 씁니다. "
                        + "결과에 없는 숫자는 만들지 않습니다.",
                "질문: " + question + "\nSQL:\n" + sql + "\n가정: " + assumptions + "\n컬럼: " + r.cols()
                        + "\n결과(최대 20행):\n" + sample, 1500);
    }

    public static void main(String[] args) throws Exception {
        String baseDt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<String> words = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--base-dt") && i + 1 < args.length) baseDt = args[++i];
            else words.add(args[i]);
        }
        if (words.isEmpty()) throw new IllegalArgumentException("질문을 넣어 주세요");
        String question = String.join(" ", words);

        Map<String, String> glossary = Metadata.loadGlossary(Path.of("glossary.json"));
        JsonNode examples = JSON.readTree(Path.of("examples.json").toFile());
        try (Connection conn = Metadata.connect()) {
            Map<String, Metadata.TableInfo> tables = Metadata.loadTables(conn);
            Metadata.Linked linked = Metadata.linkSchema(question, tables, glossary);
            String schema = Metadata.schemaText(tables, linked.tables());
            Set<String> allowed = tables.keySet();                 // 이 계정이 볼 수 있는 DW 테이블만

            String error = null, safe = null;
            JsonNode out = null;
            for (int attempt = 0; attempt < 2 && safe == null; attempt++) {   // 거부되면 이유를 붙여 한 번만 재시도
                out = askJson(buildPrompt(question, baseDt, schema, linked.terms(), examples, error));
                try {
                    safe = SqlGuard.check(out.path("sql").asText(), allowed);
                } catch (SqlGuard.UnsafeSql e) {
                    error = e.getMessage();
                    System.out.println("[검증 거부 " + (attempt + 1) + "] " + error);
                }
            }
            if (safe == null) throw new IllegalStateException("안전한 SQL 을 만들지 못했습니다. 질문을 구체적으로 바꿔 주세요.");

            Set<String> needed = SqlGuard.bindNames(safe);         // SQL 에 실제로 있는 바인드만 넘깁니다
            Map<String, String> binds = new LinkedHashMap<>();
            out.path("binds").fields().forEachRemaining(e -> {
                if (needed.contains(e.getKey())) binds.put(e.getKey(), e.getValue().asText());
            });
            if (!binds.keySet().containsAll(needed)) {
                Set<String> missing = new TreeSet<>(needed);
                missing.removeAll(binds.keySet());
                throw new IllegalStateException("바인드 값이 빠졌습니다: " + missing);
            }
            Long cost = planCost(conn, safe, binds);
            if (cost != null && cost > MAX_COST) {
                throw new IllegalStateException("예상 비용 " + cost + " 이 상한 " + MAX_COST + " 를 넘습니다. 기간·조건을 줄여 주세요.");
            }
            Result r = run(conn, safe, binds);

            System.out.println("— SQL\n" + safe + "\n— binds " + binds + "  · 예상 비용 " + cost + "\n");
            System.out.println(String.join(" | ", r.cols()));
            r.rows().stream().limit(30).forEach(row -> System.out.println(String.join(" | ", row)));
            System.out.printf("(%d행%s)%n%n", r.rows().size(), r.rows().size() == SqlGuard.MAX_ROWS ? ", 상한 도달" : "");
            System.out.println("— 설명\n" + explain(question, safe, r, out.path("assumptions")));

            Map<String, Object> log = new LinkedHashMap<>();
            log.put("at", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            log.put("question", question);
            log.put("base_dt", baseDt);
            log.put("tables", linked.tables());
            log.put("sql", safe);
            log.put("binds", binds);
            log.put("assumptions", out.path("assumptions"));
            log.put("rows", r.rows().size());
            log.put("cost", cost);
            log.put("status", "검토대기");
            Files.writeString(Path.of("review_log.jsonl"), JSON.writeValueAsString(log) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
