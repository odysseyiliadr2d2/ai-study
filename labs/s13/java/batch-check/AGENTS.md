# AGENTS.md — batch-check (Java)

모든 코딩 에이전트(Claude Code, Codex CLI, Antigravity CLI)가 공통으로 읽는 규칙입니다. 100줄 이내로 유지합니다.

## 프로젝트
- 야간 배치 로그 요약 도구. Java 21, Maven Wrapper, 테스트는 JUnit 5 (Jupiter). Spring 은 쓰지 않습니다.
- 패키지 `study.s13`. 코드는 `src/main/java`, 테스트는 `src/test/java`.

## 명령
- 테스트: `./mvnw -q test`
- 빌드 + 테스트 전체: `./mvnw -q verify`
- 실행: `./mvnw -q compile exec:java -Dexec.args=batch.log`
- 사내망에서는 `~/.m2/settings.xml` 의 mirror(사내 Nexus)를 그대로 씁니다. 저장소 주소를 pom 에 추가하지 않습니다.

## 코딩 규칙
- 값 객체는 `record`, 정적 유틸 클래스는 `final` + private 생성자.
- 기존 공개 메서드(`parseLine`, `countByLevel`)의 이름 · 인자 · 반환 형태는 바꾸지 않습니다.
- 새 기능에는 테스트를 함께 씁니다 (정상 1개 이상 + 경계 1개 이상). 테스트 이름은 한국어로 동작을 적습니다.

## 하지 말 것
- pom.xml 에 의존성 · 플러그인을 추가하거나 버전을 바꾸지 않습니다. 필요하면 이유를 적어 먼저 물어봅니다.
- 테스트를 지우거나 `@Disabled` 로 통과시키지 않습니다.

## 완료 기준
- `./mvnw -q verify` 통과, 바꾼 파일 목록과 남은 위험을 3줄 이내로 보고.
