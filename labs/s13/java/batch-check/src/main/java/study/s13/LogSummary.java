package study.s13;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 야간 배치 로그를 읽어 요약합니다.
 * 로그 한 줄 형식: '시각 수준 작업 단계 메시지'
 * 예) 2026-10-29T01:12:31 ERROR ADW_ACCT_TXN_DAILY STG_LOAD ORA-12899 value too large for column
 */
public final class LogSummary {

    public enum Level { INFO, WARN, ERROR }

    public record LogRecord(String ts, Level level, String job, String step, String message) { }

    private LogSummary() { }

    /** 로그 한 줄을 읽습니다. 형식이 틀리면 IllegalArgumentException. */
    public static LogRecord parseLine(String line) {
        String[] parts = line.strip().split("\\s+", 5);
        if (parts.length < 5 || !isLevel(parts[1])) {
            throw new IllegalArgumentException("로그 형식 오류: '" + line.strip() + "'");
        }
        return new LogRecord(parts[0], Level.valueOf(parts[1]), parts[2], parts[3], parts[4]);
    }

    /** 수준별 건수를 돌려줍니다. 빈 줄은 건너뜁니다. */
    public static Map<Level, Integer> countByLevel(List<String> lines) {
        Map<Level, Integer> counts = new EnumMap<>(Level.class);
        for (Level level : Level.values()) counts.put(level, 0);
        for (String line : lines) {
            if (line.isBlank()) continue;
            counts.merge(parseLine(line).level(), 1, Integer::sum);
        }
        return counts;
    }

    private static boolean isLevel(String s) {
        for (Level level : Level.values()) {
            if (level.name().equals(s)) return true;
        }
        return false;
    }

    /** ./mvnw -q compile exec:java -Dexec.args=batch.log */
    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(args.length > 0 ? args[0] : "batch.log"), StandardCharsets.UTF_8);
        System.out.println(countByLevel(lines));
    }
}
