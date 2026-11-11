// src/main/java/com/example/ticketdesk/config/SecurityConfig.java
package com.example.ticketdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()                    // 쿠버네티스 헬스체크
                .requestMatchers(HttpMethod.PATCH, "/api/requests/**").hasRole("HANDLER") // 상태 변경은 담당자만
                .anyRequest().authenticated())
            .formLogin(form -> form.defaultSuccessUrl("/requests", true))              // 기본 로그인 화면 사용
            .logout(Customizer.withDefaults());
        // CSRF 보호는 기본으로 켜져 있습니다. 세션 14 화면의 meta 태그가 토큰을 헤더로 보냅니다.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // bcrypt 기본
    }

    /** 실습용 사용자 2명. 사내 적용 시 LDAP · AD · SSO(OIDC) 로 교체합니다. */
    @Bean
    UserDetailsService users(PasswordEncoder encoder, @Value("${app.demo-password}") String demoPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("staff1").password(encoder.encode(demoPassword)).roles("USER").build(),
                User.withUsername("handler1").password(encoder.encode(demoPassword)).roles("USER", "HANDLER").build());
    }
}
