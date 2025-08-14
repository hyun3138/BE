package com.example.Loark.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * 애플리케이션 전역에서 발생하는 예외를 처리하는 클래스
 */
@Hidden // springdoc이 이 클래스를 스캔하지 않도록 제외합니다.
@ControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * WebClient가 외부 API(FastAPI) 호출 중 4xx, 5xx 에러를 받았을 때 발생하는 예외를 처리합니다.
     * @param ex 발생한 예외 객체
     * @return 클라이언트에게 보낼 ResponseEntity
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Object> handleWebClientResponseException(WebClientResponseException ex) {
        // FastAPI에서 보낸 에러 메시지(JSON 본문)를 문자열로 가져옵니다.
        String errorBody = ex.getResponseBodyAsString();

        // 기본 에러 메시지
        String detailMessage = "분석 서버(FastAPI)에서 오류가 발생했습니다.";

        // FastAPI의 에러 본문 형식인 {"detail": "..."}을 파싱합니다.
        try {
            JsonNode errorNode = objectMapper.readTree(errorBody);
            if (errorNode.has("detail")) {
                detailMessage = errorNode.get("detail").asText();
            }
        } catch (JsonProcessingException e) {
            // JSON 파싱에 실패하면, 원본 에러 메시지를 그대로 사용합니다.
            System.err.println("FastAPI 에러 응답 파싱 실패: " + errorBody);
            detailMessage = errorBody;
        }

        // 서버 로그에 에러를 기록합니다.
        System.err.println("FastAPI Error: " + detailMessage);

        // 클라이언트(사용자 화면)에 보낼 에러 응답을 생성합니다.
        // FastAPI의 상태 코드와 파싱한 detail 메시지를 그대로 전달합니다.
        return ResponseEntity
                .status(ex.getStatusCode()) // FastAPI의 상태 코드를 그대로 사용
                .body(Map.of("error", detailMessage)); // 프론트엔드에서 사용하기 쉽게 Map 형태로 반환
    }
}
