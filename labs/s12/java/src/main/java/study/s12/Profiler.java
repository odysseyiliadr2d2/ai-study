package study.s12;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 컬럼 프로파일 (샘플 2,000행) — ... -Dexec.mainClass=study.s12.Profiler -Dexec.args="DW TRD_DAY_F CHNL_CD" */
public final class Profiler {

    static final int SAMPLE_ROWS = 2000;      // 샘플은 앞쪽 일부만 (대형 테이블 보호)
    // 식별자 검사: SQL 에 끼워 넣기 전에 반드시 확인 (식별자는 바인드 변수로 넘길 수 없음)
    static final Pattern IDENT = Pattern.compile("^[A-Z][A-Z0-9_$#]{0,127}$");

    static String cut(Object v) {
        String s = String.valueOf(v);
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    /** NULL 비율 · 고유값 수 · 최소/최대 · 상위 값 5개 */
    public static Map<String, Object> profile(Connection conn, String owner, String table, String column) throws SQLException {
        for (String name : List.of(owner, table, column)) {
            if (!IDENT.matcher(name).matches()) throw new IllegalArgumentException("식별자 형식이 아닙니다: " + name);
        }
        String src = "(SELECT \"%s\" AS v FROM \"%s\".\"%s\" FETCH FIRST %d ROWS ONLY)".formatted(column, owner, table, SAMPLE_ROWS);
        Map<String, Object> p = new LinkedHashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(60);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*), COUNT(v), COUNT(DISTINCT v), MIN(v), MAX(v) FROM " + src)) {
                rs.next();
                long n = rs.getLong(1), nn = rs.getLong(2);
                p.put("rows", n);
                p.put("null_pct", n == 0 ? null : Math.round(1000.0 * (n - nn) / n) / 10.0);
                p.put("distinct", rs.getLong(3));
                p.put("min", cut(rs.getObject(4)));
                p.put("max", cut(rs.getObject(5)));
            }
            List<List<Object>> top = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT v, COUNT(*) c FROM " + src
                    + " WHERE v IS NOT NULL GROUP BY v ORDER BY c DESC FETCH FIRST 5 ROWS ONLY")) {
                while (rs.next()) top.add(List.of(cut(rs.getObject(1)), rs.getLong(2)));
            }
            p.put("top", top);
        }
        return p;
    }

    public static void main(String[] args) throws SQLException {
        if (args.length < 3) throw new IllegalArgumentException("사용법: 스키마 테이블 컬럼");
        try (Connection conn = CommentAudit.connect()) {
            System.out.println(profile(conn, args[0].toUpperCase(), args[1].toUpperCase(), args[2].toUpperCase()));
        }
    }
}
