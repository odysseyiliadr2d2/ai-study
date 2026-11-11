package study.s20;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 가드레일 — 입력 검사 · 도구 허용 목록 · 도구 결과 경계 · 출력 검사.
 * 모델이 무엇을 말하든 코드에서 마지막으로 막는 선입니다. 패턴은 학습용 예시이므로 사내 기준으로 보완합니다.
 */
public final class Guardrails {

    public static final Set<String> READ_TOOLS = Set.of("get_batch_log", "get_row_count", "search_table_spec");
    public static final Set<String> WRITE_TOOLS = Set.of("request_rerun");
    public static final int MAX_INPUT_CHARS = 2000;

    /** 결과: ok 가 false 면 hits 에 걸린 규칙이 들어 있습니다. */
    public record Verdict(boolean ok, List<String> hits) {}

    // 입력 검사: 접속정보·비밀번호를 달라는 요청은 모델에 보내기 전에 막습니다
    private static final List<Pattern> FORBIDDEN_INPUT = compile(
            "(비밀번호|패스워드|접속\\s*정보|password)\\s*(를|을)?\\s*(알려|보여|줘|입력)");

    // 출력 검사: 최종 답에 들어가면 안 되는 패턴 (Python 트랙 FORBIDDEN_OUTPUT 과 같음)
    private static final List<Pattern> FORBIDDEN_OUTPUT = compile(
            "\\bDROP\\s+TABLE\\b", "\\bTRUNCATE\\b", "\\bDELETE\\s+FROM\\b",
            "비밀번호를?\\s*(알려|입력|주)", "password\\s*[:=]");

    // 도구 결과 속 지시문 의심 표현 (차단하지 않고 로그에 남김)
    private static final Pattern INJECTION_HINTS = Pattern.compile(
            "(이전\\s*지시|ignore\\s+(all\\s+)?previous|SYSTEM\\s*NOTICE|비밀번호|DROP\\s+TABLE)", Pattern.CASE_INSENSITIVE);

    private Guardrails() {}

    public static Verdict checkInput(String question) {
        List<String> hits = new ArrayList<>();
        if (question == null || question.isBlank()) hits.add("빈 질문");
        else if (question.length() > MAX_INPUT_CHARS) hits.add("길이 " + question.length() + "자 > " + MAX_INPUT_CHARS);
        else hits.addAll(matches(FORBIDDEN_INPUT, question));
        return new Verdict(hits.isEmpty(), hits);
    }

    public static Verdict checkOutput(String answer) {
        List<String> hits = matches(FORBIDDEN_OUTPUT, answer == null ? "" : answer);
        return new Verdict(hits.isEmpty(), hits);
    }

    /** 허용 목록 밖의 도구는 서버가 노출해도 실행하지 않습니다. */
    public static boolean toolAllowed(String name) {
        return READ_TOOLS.contains(name) || WRITE_TOOLS.contains(name);
    }

    public static boolean isWrite(String name) {
        return WRITE_TOOLS.contains(name);
    }

    public static boolean suspicious(String toolOutput) {
        return INJECTION_HINTS.matcher(toolOutput).find();
    }

    /** 도구 결과를 데이터 경계로 감쌉니다. 지시문이 섞여 있어도 '데이터'로 읽히게. */
    public static String wrapOutput(String tool, String text) {
        return "<tool_output tool=\"" + tool + "\">\n" + text + "\n</tool_output>";
    }

    private static List<Pattern> compile(String... regex) {
        List<Pattern> out = new ArrayList<>();
        for (String r : regex) out.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
        return out;
    }

    private static List<String> matches(List<Pattern> patterns, String text) {
        List<String> hits = new ArrayList<>();
        for (Pattern p : patterns) if (p.matcher(text).find()) hits.add(p.pattern());
        return hits;
    }
}
