package study.s12;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 컬럼 설명 초안 만들기 — ... -Dexec.mainClass=study.s12.DraftComments -Dexec.args="DW [테이블명 ...]"
 * 결과는 검토용 CSV 와 COMMENT ON 스크립트(_DRAFT.sql) 두 파일로만 씁니다. DB 에는 절대 쓰지 않습니다.
 */
public final class DraftComments {

    static final String MODEL = System.getenv().getOrDefault("CLAUDE_MODEL", "claude-sonnet-5-5");
    static final ObjectMapper JSON = new ObjectMapper();
    static final AnthropicClient CLIENT = AnthropicOkHttpClient.fromEnv();

    static final String MISSING_SQL = """
            SELECT c.table_name, c.column_name, c.data_type, c.data_length, tc.comments
            FROM   all_tab_columns c
            JOIN   all_tables t ON t.owner = c.owner AND t.table_name = c.table_name
            LEFT JOIN all_col_comments cc
                   ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
            LEFT JOIN all_tab_comments tc ON tc.owner = c.owner AND tc.table_name = c.table_name
            WHERE  c.owner = ? AND TRIM(cc.comments) IS NULL
            AND    c.data_type NOT IN ('CLOB', 'NCLOB', 'BLOB', 'LONG', 'LONG RAW', 'RAW', 'XMLTYPE')   -- 프로파일 불가 타입 제외
            ORDER  BY c.table_name, c.column_id
            """;

    static final String SYSTEM = """
            당신은 증권사 정보계 DW 의 메타데이터 담당자를 돕습니다.
            컬럼 이름, 타입, 테이블 설명, 샘플 프로파일, 업무 용어사전을 보고 컬럼 코멘트 초안을 씁니다.
            규칙:
            - 용어사전에 표준용어가 있으면 반드시 그 표준용어로 시작합니다. 금지표현은 쓰지 않습니다.
            - 형식: '표준용어(단위·형식). 한 문장 보충 설명'. 80자 이내.
            - 코드 컬럼이면 샘플에 보이는 코드값을 '01 영업점'처럼 적되, 의미를 모르면 값만 적고 추측하지 않습니다.
            - 근거가 약하면 confidence 를 low 로 하고 question 에 담당자에게 물을 점을 씁니다.
            JSON 한 줄로만 답하세요: {"comment": "...", "confidence": "high|medium|low", "question": "..."}""";

    record Target(String table, String column, String type, String tableComment) {}

