package study.s22;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 학습 데이터(JSONL chat 형식) 검사와 분할 — 학습 앞단을 Java 로 맡습니다.
 *
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s22.DatasetTool -Dexec.args="validate train.jsonl"
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s22.DatasetTool -Dexec.args="split all.jsonl 0.1 42"
 *
 * validate: Python 트랙 validate_jsonl.py 와 같은 항목 (형식 · 역할 순서 · 빈 내용 · 길이 · 중복 · 개인정보 의심 패턴)
 * split   : 같은 메모(user 내용)가 train 과 eval 양쪽에 들어가지 않게 나눕니다 (평가 점수 부풀림 방지)
 * 개인정보 패턴은 예시용이며 사내 기준으로 보완해야 합니다.
 */
public final class DatasetTool {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> ROLES = Set.of("system", "user", "assistant");
    static final int MAX_CHARS = 6000;   // 대략적인 길이 상한 (토큰 수는 토크나이저로 다시 확인)
    static final Map<String, Pattern> PII = new LinkedHashMap<>();

    static {
        PII.put("주민등록번호 의심", Pattern.compile("\\d{6}-?[1-4]\\d{6}"));
        PII.put("전화번호 의심", Pattern.compile("01[016789]-?\\d{3,4}-?\\d{4}"));
        PII.put("이메일", Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"));
        PII.put("긴 숫자열(계좌 등) 의심", Pattern.compile("\\d{2,6}-\\d{2,6}-\\d{2,8}"));
    }

    static void checkRecord(int n, JsonNode rec, List<String> errors, List<String> warns) {
        JsonNode msgs = rec.path("messages");
        if (!msgs.isArray() || msgs.size() < 2) {
            errors.add(n + "행: messages 가 없거나 2개 미만");
            return;
        }
        List<String> roles = new ArrayList<>();
        msgs.forEach(m -> roles.add(m.path("role").asText(null)));
        if (roles.stream().anyMatch(r -> !ROLES.contains(r))) errors.add(n + "행: 알 수 없는 role " + roles);
        if (!"assistant".equals(roles.get(roles.size() - 1))) errors.add(n + "행: 마지막 메시지는 assistant(정답)여야 합니다");
        int total = 0;
        for (JsonNode m : msgs) {
            JsonNode c = m.path("content");
            if (!c.isTextual() || c.asText().isBlank()) {
                errors.add(n + "행: 빈 content (" + m.path("role").asText() + ")");
                continue;
            }
            total += c.asText().length();
            for (var e : PII.entrySet()) {
                if (e.getValue().matcher(c.asText()).find()) errors.add(n + "행: " + e.getKey() + " — 마스킹 후 다시 넣으세요");
            }
        }
        if (total > MAX_CHARS) warns.add(n + "행: 길이 " + total + "자 — max_length 에서 잘릴 수 있음");
    }

    static int validate(Path path) throws IOException {
        List<String> errors = new ArrayList<>(), warns = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>(), answers = new HashMap<>();
        int count = 0, n = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            n++;
            if (line.isBlank()) continue;
            count++;
            JsonNode rec;
            try {
                rec = JSON.readTree(line);
            } catch (JsonProcessingException e) {
                errors.add(n + "행: JSON 오류 " + e.getOriginalMessage());
                continue;
            }
            checkRecord(n, rec, errors, warns);
            seen.merge(sha256(line), 1, Integer::sum);
            JsonNode msgs = rec.path("messages");
            if (msgs.isArray() && msgs.size() > 0) {
                String ans = msgs.get(msgs.size() - 1).path("content").asText("");
                answers.merge(ans.substring(0, Math.min(40, ans.length())), 1, Integer::sum);
            }
        }
        int dup = seen.values().stream().mapToInt(c -> c - 1).sum();
        if (dup > 0) warns.add("완전히 같은 행 " + dup + "개 — 과적합 원인이 됩니다");
        int top = answers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (count >= 20 && top / (double) count > 0.3) warns.add("정답의 30% 이상이 같은 문장으로 시작합니다 — 다양성을 확인하세요");

        System.out.printf("%s %d건 검사 · 오류 %d · 경고 %d%n", errors.isEmpty() ? "OK" : "FAIL", count, errors.size(), warns.size());
        errors.stream().limit(50).forEach(e -> System.out.println("  FAIL " + e));
        warns.stream().limit(50).forEach(w -> System.out.println("  WARN " + w));
        return errors.isEmpty() ? 0 : 1;
    }

    /** user 메시지(원문 메모)를 정규화한 키로 묶어, 같은 메모의 변형이 양쪽에 갈리지 않게 나눕니다. */
    static void split(Path all, double evalRatio, long seed) throws IOException {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String line : Files.readAllLines(all, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode msgs = JSON.readTree(line).path("messages");
            String memo = msgs.size() >= 2 ? msgs.get(msgs.size() - 2).path("content").asText() : line;
            groups.computeIfAbsent(memo.replaceAll("\\s+", "").toLowerCase(), k -> new ArrayList<>()).add(line);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.shuffle(keys, new Random(seed));                  // seed 고정 → 다시 나눠도 같은 결과
        int evalGroups = Math.max(1, (int) Math.round(keys.size() * evalRatio));
        List<String> train = new ArrayList<>(), eval = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            List<String> rows = new ArrayList<>(new LinkedHashSet<>(groups.get(keys.get(i))));   // 완전 중복 제거
            (i < evalGroups ? eval : train).addAll(rows);
        }
        Path dir = all.toAbsolutePath().getParent();
        Files.write(dir.resolve("train.jsonl"), train, StandardCharsets.UTF_8);
        Files.write(dir.resolve("eval.jsonl"), eval, StandardCharsets.UTF_8);
        System.out.printf("메모 %d종 → train %d건 · eval %d건 (seed %d, 같은 메모는 한쪽에만)%n", keys.size(), train.size(), eval.size(), seed);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        String cmd = args.length > 0 ? args[0] : "validate";
        if (cmd.equals("split")) {
            split(Path.of(args.length > 1 ? args[1] : "all.jsonl"),
                    args.length > 2 ? Double.parseDouble(args[2]) : 0.1,
                    args.length > 3 ? Long.parseLong(args[3]) : 42L);
        } else {
            System.exit(validate(Path.of(args.length > 1 ? args[1] : "train.jsonl")));
        }
    }
}
