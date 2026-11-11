package study.s12;

import java.sql.*;
import java.util.Properties;

/** 코멘트 빈칸 찾기 (01_find_missing.sql 의 1·3번) — ... -Dexec.mainClass=study.s12.CommentAudit -Dexec.args=DW */
public final class CommentAudit {

    /** 읽기 전용 계정으로 접속합니다. 계정 정보는 환경변수로만 받습니다. */
    public static Connection connect() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", System.getenv("ORA_USER"));
        p.setProperty("password", System.getenv("ORA_PASSWORD"));
        Connection conn = DriverManager.getConnection(
                "jdbc:oracle:thin:@" + System.getenv().getOrDefault("ORA_DSN", "localhost:1521/FREEPDB1"), p);
        conn.setReadOnly(true);
        return conn;
    }

    static final String FILL_SQL = """
            SELECT COUNT(*) AS col_cnt,
                   COUNT(CASE WHEN TRIM(cc.comments) IS NOT NULL THEN 1 END) AS commented_cnt
            FROM   all_tab_columns c
            JOIN   all_tables t ON t.owner = c.owner AND t.table_name = c.table_name      -- 뷰는 빼고 테이블만
            LEFT JOIN all_col_comments cc
                   ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
            WHERE  c.owner = ?
            """;

    static final String MISSING_SQL = """
            SELECT c.table_name, c.column_name, c.data_type, cc.comments,
                   CASE WHEN TRIM(cc.comments) IS NULL THEN '없음'
                        WHEN UPPER(REPLACE(REPLACE(cc.comments, ' ', ''), '_', '')) = REPLACE(c.column_name, '_', '') THEN '컬럼명과 같음'
                        WHEN LENGTH(TRIM(cc.comments)) < 3 THEN '너무 짧음'
                   END AS issue
            FROM   all_tab_columns c
            JOIN   all_tables t ON t.owner = c.owner AND t.table_name = c.table_name
            LEFT JOIN all_col_comments cc
                   ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
            WHERE  c.owner = ?
            AND   (TRIM(cc.comments) IS NULL
                   OR UPPER(REPLACE(REPLACE(cc.comments, ' ', ''), '_', '')) = REPLACE(c.column_name, '_', '')
                   OR LENGTH(TRIM(cc.comments)) < 3)
            ORDER  BY c.table_name, c.column_id
            """;

    public static void main(String[] args) throws SQLException {
        String owner = (args.length > 0 ? args[0] : "DW").toUpperCase();
        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement(FILL_SQL)) {
                ps.setString(1, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int total = rs.getInt(1), filled = rs.getInt(2);
                    // 이 한 줄이 정비 전 기준선입니다. 정비 후 다시 실행해 비교합니다
                    System.out.printf("%s 컬럼 %d개 중 코멘트 %d개 (%.1f%%)%n", owner, total, filled,
                            total == 0 ? 0 : 100.0 * filled / total);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(MISSING_SQL)) {
                ps.setString(1, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("%-20s %-20s %-10s %-12s %s%n", rs.getString(1), rs.getString(2),
                                rs.getString(3), rs.getString(5), rs.getString(4) == null ? "" : rs.getString(4));
                    }
                }
            }
        }
    }
}
