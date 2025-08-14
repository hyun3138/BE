package com.example.Loark.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Value("${fastapi.server.url}") // application.properties에서 FastAPI 서버 주소 가져오기
    private String fastapiServerUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(fastapiServerUrl) // FastAPI 서버의 기본 URL 설정
                .build();
    }
}
