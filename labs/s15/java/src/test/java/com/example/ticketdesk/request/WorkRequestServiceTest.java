package com.example.ticketdesk.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/** 서비스 계층 정책(마스킹 · 역할 판단) 단위 테스트 — DB · API 키 없이 ./mvnw -q test */
class WorkRequestServiceTest {

    private static Authentication user(String name, String... roles) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, AuthorityUtils.createAuthorityList(roles));
    }

    @Test
    void 민감정보_형태를_가린다() {
        String masked = WorkRequestService.mask("연락처 010-1234-5678, 계좌 123-45-678901, 주민 900101-1234567");
        assertThat(masked).isEqualTo("연락처 [전화번호], 계좌 [계좌번호], 주민 [주민번호]");
    }

    @Test
    void 담당자_역할만_전체를_본다() {
        assertThat(WorkRequestService.isHandler(user("handler1", "ROLE_USER", "ROLE_HANDLER"))).isTrue();
        assertThat(WorkRequestService.isHandler(user("staff1", "ROLE_USER"))).isFalse();
    }
}
