# AGENTS.md — ticket-desk-api (Spring Boot + Oracle)

모든 코딩 에이전트(Codex CLI, Antigravity CLI, Claude Code)가 공통으로 읽는 규칙입니다.
200줄 이내로 유지하고, 사람이 읽어도 바로 이해되게 씁니다.

## 프로젝트
- 업무 요청 접수 + AI 요약·분류 서비스의 백엔드. Java 21, Spring Boot 4.x, Maven.
- 패키지: `com.example.ticketdesk` (api / service / domain / ai / config)
- DB: Oracle. 서비스 전용 스키마 `TICKETDESK` 만 씁니다. DW 테이블은 읽기 전용 뷰로만 조회합니다.

## 명령
- 빌드·테스트: `./mvnw -q verify`
- 단위 테스트만: `./mvnw -q test`
- 로컬 실행: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`

## 코딩 규칙
- DTO 는 Java `record`, 입력 검증은 Bean Validation(`@NotBlank`, `@Size`).
- 컨트롤러는 얇게. 비즈니스 로직은 `service`, LLM 호출은 `ai` 패키지에만 둡니다.
- SQL 은 JPA 우선. 네이티브 쿼리는 Oracle 문법(`FETCH FIRST n ROWS ONLY`)으로 쓰고 바인드 변수만 사용합니다.
- 새 기능에는 테스트를 함께 씁니다. 테스트 없이 커밋하지 않습니다.

## 하지 말 것
- `application-*.yml` 에 비밀번호·API 키를 쓰지 않습니다. `${ENV_VAR}` 로만 참조합니다.
- `src/main/resources/db/migration` 의 기존 마이그레이션 파일은 수정하지 않습니다. 새 파일을 추가합니다.
- 운영 DB 접속 정보는 이 저장소에 없습니다. 찾거나 만들지 않습니다.
- 의존성 추가·버전 변경은 이유를 적어 먼저 물어봅니다.

## 완료 기준
- `./mvnw -q verify` 통과, 변경 요약과 남은 위험을 3줄 이내로 보고.