    /** 용어사전 CSV (표준용어,영문약어,정의,동의어,금지표현). 실습 파일은 값에 쉼표가 없습니다. */
    static List<Map<String, String>> loadGlossary(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String[] head = lines.get(0).replace("﻿", "").split(",", -1);
        List<Map<String, String>> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] v = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.length; i++) row.put(head[i], i < v.length ? v[i] : "");
            out.add(row);
        }
        return out;
    }

    /** 컬럼명 조각(TRD, AMT, CHNL…)이 용어사전 영문약어와 겹치는 항목만 고릅니다. */
    static List<Map<String, String>> relatedTerms(String column, List<Map<String, String>> glossary) {
        Set<String> parts = Set.of(column.split("_"));
        return glossary.stream().filter(g -> {
            String abbr = g.get("영문약어");
            return abbr.equals(column) || Arrays.stream(abbr.split("_")).anyMatch(parts::contains);
        }).toList();
    }

    static JsonNode draft(Target t, Map<String, Object> profile, List<Map<String, String>> terms) throws IOException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("table", t.table());
        input.put("table_comment", t.tableComment());
        input.put("column", t.column());
        input.put("type", t.type());
        input.put("profile", profile);
        input.put("glossary", terms);
        Message msg = CLIENT.messages().create(MessageCreateParams.builder()
                .model(MODEL).maxTokens(1000).system(SYSTEM).addUserMessage(JSON.writeValueAsString(input)).build());
        String text = msg.content().stream().flatMap(b -> b.text().stream()).map(TextBlock::text).collect(Collectors.joining());
        try {
            return JSON.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
        } catch (Exception e) {
            return JSON.createObjectNode().put("comment", "").put("confidence", "low")
                    .put("question", "초안 생성 실패: " + text.substring(0, Math.min(60, text.length())));
        }
    }

    static String csv(Object v) {
        String s = v == null ? "" : v.toString();
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** 작은따옴표를 두 번 써서 SQL 문자열로 만듭니다. 줄바꿈은 공백으로. */
    static String sqlLiteral(String s) {
        return "'" + String.join(" ", s.trim().split("\\s+")).replace("'", "''") + "'";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("사용법: 스키마명 [테이블명 ...]");
        String owner = args[0].toUpperCase();
        Set<String> only = new HashSet<>();
        for (int i = 1; i < args.length; i++) only.add(args[i].toUpperCase());
        List<Map<String, String>> glossary = loadGlossary(Path.of("glossary.csv"));
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = CommentAudit.connect()) {
            List<Target> targets = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(MISSING_SQL)) {
                ps.setString(1, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!only.isEmpty() && !only.contains(rs.getString(1))) continue;
                        targets.add(new Target(rs.getString(1), rs.getString(2),
                                rs.getString(3) + "(" + rs.getInt(4) + ")", Objects.toString(rs.getString(5), "")));
                    }
                }
            }
            for (Target t : targets) {
                Map<String, Object> prof;
                try {
                    prof = Profiler.profile(conn, owner, t.table(), t.column());
                } catch (SQLException e) {              // 권한 없음·타입 문제 등은 건너뛰고 기록
                    System.out.println(t.table() + "." + t.column() + ": 프로파일 실패 " + e.getMessage());
                    continue;
                }
                JsonNode d = draft(t, prof, relatedTerms(t.column(), glossary));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", t.table());
                row.put("column", t.column());
                row.put("type", t.type());
                row.put("null_pct", prof.get("null_pct"));
                row.put("distinct", prof.get("distinct"));
                row.put("top", ((List<?>) prof.get("top")).stream()
                        .map(x -> ((List<?>) x).get(0) + ":" + ((List<?>) x).get(1)).collect(Collectors.joining(" / ")));
                row.put("draft", d.path("comment").asText(""));
                row.put("confidence", d.path("confidence").asText("low"));
                row.put("question", d.path("question").asText(""));
                row.put("final", "");
                row.put("reviewer", "");
                rows.add(row);
                System.out.printf("%s.%-20s [%-6s] %s%n", t.table(), t.column(), row.get("confidence"), row.get("draft"));
            }
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        Map<String, Integer> order = Map.of("low", 0, "medium", 1, "high", 2);
        rows.sort(Comparator.comparing((Map<String, Object> r) -> order.getOrDefault((String) r.get("confidence"), 0))
                .thenComparing(r -> (String) r.get("table")));           // 확신도 낮은 것부터 검토

        Path review = Path.of("review_%s_%s.csv".formatted(owner, stamp));
        StringBuilder sheet = new StringBuilder("﻿");                 // 엑셀용 BOM
        List<String> fields = rows.isEmpty() ? List.of("table") : new ArrayList<>(rows.get(0).keySet());
        sheet.append(String.join(",", fields)).append('\n');
        for (Map<String, Object> r : rows) {
            sheet.append(fields.stream().map(f -> csv(r.get(f))).collect(Collectors.joining(","))).append('\n');
        }
        Files.writeString(review, sheet, StandardCharsets.UTF_8);

        Path script = Path.of("comments_%s_%s_DRAFT.sql".formatted(owner, stamp));
        StringBuilder sql = new StringBuilder("-- LLM 초안 (" + MODEL + ", " + stamp + "). 검토 전 실행 금지. 검토 후 확정본을 따로 만드세요.\n");
        for (Map<String, Object> r : rows) {
            String draft = (String) r.get("draft");
            if (draft.isBlank()) continue;
            String q = (String) r.get("question");
            if (!q.isBlank()) sql.append("-- [").append(r.get("confidence")).append("] ").append(q.replace("\n", " ")).append('\n');
            sql.append("COMMENT ON COLUMN ").append(owner).append('.').append(r.get("table")).append('.').append(r.get("column"))
                    .append(" IS ").append(sqlLiteral(draft)).append(";\n");
        }
        Files.writeString(script, sql, StandardCharsets.UTF_8);
        System.out.printf("%n%d개 컬럼 초안 → %s, %s%n", rows.size(), review, script);
    }
}
