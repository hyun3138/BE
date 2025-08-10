package com.example.Loark.Service;

import com.example.Loark.DTO.DefaultResponse;
import com.example.Loark.Entity.User;
import com.example.Loark.Util.ResponseMessage;
import com.example.Loark.Util.StatusCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

@Service
public class LostArkAuth {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String genRandCode(User user) {
        // ... (이전과 동일)
        String randCode = "";
        Base64.Encoder encode = Base64.getEncoder();
        try {
            byte[] encodeData = encode.encode(user.getGoogleSub().getBytes());
            randCode = new String(encodeData);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return randCode;
    }

    public DefaultResponse<String> verifyStoveProfile(User user, String stoveUrl) {
        // 1. URL에서 stoveMemberNo 추출
        DefaultResponse<String> stoveNoResponse = getStoveNo(stoveUrl);
        if (stoveNoResponse.getStatusCode() != StatusCode.OK) {
            return stoveNoResponse;
        }
        String stoveMemberNo = stoveNoResponse.getData();

        // 2. 저장된 인증 코드 확인
        String savedAuthCode = user.getAuthCode();
        if (savedAuthCode == null || savedAuthCode.isEmpty()) {
            return new DefaultResponse<>(StatusCode.BAD_REQUEST, ResponseMessage.CERTIFICATION_CODE_FAIL, "인증 코드가 먼저 발급되어야 합니다.");
        }

        // 3. 스토브 API로 자기소개글의 인증 코드 검증
        try {
            String apiUrl = "https://api.onstove.com/tm/v1/preferences/" + stoveMemberNo;
            JsonNode responseNode = httpGetConnection(apiUrl);

            if (responseNode == null || !responseNode.has("result") || !responseNode.get("result").asText().equals("000")) {
                return new DefaultResponse<>(StatusCode.BAD_REQUEST, ResponseMessage.STOVE_INTRODUCTION_FAIL, "스토브 API에서 자기소개 정보를 가져올 수 없습니다.");
            }

            String introductionText = responseNode.get("data").get("introduce").asText();
            if (!introductionText.contains(savedAuthCode)) {
                return new DefaultResponse<>(StatusCode.BAD_REQUEST, ResponseMessage.STOVE_CERTIFICATION_FAIL, "자기소개글에서 인증 코드를 찾을 수 없습니다.");
            }

            // 4. 인증 성공! 이제 대표 캐릭터명을 가져옵니다.
            // 4-1. 암호화된 멤버 번호(encryptMemberNo) 가져오기
            String encryptMemberNo = getEncryptMemberNo(stoveMemberNo);
            if (encryptMemberNo == null) {
                return new DefaultResponse<>(StatusCode.INTERNAL_SERVER_ERROR, "대표 캐릭터 정보를 조회하는 중 오류가 발생했습니다. (encryptMemberNo 획득 실패)", null);
            }

            // 4-2. encryptMemberNo로 대표 캐릭터명 스크래핑
            String mainCharacter = getMainCharacter(encryptMemberNo);
            if (mainCharacter == null) {
                return new DefaultResponse<>(StatusCode.NOT_FOUND, "대표 캐릭터를 찾을 수 없습니다. 로스트아크 홈페이지에 대표 캐릭터가 설정되어 있는지 확인해주세요.", null);
            }

            // 5. 최종 성공: 대표 캐릭터명 반환
            return new DefaultResponse<>(StatusCode.OK, ResponseMessage.STOVE_CERTIFICATION_SUCCESS, mainCharacter);

        } catch (Exception e) {
            e.printStackTrace();
            return new DefaultResponse<>(StatusCode.INTERNAL_SERVER_ERROR, "인증 중 알 수 없는 오류가 발생했습니다.", null);
        }
    }

    private String getEncryptMemberNo(String memberNo) throws Exception {
        String apiUrl = "https://lostark.game.onstove.com/board/IsCharacterList";
        String body = "{\"memberNo\": \"" + memberNo + "\"}";
        JsonNode responseNode = httpPostConnection(apiUrl, body);

        if (responseNode != null && responseNode.has("encryptMemberNo")) {
            return responseNode.get("encryptMemberNo").asText();
        }
        return null;
    }

    private String getMainCharacter(String encryptMemberNo) throws Exception {
        String profileUrl = "http://lostark.game.onstove.com/Profile/Member?id=" + encryptMemberNo;
        Document doc = Jsoup.connect(profileUrl).get();
        Element nameElement = doc.selectFirst("span.profile-character-info__name");
        return (nameElement != null) ? nameElement.text() : null;
    }

    public DefaultResponse<String> getStoveNo(String url) {
        // ... (이전과 동일)
        if (url == null || !url.startsWith("https://profile.onstove.com/ko/")) {
            return new DefaultResponse<>(StatusCode.URL_ERROR, ResponseMessage.STOVE_URL_AGAIN, null);
        }
        String memberNo = url.substring("https://profile.onstove.com/ko/".length());
        try {
            Long.parseLong(memberNo);
        } catch (NumberFormatException e) {
            return new DefaultResponse<>(StatusCode.URL_ERROR, ResponseMessage.STOVE_URL_AGAIN, null);
        }
        return new DefaultResponse<>(StatusCode.OK, "멤버 번호 추출 성공", memberNo);
    }

    private JsonNode httpGetConnection(String desiredUrl) throws Exception {
        URL url = new URL(desiredUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-type", "application/json");

        try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300 ? conn.getInputStream() : conn.getErrorStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            return objectMapper.readTree(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private JsonNode httpPostConnection(String desiredUrl, String body) throws Exception {
        URL url = new URL(desiredUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300 ? conn.getInputStream() : conn.getErrorStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            return objectMapper.readTree(sb.toString());
        } finally {
            conn.disconnect();
        }
    }
}