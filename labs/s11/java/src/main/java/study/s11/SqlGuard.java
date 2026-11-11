package study.s11;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 생성 SQL 검증: 파싱 → SELECT만 → 허용 목록 → 행 수 제한 — ... -Dexec.mainClass=study.s11.SqlGuard 로 자체 시험 */
public final class SqlGuard {

    public static final int MAX_ROWS = 200;

    public static final class UnsafeSql extends Exception {
        public UnsafeSql(String message) {
            super(message);
        }
    }

    static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    static final Pattern FOR_UPDATE = Pattern.compile("\\bFOR\\s+UPDATE\\b", Pattern.CASE_INSENSITIVE);
    // 패키지 호출 금지: DBMS_*, UTL_*, OWA_*, APEX_*, SYS.*, HTP.*
    static final Pattern DENY = Pattern.compile("\\b(DBMS_|UTL_|OWA_|APEX_)\\w*|\\b(SYS|HTP)\\s*\\.", Pattern.CASE_INSENSITIVE);
    static final Pattern BIND = Pattern.compile("(?<![:\\w]):([A-Za-z_]\\w*)");

    /** 문자열 리터럴 안의 글자는 키워드 검사에서 빼기 위해 비웁니다. */
    static String withoutLiterals(String sql) {
        return STRING_LITERAL.matcher(sql).replaceAll("''");
    }

    /** SQL 에 실제로 있는 바인드 변수 이름 (:from_dt → from_dt) */
    public static Set<String> bindNames(String sql) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = BIND.matcher(withoutLiterals(sql));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 안전하면 행 수 제한을 붙인 SQL 을 돌려주고, 아니면 UnsafeSql 을 던집니다. */
    public static String check(String sql, Set<String> allowedTables) throws UnsafeSql {
        String s = sql.strip();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).strip();
        Statements stmts;
        try {
            stmts = CCJSqlParserUtil.parseStatements(s);
        } catch (JSQLParserException e) {
            String msg = String.valueOf(e.getMessage());
            throw new UnsafeSql("파싱 실패: " + msg.lines().findFirst().orElse(msg));
        }
        if (stmts.size() != 1) throw new UnsafeSql("문장은 1개만 허용합니다 (받은 문장 " + stmts.size() + "개)");
        Statement stmt = stmts.get(0);
        if (!(stmt instanceof Select select)) {                       // SELECT / WITH / UNION 만
            throw new UnsafeSql("조회문만 허용합니다: " + stmt.getClass().getSimpleName());
        }
        String code = withoutLiterals(select.toString());
        if (select.getForMode() != null || FOR_UPDATE.matcher(code).find()) {
            throw new UnsafeSql("FOR UPDATE 잠금은 허용하지 않습니다");
        }
        if (select instanceof PlainSelect ps && ps.getIntoTables() != null && !ps.getIntoTables().isEmpty()) {
            throw new UnsafeSql("SELECT INTO 는 허용하지 않습니다");
        }
        // WITH 절 이름은 TablesNamesFinder 가 알아서 뺍니다
        for (String t : new TablesNamesFinder<Void>().getTables((Statement) select)) {
            String full = t.replace("\"", "").toUpperCase(Locale.ROOT);
            if (full.contains("@")) throw new UnsafeSql("DB 링크는 허용하지 않습니다: " + full);
            if (full.equals("DUAL")) continue;
            if (!allowedTables.contains(full)) {
                throw new UnsafeSql("허용 목록에 없는 테이블: " + full + " (스키마명을 붙여 주세요)");
            }
        }
        Matcher deny = DENY.matcher(code);
        if (deny.find()) throw new UnsafeSql("허용하지 않는 함수 호출: " + deny.group());
        if (select.getLimit() != null) {
            throw new UnsafeSql("LIMIT 대신 FETCH FIRST n ROWS ONLY 를 씁니다 (Oracle)");
        }
        // 행 수 제한: 없거나 숫자가 아니거나 MAX_ROWS 보다 크면 FETCH FIRST MAX_ROWS ROWS ONLY 로 바꿉니다
        Fetch fetch = select.getFetch();
        if (fetch == null || !(fetch.getExpression() instanceof LongValue n) || n.getValue() > MAX_ROWS) {
            Fetch limit = new Fetch().withExpression(new LongValue(MAX_ROWS));
            limit.setFetchParamFirst(true);
            limit.addFetchParameter("ROWS");
            limit.addFetchParameter("ONLY");
            select.setFetch(limit);
        }
        return select.toString();
    }

    public static void main(String[] args) {
        Set<String> allowed = Set.of("DW.TRD_DAY_F", "DW.CMN_BIZ_DAY_D");
        List<String> tests = List.of(
                "SELECT base_dt, SUM(trd_amt) FROM dw.trd_day_f WHERE base_dt BETWEEN :from_dt AND :to_dt GROUP BY base_dt",
                "DELETE FROM dw.trd_day_f",
                "SELECT * FROM dw.trd_day_f; DROP TABLE dw.trd_day_f",
                "SELECT * FROM dw.trd_day_f FOR UPDATE",
                "SELECT * FROM cust.cust_master",
                "SELECT dbms_lock.sleep(60) FROM dual",
                "SELECT * FROM dw.trd_day_f@prod_link");
        for (String sql : tests) {
            try {
                System.out.println("통과 → " + check(sql, allowed));
            } catch (UnsafeSql e) {
                System.out.println("거부 → " + e.getMessage());
            }
        }
    }
}
