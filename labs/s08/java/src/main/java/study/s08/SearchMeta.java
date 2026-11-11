package study.s08;

import com.pgvector.PGvector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 의미 검색 — ./mvnw -q compile exec:java -Dexec.mainClass=study.s08.SearchMeta -Dexec.args="고객 등급이 바뀐 이력은 어디에 있나요?"
 * 용어만 보려면 환경변수 KIND=term
 */
public final class SearchMeta {

    static final String SQL = """
            SELECT kind, table_name, column_name, title,
                   1 - (embedding <=> ?) AS similarity      -- <=> 는 코사인 '거리', 1 - 거리 = 유사도
            FROM meta_chunk
            WHERE (?::text IS NULL OR kind = ?)
            ORDER BY embedding <=> ?                        -- 거리 오름차순 = 가까운 순
            LIMIT ?
            """;

    public static void main(String[] args) throws Exception {
        String question = args.length > 0 ? String.join(" ", args) : "고객 등급이 바뀐 이력은 어디에 있나요?";
        String kind = System.getenv("KIND");            // null 이면 필터 없음
        PGvector q = new PGvector(new E5Embedder().query(question));

        try (Connection conn = DriverManager.getConnection(LoadMeta.PGURL)) {
            PGvector.registerTypes(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("SET hnsw.ef_search = 100");   // 후보 목록 크기 (기본 40) — 크면 정확, 느림
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL)) {
                ps.setObject(1, q);
                ps.setString(2, kind);
                ps.setString(3, kind);
                ps.setObject(4, q);
                ps.setInt(5, 5);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String table = rs.getString("table_name");
                        String where = table == null ? "(용어)" : table + "." + rs.getString("column_name");
                        System.out.printf("%.3f  %-28s %s%n", rs.getDouble("similarity"), where, rs.getString("title"));
                    }
                }
            }
        }
    }
}
