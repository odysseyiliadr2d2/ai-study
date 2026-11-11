package study.s13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import study.s13.LogSummary.Level;
import study.s13.LogSummary.LogRecord;

/** ./mvnw -q test */
class LogSummaryTest {

    static final List<String> LINES = List.of(
            "2026-10-29T01:10:04 INFO ADW_ACCT_TXN_DAILY STG_LOAD STG 적재 시작",
            "2026-10-29T01:12:31 ERROR ADW_ACCT_TXN_DAILY STG_LOAD ORA-12899 value too large for column",
            "",
            "2026-10-29T01:12:32 WARN ADW_ACCT_TXN_DAILY ADW_MERGE 선행 블록 실패로 실행하지 않음",
            "2026-10-29T02:00:01 INFO ADW_BRNCH_DIM DIM_LOAD 적재 완료 412건");

    @Test
    void parseLine_은_다섯_필드를_읽는다() {
        LogRecord r = LogSummary.parseLine(LINES.get(1));
        assertEquals(Level.ERROR, r.level());
        assertEquals("ADW_ACCT_TXN_DAILY", r.job());
        assertEquals("STG_LOAD", r.step());
        assertEquals("ORA-12899 value too large for column", r.message());
    }

    @Test
    void 형식이_틀리면_예외() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LogSummary.parseLine("2026-10-29 DEBUG 형식이 다름"));
        assertTrue(e.getMessage().startsWith("로그 형식 오류"));
    }

    @Test
    void countByLevel_은_빈_줄을_건너뛴다() {
        assertEquals(Map.of(Level.INFO, 2, Level.WARN, 1, Level.ERROR, 1), LogSummary.countByLevel(LINES));
    }
}
