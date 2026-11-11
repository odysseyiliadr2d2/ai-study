package study.s23;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 거버넌스 유틸 테스트 — ./mvnw test */
class GovernanceTest {

    // Python 트랙 test_pii_mask.py 와 같은 사례. 팀이 실제로 쓰는 번호 형식(사번·계좌 체계)을 여기에 더합니다.
    static final List<String[]> CASES = List.of(
            new String[] {"연락처 010-1234-5678", "[PHONE_1]"},
            new String[] {"번호 01012345678 입니다", "[PHONE_1]"},
            new String[] {"주민번호 900101-1234567", "[RRN_1]"},
            new String[] {"계좌 123-456-789012", "[ACCOUNT_1]"},
            new String[] {"메일 a.b@example.co.kr", "[EMAIL_1]"},
            new String[] {"사무실 02-123-4567", "[TEL_1]"});

    @Test
    void 개인정보_패턴을_토큰으로_바꾸고_복원한다() {
        for (String[] c : CASES) {
            PiiMasker.Masked m = PiiMasker.mask(c[0]);
            assertTrue(m.text().contains(c[1]), c[0] + " → " + m.text());
            assertEquals(c[0], PiiMasker.unmask(m.text(), m.table()));
        }
    }

    @Test
    void 날짜_금액_작업번호는_마스킹하지_않는다() {
        PiiMasker.Masked m = PiiMasker.mask("2026-10-29 배치 결과 1,250,000원 처리, 작업번호 JOB-0042");
        assertTrue(m.table().isEmpty(), m.table().toString());
    }

    @Test
    void 감사_로그에는_원문_개인정보가_남지_않는다() throws Exception {
        PiiMasker.Masked m = PiiMasker.mask("회신 010-1234-5678, 계좌 123-456-789012");
        String json = AuditLog.toJson(AuditLog.entry("UC-2026-014", "E12345", "M-03", m, "답변", "allowed", 10));
        assertFalse(json.contains("010-1234-5678"));
        assertFalse(json.contains("123-456-789012"));
        assertFalse(json.contains("E12345"));
        assertTrue(json.contains("\"PHONE\":1"));
    }

    @Test
    void 고객_권리에_영향을_주면_점수와_관계없이_높음() {
        Map<String, Object> uc = sample();
        ((Map<String, Object>) uc.get("impact")).put("affects_rights_or_obligations", true);
        RiskAssess.Result r = RiskAssess.assess(uc);
        assertEquals("높음", r.level());
        assertTrue(RiskAssess.markdown(uc, r, LocalDate.of(2026, 11, 12)).contains("제37조의2"));
    }

    @Test
    void 등록부_검사는_허용_값_밖과_자동_결정을_잡는다() {
        Map<String, Object> policy = Map.of(
                "required", List.of("id", "name", "controls.human_review"),
                "id_pattern", "^UC-\\d{4}-\\d{3}$",
                "allowed", Map.of("model.deployment", List.of("on_prem", "external_api", "saas")),
                "rules", Map.of("rights_impact_forbids_automated", true));
        Map<String, Object> uc = sample();
        assertTrue(RegisterCheck.check(policy, uc).isEmpty(), RegisterCheck.check(policy, uc).toString());

        ((Map<String, Object>) uc.get("model")).put("deployment", "cloud");
        ((Map<String, Object>) uc.get("impact")).put("affects_rights_or_obligations", true);
        ((Map<String, Object>) uc.get("impact")).put("decision_type", "automated");
        uc.put("id", "UC-14");
        List<String> errors = RegisterCheck.check(policy, uc);
        assertEquals(3, errors.size(), errors.toString());   // ID 형식 · 허용 값 · 자동 결정 금지
    }

    /** usecase.yaml 과 같은 내용 (수정 가능한 맵으로) */
    static Map<String, Object> sample() {
        Map<String, Object> uc = new HashMap<>(Map.of("id", "UC-2026-014", "name", "운영 매뉴얼 Q&A 봇", "owner", "정보계 운영팀",
                "purpose", "야간 배치 장애 대응 절차 안내", "users", "내부 직원"));
        uc.put("model", new HashMap<>(Map.of("name", "M-03", "deployment", "on_prem", "license_checked", true)));
        uc.put("data", new HashMap<>(Map.of("classification", "internal", "contains_personal_info", false,
                "pseudonymized", false, "leaves_company_network", false)));
        uc.put("impact", new HashMap<>(Map.of("customer_facing", false, "affects_rights_or_obligations", false, "decision_type", "draft")));
        uc.put("controls", new HashMap<>(Map.of("human_review", true, "prompt_logging", true, "log_retention_days", 365,
                "pii_masking", true, "output_filter", false, "prompt_injection_test", false, "rollback_plan", true)));
        return uc;
    }
}
