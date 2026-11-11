package com.example.ticketdesk.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 필수 설정 헬스체크 — Next.js 트랙의 /api/health 와 같은 규칙: DB · LLM 은 부르지 않고 설정 값이 있는지만 봅니다.
 * 빈 이름(requiredConfigHealthIndicator)에서 접미사를 뺀 'requiredConfig' 로 readiness 그룹에 넣습니다.
 */
@Component
public class RequiredConfigHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RequiredConfigHealthIndicator.class);
    private static final List<String> REQUIRED =
            List.of("spring.datasource.url", "spring.datasource.password", "spring.ai.anthropic.api-key");

    private final Environment env;

    public RequiredConfigHealthIndicator(Environment env) {
        this.env = env;
    }

    @Override
    public Health health() {
        List<String> missing = REQUIRED.stream()
                .filter(key -> !StringUtils.hasText(env.getProperty(key, "")))
                .toList();
        if (!missing.isEmpty()) {
            log.error("필수 설정 누락: {}", missing);   // 어떤 값이 빠졌는지는 로그에만
            return Health.down().build();               // 응답에는 상태만
        }
        return Health.up().build();
    }
}
