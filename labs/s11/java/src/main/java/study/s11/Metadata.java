package study.s11;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 메타데이터 읽기와 스키마 링킹 — ... -Dexec.mainClass=study.s11.Metadata -Dexec.args="질문" 으로 링킹 결과만 확인 */
public final class Metadata {

    public record Column(String name, String type, String comment) {}
    public record TableInfo(String comment, List<Column> columns) {}
    public record Linked(List<String> tables, Map<String, String> terms) {}

    static final String OWNER = System.getenv().getOrDefault("DW_OWNER", "DW");
    static final ObjectMapper JSON = new ObjectMapper();

    static final String COLS_SQL = """
            SELECT c.table_name, c.column_name, c.data_type, c.data_length, c.nullable,
                   NVL(cc.comments, '(설명 없음)') AS col_comment,
                   NVL(tc.comments, '(설명 없음)') AS tab_comment
            FROM   all_tab_columns c
            LEFT JOIN all_col_comments cc
                   ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
            LEFT JOIN all_tab_comments tc
                   ON tc.owner = c.owner AND tc.table_name = c.table_name
            WHERE  c.owner = ?
            ORDER  BY c.table_name, c.column_id
            """;

    /** thin 드라이버 접속. 읽기 전용 계정 정보는 환경변수로만 받습니다. */
    public static Connection connect() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", System.getenv("ORA_USER"));
        p.setProperty("password", System.getenv("ORA_PASSWORD"));
        String dsn = System.getenv().getOrDefault("ORA_DSN", "localhost:1521/FREEPDB1");
        Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@" + dsn, p);
        conn.setReadOnly(true);           // 계정 권한(READ)에 더한 한 겹. 진짜 방어선은 DB 권한입니다
        conn.setAutoCommit(false);        // EXPLAIN PLAN 결과를 rollback 으로 지우기 위해
        return conn;
    }

    /** {테이블명: (설명, [(컬럼, 타입, 설명), ...])} */
    public static Map<String, TableInfo> loadTables(Connection conn) throws SQLException {
        Map<String, TableInfo> tables = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(COLS_SQL)) {
            ps.setQueryTimeout(30);       // 30초 넘는 조회는 끊습니다
            ps.setString(1, OWNER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dtype = rs.getString("data_type");
                    String type = dtype.startsWith("VARCHAR") || dtype.startsWith("CHAR")
                            ? dtype + "(" + rs.getInt("data_length") + ")" : dtype;
                    tables.computeIfAbsent(OWNER + "." + rs.getString("table_name"),
                                    k -> safe(() -> new TableInfo(rs.getString("tab_comment"), new ArrayList<>())))
                            .columns().add(new Column(rs.getString("column_name"), type, rs.getString("col_comment")));
                }
            }
        }
        return tables;
    }

    interface SqlSupplier<T> { T get() throws SQLException; }

    static <T> T safe(SqlSupplier<T> s) {
        try {
            return s.get();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, String> loadGlossary(Path path) throws IOException {
        return JSON.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, String>>() {});
    }

    /** 한글은 2글자 조각, 영문·숫자는 단어 단위. 조사 차이를 흡수하려는 단순한 방식입니다. */
    static Set<String> tokens(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("[a-z0-9_]{2,}").matcher(t);
        while (m.find()) out.add(m.group());
        Matcher h = Pattern.compile("[가-힣]+").matcher(t);
        while (h.find()) {
            String w = h.group();
            for (int i = 0; i + 2 <= w.length(); i++) out.add(w.substring(i, i + 2));
        }
        return out;
    }

    static int overlap(Set<String> a, Set<String> b) {
        Set<String> c = new HashSet<>(a);
        c.retainAll(b);
        return c.size();
    }

    /** 질문과 겹치는 용어사전 항목을 고르고, 테이블·컬럼 설명과 겹치는 정도로 테이블 점수를 매깁니다. */
    public static Linked linkSchema(String question, Map<String, TableInfo> tables, Map<String, String> glossary) {
        Set<String> q = tokens(question);
        Map<String, String> terms = new LinkedHashMap<>();
        glossary.forEach((k, v) -> {
            if (overlap(tokens(k), q) > 0 || question.contains(k)) terms.put(k, v);
        });
        String termText = String.join(" ", terms.values()).toUpperCase(Locale.ROOT);
        record Scored(int score, String name) {}
        List<Scored> scored = new ArrayList<>();
        tables.forEach((name, t) -> {
            StringBuilder doc = new StringBuilder(t.comment());
            t.columns().forEach(c -> doc.append(' ').append(c.name()).append(' ').append(c.comment()));
            int score = overlap(q, tokens(doc.toString()));
            if (termText.contains(name) || termText.contains(name.substring(name.indexOf('.') + 1))) {
                score += 5;                               // 용어사전이 직접 가리키는 테이블은 가산점
            }
            scored.add(new Scored(score, name));
        });
        scored.sort(Comparator.comparingInt(Scored::score).thenComparing(Scored::name).reversed());
        List<String> picked = new ArrayList<>(scored.stream().filter(s -> s.score() > 0).limit(4).map(Scored::name).toList());
        if (!picked.contains("DW.CMN_CODE_D") && Pattern.compile("코드|채널|분류|지역|비대면").matcher(question).find()) {
            picked.add("DW.CMN_CODE_D");                  // 코드명을 보여 달라는 질문이면 공통코드를 함께
        }
        return new Linked(picked, terms);
    }

    public static String schemaText(Map<String, TableInfo> tables, List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            TableInfo t = tables.get(n);
            sb.append("테이블 ").append(n).append(" -- ").append(t.comment()).append('\n');
            for (Column c : t.columns()) {
                sb.append("  ").append(c.name()).append(' ').append(c.type()).append(" -- ").append(c.comment()).append('\n');
            }
        }
        return sb.toString().strip();
    }

    public static void main(String[] args) throws Exception {
        String question = args.length > 0 ? String.join(" ", args) : "지난달 영업일 기준 채널별 거래금액";
        try (Connection conn = connect()) {
            Map<String, TableInfo> tables = loadTables(conn);
            Linked linked = linkSchema(question, tables, loadGlossary(Path.of("glossary.json")));
            System.out.println("고른 테이블: " + linked.tables());
            System.out.println("관련 용어: " + linked.terms().keySet());
            System.out.println(schemaText(tables, linked.tables()));
        }
    }
}
