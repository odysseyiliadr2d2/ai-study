package study.s12;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 반영 전 현재 코멘트를 되돌리기용 COMMENT ON 스크립트로 떠 둡니다 — ... -Dexec.mainClass=study.s12.BackupComments -Dexec.args=DW */
public final class BackupComments {

    static final String SQL = """
            SELECT cc.table_name, cc.column_name, cc.comments
            FROM   all_col_comments cc
            JOIN   all_tables t ON t.owner = cc.owner AND t.table_name = cc.table_name
            WHERE  cc.owner = ?
            ORDER  BY cc.table_name, cc.column_name
            """;

    public static void main(String[] args) throws Exception {
        String owner = (args.length > 0 ? args[0] : "DW").toUpperCase();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        StringBuilder out = new StringBuilder("-- " + owner + " 컬럼 코멘트 백업 (" + stamp + "). 되돌릴 때 이 파일을 실행합니다.\n");
        int n = 0;
        try (Connection conn = CommentAudit.connect(); PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String comment = rs.getString(3) == null ? "" : rs.getString(3);
                    out.append("COMMENT ON COLUMN ").append(owner).append('.').append(rs.getString(1)).append('.')
                            .append(rs.getString(2)).append(" IS '").append(comment.replace("'", "''")).append("';\n");
                    n++;
                }
            }
        }
        Path file = Path.of("backup_comments_%s_%s.sql".formatted(owner, stamp));
        Files.writeString(file, out, StandardCharsets.UTF_8);
        System.out.println(n + "개 컬럼 코멘트 → " + file + " (변경 요청서에 확정 스크립트와 함께 첨부)");
    }
}
