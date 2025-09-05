package com.example.Loark.Config;

import com.example.Loark.Security.JwtAuthFilter;
import com.example.Loark.Security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 1) CSRF 비활성, CORS는 corsConfigurationSource Bean 사용
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // ✅ Form Login, HTTP Basic 인증 비활성화 (API 서버이므로)
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);

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
        http.oauth2Login(oauth -> oauth
                .successHandler(oAuth2SuccessHandler)
                // ✅ 인증 실패 시, 프론트엔드 에러 페이지로 리다이렉트하도록 핸들러 추가
                .failureHandler((request, response, exception) -> {
                    log.warn("OAuth2 Login failed: {}", exception.getMessage());
                    String targetUrl = UriComponentsBuilder.fromUriString("https://loark.kr/login-error") // 프론트엔드의 에러 페이지 경로
                            .queryParam("error", exception.getLocalizedMessage())
                            .build().toUriString();
                    response.sendRedirect(targetUrl);
                })
        );

        // 5) JWT 인증 필터 체인 등록
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // 6) 접근 제어
        http.authorizeHttpRequests(auth -> auth
                // CORS Pre-flight 요청은 인증 없이 허용
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 프론트 정적 파일/헬스체크/문서 등 허용
                .requestMatchers("/", "/index.html", "/favicon.ico", "/static/**").permitAll()
                .requestMatchers("/actuator/health", "/health").permitAll()

                // OAuth2 엔드포인트 및 인증 유틸은 허용
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/auth/**").permitAll()   // /auth/me는 비로그인도 호출 가능(내부에서 authenticated=false로 응답)

                // ✅ 공개 공대 목록은 비로그인 허용
                .requestMatchers(HttpMethod.GET, "/api/parties/public").permitAll()

                // 실제 API는 인증 필요
                .requestMatchers("/api/**").authenticated()

                // ✅ 그 외 모든 요청은 인증을 요구하도록 변경 (보안 강화)
                .anyRequest().authenticated()
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "https://loark.kr",
                "https://www.loark.kr",
                "http://loark.kr",
                "http://api.loark.kr",
                "https://api.loark.kr",
                "http://localhost:5173", // Vite 개발 서버
                "http://localhost:3000"  // CRA 개발 서버
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
