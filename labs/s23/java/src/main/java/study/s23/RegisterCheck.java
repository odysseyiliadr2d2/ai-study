package study.s23;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * AI 유스케이스 등록부 검사 — 신청서 YAML 이 정책(policy.yaml)을 지키는지 봅니다.
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s23.RegisterCheck -Dexec.args="policy.yaml usecases" > register.md
 *
 * ① 필수 항목 ② 허용 값 ③ ID 형식·중복 ④ 정책 규칙(마스킹·사람 검토·자동 결정 금지·망분리 예외 근거)
 * 위반이 있으면 종료 코드 1 → 등록부 저장소의 CI 에서 병합을 막을 수 있습니다.
 */
public final class RegisterCheck {

    public record Finding(String file, String id, List<String> errors) {}

    @SuppressWarnings("unchecked")
    public static List<String> check(Map<String, Object> policy, Map<String, Object> uc) {
        List<String> errors = new ArrayList<>();
        for (Object f : (List<Object>) policy.getOrDefault("required", List.of())) {
            Object v = at(uc, String.valueOf(f));
            if (v == null || String.valueOf(v).isBlank()) errors.add("필수 항목 없음: " + f);
        }
        Object idPattern = policy.get("id_pattern");
        if (idPattern != null && !Pattern.matches(String.valueOf(idPattern), String.valueOf(uc.get("id")))) {
            errors.add("ID 형식 오류: " + uc.get("id") + " (형식 " + idPattern + ")");
        }
        Map<String, Object> allowed = (Map<String, Object>) policy.getOrDefault("allowed", Map.of());
        allowed.forEach((path, values) -> {
            Object v = at(uc, path);
            if (v != null && !((List<Object>) values).contains(v)) errors.add(path + " 값 '" + v + "' 은 허용 목록 " + values + " 에 없음");
        });
        Object minDays = policy.get("min_log_retention_days");
        if (minDays instanceof Number n && at(uc, "controls.log_retention_days") instanceof Number d && d.intValue() < n.intValue()) {
            errors.add("로그 보존 " + d + "일 < 정책 " + n + "일");
        }
        Map<String, Object> rules = (Map<String, Object>) policy.getOrDefault("rules", Map.of());
        if (on(rules, "personal_info_requires_masking") && yes(uc, "data.contains_personal_info") && !yes(uc, "controls.pii_masking")) {
            errors.add("개인정보 포함인데 controls.pii_masking 이 true 가 아님");
        }
        if (on(rules, "customer_facing_requires_human_review") && yes(uc, "impact.customer_facing") && !yes(uc, "controls.human_review")) {
            errors.add("고객 대상인데 사람 검토(controls.human_review)가 없음");
        }
        if (on(rules, "rights_impact_forbids_automated") && yes(uc, "impact.affects_rights_or_obligations")
                && "automated".equals(at(uc, "impact.decision_type"))) {
            errors.add("고객 권리·의무에 영향을 주는 결정을 자동(automated)으로 할 수 없음");
        }
        boolean external = !"on_prem".equals(at(uc, "model.deployment")) || yes(uc, "data.leaves_company_network");
        if (on(rules, "external_requires_exception_doc") && external && at(uc, "exception_doc") == null) {
            errors.add("외부 API·SaaS·망 밖 전송인데 exception_doc(망분리 예외 근거) 없음");
        }
        return errors;
    }

    /** "controls.human_review" 같은 점 경로로 값을 꺼냅니다. */
    @SuppressWarnings("unchecked")
    static Object at(Map<String, Object> m, String path) {
        Object cur = m;
        for (String key : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> x)) return null;
            cur = ((Map<String, Object>) x).get(key);
        }
        return cur;
    }

    static boolean yes(Map<String, Object> uc, String path) {
        return Boolean.TRUE.equals(at(uc, path));
    }

    static boolean on(Map<String, Object> rules, String key) {
        return Boolean.TRUE.equals(rules.get(key));
    }

    public static void main(String[] args) throws IOException {
        Map<String, Object> policy = RiskAssess.load(Path.of(args.length > 0 ? args[0] : "policy.yaml"));
        List<Path> files = new ArrayList<>();
        for (int i = 1; i < Math.max(2, args.length); i++) {
            Path p = Path.of(args.length > i ? args[i] : "usecases");
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.list(p)) {
                    s.filter(f -> f.toString().endsWith(".yaml")).sorted().forEach(files::add);
                }
            } else {
                files.add(p);
            }
        }
        Map<String, String> seen = new HashMap<>();
        List<Finding> findings = new ArrayList<>();
        System.out.println("# AI 유스케이스 등록부 검사\n");
        System.out.println("| ID | 이름 | 담당 | 배포 | 데이터 | 위험 등급 | 정책 위반 |");
        System.out.println("|---|---|---|---|---|---|---:|");
        for (Path f : files) {
            Map<String, Object> uc = RiskAssess.load(f);
            List<String> errors = new ArrayList<>(check(policy, uc));
            String id = String.valueOf(uc.get("id"));
            String prev = seen.putIfAbsent(id, f.getFileName().toString());
            if (prev != null) errors.add("ID 중복: " + prev + " 와 같은 " + id);
            String level;
            try {
                level = RiskAssess.assess(uc).level();
            } catch (RuntimeException e) {                         // 허용 값 밖이면 채점 불가
                level = "채점 불가";
            }
            System.out.printf("| %s | %s | %s | %s | %s | %s | %d |%n", id, uc.get("name"), uc.get("owner"),
                    at(uc, "model.deployment"), at(uc, "data.classification"), level, errors.size());
            findings.add(new Finding(f.getFileName().toString(), id, errors));
        }
        System.out.println("\n## 위반 내역");
        findings.stream().filter(x -> !x.errors().isEmpty()).forEach(x -> {
            System.out.println("- " + x.file() + " (" + x.id() + ")");
            x.errors().forEach(e -> System.out.println("  - " + e));
        });
        long bad = findings.stream().filter(x -> !x.errors().isEmpty()).count();
        System.out.printf("%n검사 %d건 · 위반 있는 신청서 %d건%n", findings.size(), bad);
        if (bad > 0) System.exit(1);
    }
}
