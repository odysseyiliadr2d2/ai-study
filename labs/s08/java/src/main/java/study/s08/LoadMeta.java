package study.s08;

import com.pgvector.PGvector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/** 메타데이터를 문장으로 풀어 임베딩하고 저장 — ./mvnw -q compile exec:java -Dexec.mainClass=study.s08.LoadMeta -Dexec.args=meta.csv */
public final class LoadMeta {

    record Row(String kind, String table, String column, String title, String description) {}

    static final String PGURL = System.getenv().getOrDefault("PGURL",
            "jdbc:postgresql://localhost:5432/meta?user=postgres&password=study");

    /** 표의 한 행을 검색이 잘 되는 문장으로 바꿉니다 (물리명 + 한글명 + 설명). */
    static String toSentence(Row r) {
        return r.kind().equals("column")
                ? "테이블 %s 의 컬럼 %s (%s): %s".formatted(r.table(), r.column(), r.title(), r.description())
                : "용어 %s: %s".formatted(r.title(), r.description());
    }

    static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "meta.csv");
        // 실습 파일은 값 안에 쉼표가 없습니다. 실제 정의서를 CSV 로 받으면 Apache Commons CSV 같은 파서를 씁니다.
        List<Row> rows = Files.readAllLines(csv, StandardCharsets.UTF_8).stream()
                .skip(1).filter(l -> !l.isBlank())
                .map(l -> l.split(",", -1))
                .map(c -> new Row(c[0], c[1], c[2], c[3], c[4]))
                .toList();
        E5Embedder e5 = new E5Embedder();

        try (Connection conn = DriverManager.getConnection(PGURL)) {
            PGvector.registerTypes(conn);                 // vector 타입을 JDBC 에 등록
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("TRUNCATE meta_chunk");
            }
            String sql = "INSERT INTO meta_chunk (kind, table_name, column_name, title, content, embedding) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Row r : rows) {
                    String text = toSentence(r);
                    ps.setString(1, r.kind());
                    ps.setString(2, nullIfBlank(r.table()));
                    ps.setString(3, nullIfBlank(r.column()));
                    ps.setString(4, r.title());
                    ps.setString(5, text);
                    ps.setObject(6, new PGvector(e5.passage(text)));
                    ps.addBatch();                        // 수만 건이면 배치 크기를 나눠 executeBatch 또는 COPY
                }
                ps.executeBatch();
            }
            conn.commit();
        }
        System.out.println(rows.size() + "건 저장 완료");
    }
}
