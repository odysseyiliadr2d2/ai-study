# prompts/failed-jobs.md — 작업 지시 템플릿 (Java)

## 배경
야간 배치 로그(`시각 수준 작업 단계 메시지` 한 줄 형식)를 요약하는 도구에, 실패한 작업 목록을 뽑는 기능이 필요합니다.
운영 담당자가 아침에 '어떤 작업을 재실행해야 하나'를 한 번에 보려는 용도입니다.

## 범위 (이것만 합니다)
- `LogSummary` 에 `public static List<String> failedJobs(List<String> lines)` 를 추가합니다.
  - ERROR 줄이 하나라도 있는 작업 ID 를 중복 없이, 이름순으로 돌려줍니다 (수정할 수 없는 목록).
  - 빈 줄은 건너뛰고, 형식이 틀린 줄은 `parseLine` 과 같이 IllegalArgumentException 을 냅니다.
- `LogSummaryTest` 에 테스트 3개: ERROR 가 있는 작업만 · 같은 작업 여러 줄 · ERROR 가 없으면 빈 목록.

## 하지 말 것
- 기존 메서드 · 테스트 수정, pom.xml 변경, 다른 파일 정리(리팩터링).

## 완료 기준
- `./mvnw -q verify` 통과 (기존 3개 + 새 3개 = Tests run: 6)
- 변경 요약과 남은 위험 3줄 이내
