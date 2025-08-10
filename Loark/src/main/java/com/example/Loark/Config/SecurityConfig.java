package com.example.Loark.Config;

import com.example.Loark.Security.JwtAuthFilter;
import com.example.Loark.Security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 1) CSRF는 쿠키-JWT(stateless)라서 비활성, CORS는 기본 빈/설정 사용
        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());

        // 2) 세션 완전 비활성 (JWT만 사용)
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 3) 인증 실패 시 401 JSON으로 응답
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}");
        };
        http.exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint));

        // 4) OAuth2 로그인 (성공 핸들러에서 업서트 + JWT 쿠키 발급)
        http.oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler));

        // 5) JWT 인증 필터 체인 등록
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // 6) 접근 제어
        http.authorizeHttpRequests(auth -> auth
                // 프론트 정적 파일/헬스체크/문서 등 허용
                .requestMatchers("/", "/index.html", "/favicon.ico", "/static/**").permitAll()
                .requestMatchers("/actuator/health", "/health").permitAll()

                // OAuth2 엔드포인트 및 인증 유틸은 허용
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/auth/**").permitAll()   // /auth/me는 비로그인도 호출 가능(내부에서 authenticated=false로 응답)

                // 실제 API는 인증 필요
                .requestMatchers("/api/**").authenticated()

                // 그 외는 개발 중엔 열어두고, 배포 전에 필요시 tighten
                .anyRequest().permitAll()
        );

        return http.build();
    }
}
