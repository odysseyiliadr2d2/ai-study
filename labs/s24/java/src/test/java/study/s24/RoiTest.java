package study.s24;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 계산식 회귀 테스트 — Python 트랙 roi.py 와 같은 값이 나오는지 고정합니다. ./mvnw test */
class RoiTest {

    static final Roi.Assumptions A = new Roi.Assumptions(60000, 240, 0.7, 0.2);   // usecases.yaml 예시 가정
    static final List<Roi.UseCase> UCS = List.of(
            new Roi.UseCase("장애 보고서 초안 자동 작성", 25, 20, 30_000_000, 12_000_000, 4, 2, "추정(예시)"),
            new Roi.UseCase("ETL 설계서·테이블 정의서 Q&A", 40, 15, 45_000_000, 15_000_000, 4, 3, "추정(예시)"),
            new Roi.UseCase("SQL 코드 리뷰 보조", 15, 25, 25_000_000, 8_000_000, 3, 2, "추정(예시)"),
            new Roi.UseCase("고객 문의 답변 초안", 60, 30, 150_000_000, 40_000_000, 5, 5, "추정(예시)"));

    @Test
    void 장애_보고서_초안의_절감_시간과_회수_기간() {
        Roi.Row r = Roi.evaluate(UCS.get(0), A);
        assertEquals(1120.0, r.hours(), 1e-6);                 // 25 × 20 ÷ 60 × 240 × 0.7 × 0.8
        assertEquals(55_200_000.0, r.net(), 1e-3);             // 1,120h × 60,000원 − 12,000,000원
        assertEquals(30_000_000 / (55_200_000.0 / 12), r.payback(), 1e-9);   // 약 6.5개월
        assertEquals(4.52, r.roi3(), 1e-9);                   // 452%
        assertEquals("먼저 할 것", r.quadrant());
    }

    @Test
    void 순위는_회수_기간이_짧은_순() {
        List<String> order = Roi.rank(UCS, A).stream().map(r -> r.uc().name()).toList();
        assertEquals(List.of("장애 보고서 초안 자동 작성", "SQL 코드 리뷰 보조", "ETL 설계서·테이블 정의서 Q&A", "고객 문의 답변 초안"), order);
    }

    @Test
    void 순효과가_0_이하면_회수_불가() {
        Roi.UseCase loss = new Roi.UseCase("적자", 1, 1, 10_000_000, 50_000_000, 2, 4, "가정");
        assertTrue(Double.isInfinite(Roi.evaluate(loss, A).payback()));
        assertEquals("회수 불가", Roi.months(Roi.evaluate(loss, A).payback()));
    }

    @Test
    void 보고서에_한_줄_요약과_민감도와_위험_등급이_들어간다() {
        String review = "- 위험 점수: **1점 → 낮음**\n- 근거 규정은 2026-10-29 기준으로 확인했습니다.";
        String md = BusinessCaseReport.build(new Roi.Config(A, UCS), review, LocalDate.of(2026, 11, 12));
        assertTrue(md.contains("연간 약 1,120시간 절감"), md);
        assertTrue(md.contains("| 50% |"), md);
        assertTrue(md.contains("위험 점수 1점 → 낮음"), md);
        assertTrue(md.contains("규제 확인 일자: 2026-10-29"), md);
    }
}
