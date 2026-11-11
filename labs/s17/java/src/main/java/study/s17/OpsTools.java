package study.s17;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 정보계 운영 도구 (모의 데이터판) — Python 트랙 ops_tools.py 와 같은 입력 · 같은 출력.
 * 실제 환경에서는 각 메서드 안쪽만 바꿉니다 (Oracle 읽기 전용 조회, 로그 시스템 API, ITSM 등록).
 */
public final class OpsTools {

    /** 모델에게 그대로 보여 줄 입력 오류. 무엇이 틀렸고 어떻게 고칠지 적습니다. */
    public static final class ToolError extends RuntimeException {
        public ToolError(String message) { super(message); }
    }

    /** 필수 파라미터 누락 · 타입 불일치 (Python 의 TypeError 자리) */
    public static final class ParamError extends RuntimeException {
        public ParamError(String message) { super(message); }
    }

    public static final Set<String> READ_TOOLS = Set.of("get_batch_log", "get_row_count", "search_table_spec");
    public static final Set<String> WRITE_TOOLS = Set.of("request_rerun");

    private static final Pattern JOB_RE = Pattern.compile("^[A-Z0-9_]{3,40}$");
    private static final Pattern TABLE_RE = Pattern.compile("^[A-Z0-9_]+\\.[A-Z0-9_]+$");
    private static final Pattern DATE_RE = Pattern.compile("^\\d{8}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    // 모의 데이터는 2026-10-28 밤 배치 기준이라 '오늘'도 고정합니다. OPS_TODAY= 로 비우면 시스템 날짜.
    public static final LocalDate TODAY = today();
    public static final String YESTERDAY = TODAY.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);

    private final Path data;
    private final Path outbox;

    public OpsTools() {
        this(Path.of(env("OPS_MOCK_DIR", "mock")), Path.of(env("OPS_OUTBOX", "outbox")));
    }

    public OpsTools(Path data, Path outbox) {
        this.data = data;
        this.outbox = outbox;
    }

    /** 도구 이름과 모델이 보낸 인자(JSON)로 해당 메서드를 부릅니다. */
    public String call(String name, JsonNode args) {
        return switch (name) {
            case "get_batch_log" -> getBatchLog(req(args, "job_name"), req(args, "run_date"),
                    args.path("level").asText("ALL"), args.path("limit").asInt(20));
            case "get_row_count" -> getRowCount(req(args, "table_name"), req(args, "base_date"));
            case "search_table_spec" -> searchTableSpec(req(args, "keyword"));
            case "request_rerun" -> requestRerun(req(args, "job_name"), req(args, "run_date"),
                    req(args, "from_step"), req(args, "reason"));
            default -> throw new ParamError("알 수 없는 도구: " + name);
        };
    }

    /** 배치 작업 로그를 시간순으로 돌려줍니다 (읽기 전용). */
    public String getBatchLog(String jobName, String runDate, String level, int limit) {
        String job = jobName.strip().toUpperCase(Locale.ROOT);
        if (!JOB_RE.matcher(job).matches()) {
            throw new ToolError("job_name 형식 오류: '" + job + "'. 예: ADW_ACCT_TXN_DAILY");
        }
        if (!DATE_RE.matcher(runDate).matches()) {
            throw new ToolError("run_date 는 YYYYMMDD 8자리입니다. 받은 값: '" + runDate + "'");
        }
        List<JsonNode> rows = readJsonl(data.resolve("batch_log.jsonl"));
        List<JsonNode> hits = rows.stream()
                .filter(r -> r.path("job").asText().equals(job) && r.path("run_date").asText().equals(runDate))
                .filter(r -> "ALL".equals(level) || r.path("level").asText().equals(level))
                .sorted(Comparator.comparing((JsonNode r) -> r.path("ts").asText()))
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
        if (hits.isEmpty()) {
            String jobs = rows.stream().filter(r -> r.path("run_date").asText().equals(runDate))
                    .map(r -> r.path("job").asText()).distinct().sorted().collect(Collectors.joining(", "));
            return job + " / " + runDate + " 로그 없음. 이 날짜에 로그가 있는 작업: " + (jobs.isEmpty() ? "없음" : jobs);
        }
        return hits.stream()
                .map(r -> "%s [%s] %s: %s".formatted(r.path("ts").asText(), r.path("level").asText(),
                        r.path("step").asText(), r.path("msg").asText()))
                .collect(Collectors.joining("\n"));
    }

