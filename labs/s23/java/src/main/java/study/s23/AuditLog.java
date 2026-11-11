package study.s23;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * AI 호출 감사 로그 형식 — 한 호출이 JSON 한 줄 (logs/ai-audit-YYYYMMDD.jsonl).
 *
 * 남기는 것: 언제 · 누가(사번 해시) · 어느 유스케이스(등록부 ID) · 어떤 모델(승인 목록 번호·버전) · 무엇을(마스킹된 프롬프트의 해시와 길이)
 *            · 마스킹 건수(종류별) · 응답 길이 · 처리 결과(allowed/blocked/human_review) · 지연
 * 남기지 않는 것: 원문 프롬프트·응답, 복원표 (원문 보관이 필요하면 별도 암호화 저장소 + 접근 통제, 사내 정책에 따름)
 * 보존 기간은 등록부의 controls.log_retention_days(예: 365일)를 따릅니다.
 */
public final class AuditLog {

    public record Entry(
            String ts, String requestId, String useCaseId, String userHash, String model,
            String promptSha256, int promptChars, Map<String, Integer> masked,
            int responseChars, String decision, long latencyMs) {}

    static final ObjectMapper JSON = new ObjectMapper();   // Jackson 2.12+ 는 record 를 그대로 직렬화

    /** 사번은 그대로 쓰지 않고 사내 비밀 salt 를 붙여 해시합니다 (같은 사람 추적은 되고, 로그만으로는 누군지 모름). */
    public static String userHash(String employeeId, String salt) {
        return sha256(salt + ":" + employeeId).substring(0, 16);
    }

    public static Entry entry(String useCaseId, String employeeId, String model, PiiMasker.Masked prompt,
                              String response, String decision, long latencyMs) {
        return new Entry(
                OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                UUID.randomUUID().toString(),
                useCaseId,
                userHash(employeeId, System.getenv().getOrDefault("AUDIT_SALT", "change-me")),
                model,
                sha256(prompt.text()),                 // 마스킹 후 텍스트의 해시 (원문 아님)
                prompt.text().length(),
                prompt.counts(),
                response == null ? 0 : response.length(),
                decision,
                latencyMs);
    }

    public static String toJson(Entry e) throws IOException {
        return JSON.writeValueAsString(e);
    }

    public static Path append(Path dir, Entry e) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("ai-audit-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".jsonl");
        Files.writeString(file, toJson(e) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return file;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 데모: 마스킹 → (모델 호출 자리) → 감사 로그 한 줄. */
    public static void main(String[] args) throws IOException {
        String prompt = "고객 문의 요약: 010-1234-5678 로 회신 요청, 계좌 123-456-789012 이체 오류 문의";
        PiiMasker.Masked masked = PiiMasker.mask(prompt);
        // 여기서 masked.text() 만 사내 LLM 으로 보냅니다 (세션 21 클라이언트). 데모라 응답은 고정 문자열.
        String response = "[PHONE_1] 고객의 이체 오류 문의입니다. 계좌 [ACCOUNT_1] 거래 내역 확인이 필요합니다.";
        Entry e = entry("UC-2026-014", "E12345", "M-03 qwen3.5-9b", masked, response, "human_review", 840);
        Path file = append(Path.of("logs"), e);
        System.out.println("보낸 프롬프트: " + masked.text());
        System.out.println("감사 로그 → " + file + "\n" + toJson(e));
    }
}
