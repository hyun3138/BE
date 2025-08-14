package com.example.Loark.Service;

import com.example.Loark.DTO.Character_ArkPassive;
import com.example.Loark.DTO.Character_Profile;
import com.example.Loark.DTO.LoaSiblings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class LostarkApiClient {

    private RestTemplate rt = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpEntity<String> auth(String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + apiKey);
        h.set("accept", "application/json");
        return new HttpEntity<>(h);
    }

    public Character_Profile fetchProfile(String apiKey, String characterName) {
        try {
            String enc = UriUtils.encodePathSegment(characterName, StandardCharsets.UTF_8);
            String url = "https://developer-lostark.game.onstove.com/armories/characters/" + enc + "/profiles";

            ResponseEntity<String> res =
                    rt.exchange(url, HttpMethod.GET, auth(apiKey), String.class);

            if (res.getStatusCode() != HttpStatus.OK || res.getBody() == null) {
                throw new IllegalStateException("프로필 조회 실패: " + res.getStatusCode());
            }

            Character_Profile profile = mapper.readValue(res.getBody(), Character_Profile.class);

            // combatPower가 응답에 없으면 null로 둔다(서비스에서 null-safe 변환)
            return profile;
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("캐릭터를 찾을 수 없습니다: " + characterName);
        } catch (Exception e) {
            log.error("fetchProfile 실패 - {} ", characterName, e);
            throw new IllegalStateException("프로필 조회 중 오류가 발생했습니다.");
        }
    }
    public List<LoaSiblings> fetchSiblings(String apiKey, String characterName) {
        try {
            String enc = UriUtils.encodePathSegment(characterName, StandardCharsets.UTF_8);
            String url = "https://developer-lostark.game.onstove.com/characters/" + enc + "/siblings";
            ResponseEntity<String> res = rt.exchange(url, HttpMethod.GET, auth(apiKey), String.class);
            if (res.getStatusCode() != HttpStatus.OK || res.getBody() == null) return List.of();
            var type = mapper.getTypeFactory().constructCollectionType(List.class, LoaSiblings.class);
            return mapper.readValue(res.getBody(), type);
        } catch (Exception e) {
            log.error("fetchSiblings 실패 - {}", characterName, e);
            return List.of();
        }
    }

    /** 닉네임의 원정대(=siblings) 닉네임 목록 조회 */
    public List<String> getSiblings(String apiKey, String nickname) {
        try {
            String enc = UriUtils.encodePathSegment(nickname, StandardCharsets.UTF_8);
            String url = "https://developer-lostark.game.onstove.com/characters/" + enc + "/siblings";
            var res = rt.exchange(url, HttpMethod.GET, auth(apiKey), String.class);
            if (res.getStatusCode() != HttpStatus.OK || res.getBody() == null) return List.of();

            // 최소 파싱 (Jackson 추천이지만 간단히 정규/스캔도 가능)
            // [{"ServerName":"...","CharacterName":"닉1",...}, ...]
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var arr = mapper.readTree(res.getBody());
            List<String> names = new java.util.ArrayList<>();
            if (arr.isArray()) {
                for (var n : arr) {
                    var name = n.get("CharacterName");
                    if (name != null) names.add(name.asText());
                }
            }
            return names;
        } catch (Exception e) {
            log.error("siblings 조회 실패 {}", nickname, e);
            return List.of();
        }
    }

    /** 주어진 닉네임이 실제 존재하는지 빠른 확인(옵션) */
    public boolean existsCharacter(String apiKey, String nickname) {
        var sib = getSiblings(apiKey, nickname);
        // 본인도 siblings에 포함되어 내려오기 때문에 빈 배열이면 존재X로 판단
        return !sib.isEmpty();
    }
    public String fetchArkPassiveTitle(String apiKey, String characterName) {
        try {
            String enc = UriUtils.encodePathSegment(characterName, StandardCharsets.UTF_8);
            String url = "https://developer-lostark.game.onstove.com/armories/characters/" + enc + "?filters=arkpassive";
            log.info("[ARK] GET {}", url);
            ResponseEntity<String> res = rt.exchange(url, HttpMethod.GET, auth(apiKey), String.class);
            log.info("[ARK] status={}, title={}",
                    res.getStatusCode(),
                    mapper.readTree(res.getBody()).path("ArkPassive").path("Title").asText(null));
            if (res.getStatusCode() != HttpStatus.OK || res.getBody() == null) {
                log.warn("arkpassive 조회 실패 status={}, name={}", res.getStatusCode(), characterName);
                return null;
            }

            Character_ArkPassive root = mapper.readValue(res.getBody(), Character_ArkPassive.class);
            if (root.getArkPassive() == null) return null;
            return root.getArkPassive().getTitle(); // ex) "질풍노도"
        } catch (HttpClientErrorException.NotFound e) {
            // 캐릭 삭제/명 중복 등으로 프로필 없음
            return null;
        } catch (Exception e) {
            log.error("fetchArkPassiveTitle 실패 - {}", characterName, e);
            return null;
        }
    }
}
