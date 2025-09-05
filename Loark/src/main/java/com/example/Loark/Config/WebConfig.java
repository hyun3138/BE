package com.example.Loark.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로에 대해
                .allowedOrigins("https://loark.kr") // 로컬 프론트엔드 주소 허용
                .allowedOrigins(
                         "https://loark.kr",
                         "https://www.loark.kr",
                         "http://loark.kr",
                         "http://localhost:5173", // Vite 개발 서버
                         "http://localhost:3000"  // CRA 개발 서버
                 ) // 허용할 프론트엔드 도메인
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 허용할 HTTP 메소드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true) // 쿠키 등 자격 증명 허용
                .maxAge(3600); // pre-flight 요청의 캐시 시간(초)
    }
}
