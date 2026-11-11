package study.s06;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 업무 규칙 검사. API 스키마는 길이 · 범위 제약(maxLength, minimum)을 강제하지 않으므로 여기서 봅니다.
 * pydantic 의 field_validator 자리입니다. 빈 목록이면 통과.
 */
public final class InquiryRules {
    static final Set<String> CATEGORIES = Set.of("계정·로그인", "요금·계약", "장애·오류", "기능 요청", "데이터·보고서", "기타");
    static final Set<String> URGENCIES = Set.of("높음", "보통", "낮음");

    private InquiryRules() {}

    public static List<String> problems(Inquiry q) {
        List<String> p = new ArrayList<>();
        if (!CATEGORIES.contains(q.category())) p.add("category 가 허용 목록에 없습니다: " + q.category());
        if (!URGENCIES.contains(q.urgency())) p.add("urgency 가 허용 목록에 없습니다: " + q.urgency());
        if (q.summary() == null || q.summary().length() > 120) p.add("summary 가 없거나 너무 깁니다 (120자 이하)");
        if (q.confidence() < 0.0 || q.confidence() > 1.0) p.add("confidence 는 0~1 사이");
        q.requestedByDate().ifPresent(d -> {
            try {
                LocalDate.parse(d);   // YYYY-MM-DD
            } catch (DateTimeParseException e) {
                p.add("requested_by_date 형식은 YYYY-MM-DD: " + d);
            }
        });
        return p;
    }
}
