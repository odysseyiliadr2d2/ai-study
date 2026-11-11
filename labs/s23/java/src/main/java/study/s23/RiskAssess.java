package study.s23;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 유스케이스 위험 채점 + 심의 체크리스트 — Python 트랙 risk_assess.py 와 같은 기준, 같은 usecase.yaml.
 *   ./mvnw -q compile exec:java -Dexec.args=usecase.yaml > review.md
 * 채점 기준은 학습용 예시입니다. 사내 위험관리 기준에 맞게 가중치를 바꾸세요.
 */
public final class RiskAssess {

    static final String REG_CHECKED = "2026-10-29";   // 근거 목록을 마지막으로 확인한 날짜
    static final Map<String, Integer> DATA_SCORE = Map.of("public", 0, "internal", 1, "confidential", 2, "personal", 3, "credit", 4);
    static final Map<String, Integer> DEPLOY_SCORE = Map.of("on_prem", 0, "saas", 2, "external_api", 2);
    static final Map<String, Integer> DECISION_SCORE = Map.of("draft", 0, "human_approved", 1, "automated", 3);

    public record Item(String text, boolean ok) {}

    public record Result(int score, String level, List<String> reasons, List<Item> checklist) {}

    public static Result assess(Map<String, Object> uc) {
        Map<String, Object> d = map(uc, "data"), m = map(uc, "model"), i = map(uc, "impact"), c = map(uc, "controls");
        List<String> reasons = new ArrayList<>();
        String cls = str(d, "classification");
        int s = DATA_SCORE.get(cls);
        reasons.add("데이터 등급 " + cls + " (+" + s + ")");
        if (bool(d, "contains_personal_info") && !bool(d, "pseudonymized")) {
            s += 2;
            reasons.add("가명처리 안 된 개인정보 포함 (+2)");
        }
        String dep = str(m, "deployment");
        int ds = DEPLOY_SCORE.get(dep);
        if (ds > 0) {
            s += ds;
            reasons.add("배포 형태 " + dep + " (+" + ds + ")");
        }
        if (bool(d, "leaves_company_network")) {
            s += 2;
            reasons.add("데이터가 사내망 밖으로 나감 (+2)");
        }
        if (bool(i, "customer_facing")) {
            s += 2;
            reasons.add("고객 대상 (+2)");
        }
        if (bool(i, "affects_rights_or_obligations")) {
            s += 4;
            reasons.add("고객 권리·의무에 영향 (+4)");
        }
        String dec = str(i, "decision_type");
        s += DECISION_SCORE.get(dec);
        reasons.add("결정 방식 " + dec + " (+" + DECISION_SCORE.get(dec) + ")");
        if (!bool(c, "human_review")) {
            s += 2;
            reasons.add("사람 검토 없음 (+2)");
        }
        // 고객 권리·의무에 영향을 주는 AI 는 점수와 관계없이 '높음'으로 다룹니다
        String level = bool(i, "affects_rights_or_obligations") || s >= 9 ? "높음" : s >= 4 ? "중간" : "낮음";
        return new Result(s, level, reasons, checklist(uc, level));
    }

    static List<Item> checklist(Map<String, Object> uc, String level) {
        Map<String, Object> d = map(uc, "data"), m = map(uc, "model"), i = map(uc, "impact"), c = map(uc, "controls");
        String cls = str(d, "classification");
        List<Item> items = new ArrayList<>(List.of(
                new Item("승인 모델 목록에 있는 모델인가", bool(m, "license_checked")),
                new Item("프롬프트·응답 로그를 남기는가", bool(c, "prompt_logging")),
                new Item("로그 보존 기간이 사내 기준 이상인가 (예: 365일)", num(c, "log_retention_days") >= 365),
                new Item("사람이 최종 검토·승인하는가", bool(c, "human_review")),
                new Item("프롬프트 앞단에서 개인정보를 마스킹하는가", bool(c, "pii_masking") || cls.equals("public") || cls.equals("internal")),
                new Item("프롬프트 인젝션 시험을 했는가", bool(c, "prompt_injection_test")),
                new Item("장애·오답 시 되돌리는 절차가 있는가", bool(c, "rollback_plan"))));
        if (!str(m, "deployment").equals("on_prem") || bool(d, "leaves_company_network")) {
            items.add(new Item("망분리 예외 근거(규제 특례·규정)와 보안대책을 확인했는가", false));
        }
        if (bool(i, "affects_rights_or_obligations")) {
            items.add(new Item("AI 기본법 고영향 AI 해당 여부를 검토했는가", false));
            items.add(new Item("자동화된 결정 거부·설명 요구 대응 절차가 있는가 (개인정보보호법 제37조의2)", false));
        }
        if (bool(i, "customer_facing")) {
            items.add(new Item("고객에게 생성형 AI 사용 사실을 미리 알리는가 (AI 기본법 투명성)", false));
        }
        if (level.equals("높음")) {
            items.add(new Item("AI 위원회(의사결정기구) 심의를 받았는가", false));
        }
        return items;
    }

    public static String markdown(Map<String, Object> uc, Result r, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI 도입 심의 자료 — ").append(uc.get("name")).append(" (").append(uc.get("id")).append(")\n\n");
        sb.append("- 작성일: ").append(today).append(" · 담당: ").append(uc.get("owner")).append('\n');
        sb.append("- 목적: ").append(uc.get("purpose")).append('\n');
        sb.append("- 위험 점수: **").append(r.score()).append("점 → ").append(r.level()).append("**\n\n");
        sb.append("## 점수 근거\n");
        r.reasons().forEach(x -> sb.append("- ").append(x).append('\n'));
        sb.append("\n## 심의 체크리스트\n");
        r.checklist().forEach(x -> sb.append("- [").append(x.ok() ? "x" : " ").append("] ").append(x.text()).append('\n'));
        sb.append("\n## 규제 확인 일자\n- 근거 규정은 ").append(REG_CHECKED).append(" 기준으로 확인했습니다. 심의 전에 금융위원회·금융감독원·")
          .append("개인정보보호위원회·국가법령정보센터에서 최신 내용을 다시 확인하세요.\n");
        return sb.toString();
    }

    // ── YAML 도우미 (다른 클래스도 씀)
    public static Map<String, Object> load(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(r);   // 임의 클래스 생성을 막는 안전 로더
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> m, String k) {
        return m.get(k) instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    }

    static String str(Map<String, Object> m, String k) {
        return String.valueOf(m.getOrDefault(k, ""));
    }

    static boolean bool(Map<String, Object> m, String k) {
        return Boolean.TRUE.equals(m.get(k));
    }

    static double num(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : 0;
    }

    public static void main(String[] args) throws IOException {
        Map<String, Object> uc = load(Path.of(args.length > 0 ? args[0] : "usecase.yaml"));
        System.out.print(markdown(uc, assess(uc), LocalDate.now()));
    }
}