    /** 테이블의 기준일자별 적재 건수와 직전 기준일 건수를 돌려줍니다 (읽기 전용). */
    public String getRowCount(String tableName, String baseDate) {
        String table = tableName.strip().toUpperCase(Locale.ROOT);
        if (!TABLE_RE.matcher(table).matches()) {
            throw new ToolError("table_name 은 '스키마.테이블' 형식입니다. 받은 값: '" + table + "'");
        }
        if (!DATE_RE.matcher(baseDate).matches()) {
            throw new ToolError("base_date 는 YYYYMMDD 8자리입니다. 받은 값: '" + baseDate + "'");
        }
        TreeMap<String, Long> byDate = new TreeMap<>();
        List<String> lines = readLines(data.resolve("row_counts.csv"));
        for (String line : lines.subList(1, lines.size())) {          // 첫 줄은 헤더
            String[] c = line.split(",");
            if (c.length == 3 && c[0].equals(table)) byDate.put(c[1], Long.parseLong(c[2]));
        }
        if (byDate.isEmpty()) {
            throw new ToolError(table + " 은 건수 조회 대상이 아닙니다. search_table_spec 으로 테이블명을 먼저 확인하세요.");
        }
        Long cur = byDate.get(baseDate);
        String prev = byDate.lowerKey(baseDate);
        List<String> parts = new ArrayList<>();
        parts.add(cur != null ? table + " " + baseDate + ": " + num(cur) + "건" : table + " " + baseDate + ": 적재 이력 없음");
        if (prev != null) parts.add("직전 기준일 " + prev + ": " + num(byDate.get(prev)) + "건");
        return String.join(" / ", parts);
    }

    /** 테이블 정의서에서 테이블명 · 한글명 · 컬럼명 · 설명으로 검색합니다 (읽기 전용). */
    public String searchTableSpec(String keyword) {
        String kw = keyword.strip().toUpperCase(Locale.ROOT);
        if (kw.length() < 2) {
            throw new ToolError("keyword 는 2자 이상으로 주세요. 예: STG_ACCT_TXN, BRNCH_CD, 지점코드");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode t : readJson(data.resolve("table_specs.json"))) {
            if (!t.toString().toUpperCase(Locale.ROOT).contains(kw)) continue;   // 한글은 그대로 출력됨
            List<String> cols = new ArrayList<>();
            t.path("columns").forEach(c -> cols.add("%s %s(%s)".formatted(
                    c.path("col").asText(), c.path("type").asText(), c.path("desc").asText())));
            out.add("■ %s [%s] 적재: %s · 원천: %s\n  컬럼: %s\n  비고: %s".formatted(
                    t.path("table").asText(), t.path("name").asText(), t.path("load_job").asText(),
                    t.path("source").asText(), String.join(", ", cols), t.path("notes").asText()));
        }
        return out.isEmpty() ? "'" + keyword + "' 와 일치하는 정의서 항목 없음"
                             : String.join("\n", out.subList(0, Math.min(5, out.size())));
    }

    /** 재실행 요청을 운영 요청함(outbox)에 등록합니다 (쓰기 · 사람 승인 후에만 호출). */
    public String requestRerun(String jobName, String runDate, String fromStep, String reason) {
        String job = jobName.strip().toUpperCase(Locale.ROOT);
        if (!JOB_RE.matcher(job).matches() || !DATE_RE.matcher(runDate).matches()) {
            throw new ToolError("job_name 또는 run_date 형식 오류. 예: ADW_ACCT_TXN_DAILY, 20261028");
        }
        if (reason.length() < 10) {
            throw new ToolError("reason 은 원인과 조치를 10자 이상으로 적어 주세요.");
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("requested_at", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        req.put("job", job);
        req.put("run_date", runDate);
        req.put("from_step", fromStep);
        req.put("reason", reason);
        req.put("status", "REQUESTED");
        try {
            Files.createDirectories(outbox);
            Files.writeString(outbox.resolve("rerun_requests.jsonl"), JSON.writeValueAsString(req) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "재실행 요청 등록 완료: %s %s (%s부터). 실제 실행은 운영 담당자가 합니다.".formatted(job, runDate, fromStep);
    }

    // ── 보조 메서드
    private static String req(JsonNode args, String key) {
        JsonNode v = args.get(key);
        if (v == null || v.isNull() || !v.isTextual()) {
            throw new ParamError("필수 문자열 파라미터 '" + key + "' 가 없거나 형식이 다릅니다");
        }
        return v.asText();
    }

    private static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n);       // 1,251,870
    }

    private static List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<JsonNode> readJsonl(Path p) {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : readLines(p)) {
            if (line.isBlank()) continue;
            try {
                rows.add(JSON.readTree(line));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
        return rows;
    }

    private static JsonNode readJson(Path p) {
        try {
            return JSON.readTree(p.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null ? def : v;
    }

    private static LocalDate today() {
        String v = System.getenv().getOrDefault("OPS_TODAY", "2026-10-29");
        return v.isBlank() ? LocalDate.now() : LocalDate.parse(v);
    }
}
