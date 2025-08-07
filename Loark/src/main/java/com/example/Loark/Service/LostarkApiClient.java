package com.example.Loark.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class LostarkApiClient {

    public boolean verifyNickname(String apiKey, String nickname) {
        try {
            String encodedNickname = UriUtils.encodePathSegment(nickname, StandardCharsets.UTF_8);
            String url = "https://developer-lostark.game.onstove.com/characters/" + encodedNickname + "/siblings";

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("보내는 API KEY: {}", apiKey);
            log.info("요청 URL: {}", url);
            log.info("Authorization 헤더: Bearer {}", apiKey);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                return false;
            }

            return response.getBody().contains("\"CharacterName\":\"" + nickname + "\"");
        } catch(Exception e) {
            log.error("API 호출 실패", e);
            return false;
        }
    }
}
