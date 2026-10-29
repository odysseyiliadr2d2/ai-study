# Dockerfile — ticket-desk-api (Spring Boot 계층형 jar, Java 21)
# 빌드: ./mvnw -B package -DskipTests && docker build -t ticket-desk-api .
# 계층으로 나누면 코드만 바뀐 배포에서 의존성 계층(수십 MB)을 다시 받지 않습니다.
ARG BASE_IMAGE=eclipse-temurin:21-jre

FROM ${BASE_IMAGE} AS builder
WORKDIR /builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
# Spring Boot 공식 방식: jarmode=tools 로 계층 추출
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
WORKDIR /application
RUN groupadd --system app && useradd --system --gid app app
# 자주 안 바뀌는 계층부터 복사 (docker 캐시 · 레지스트리 전송량 절약)
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
USER app
EXPOSE 8080
# 컨테이너 메모리 한도의 75% 를 힙으로
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
