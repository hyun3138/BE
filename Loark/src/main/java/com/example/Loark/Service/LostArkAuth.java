package com.example.Loark.Service;

import com.example.Loark.DTO.DefaultResponse;
import com.example.Loark.Entity.User;
import com.example.Loark.Util.ResponseMessage;
import com.example.Loark.Util.StatusCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

@Service
public class LostArkAuth {

    /**
     * 사용자의 고유 ID를 기반으로 Base64 인코딩된 인증 코드를 생성합니다.
     */
    public String genRandCode(User user) {
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

    /**
     * 사용자의 스토브 프로필을 방문하여 자기소개란의 인증 코드를 검증합니다.
     * @param user 현재 로그인된 사용자 엔터티
     * @param stoveUrl 사용자가 입력한 스토브 프로필 URL
     * @return 검증 결과 (성공 시 스토브 멤버 번호 포함)
     */
    public DefaultResponse<String> verifyStoveProfile(User user, String stoveUrl) {
        // 1. URL 유효성 검사 및 멤버 번호 추출
        DefaultResponse<String> stoveNoResponse = getStoveNo(stoveUrl);
        if (stoveNoResponse.getStatusCode() != StatusCode.OK) {
            return stoveNoResponse;
        }
        String stoveMemberNo = stoveNoResponse.getData();

        // 2. 사용자의 인증 코드 확인
        String savedAuthCode = user.getAuthCode();
        if (savedAuthCode == null || savedAuthCode.isEmpty()) {
            return new DefaultResponse<>(StatusCode.BAD_REQUEST, ResponseMessage.CERTIFICATION_CODE_FAIL, "인증 코드가 먼저 발급되어야 합니다.");
        }

        // 3. Jsoup을 사용하여 스토브 프로필 페이지 스크래핑
        try {
            Document doc = Jsoup.connect(stoveUrl).get();
            // "p.line-clamp-2" 클래스를 가진 p 태그의 텍스트를 가져옵니다.
            Element introElement = doc.selectFirst("p.line-clamp-2");

            if (introElement == null) {
                return new DefaultResponse<>(StatusCode.NOT_FOUND, ResponseMessage.STOVE_INTRODUCTION_FAIL, "자기소개 영역을 찾을 수 없습니다.");
            }

            String introductionText = introElement.text();

            // 4. 자기소개 텍스트에 저장된 인증 코드가 포함되어 있는지 확인
            if (introductionText.contains(savedAuthCode)) {
                // 5. 인증 성공
                return new DefaultResponse<>(StatusCode.OK, ResponseMessage.STOVE_CERTIFICATION_SUCCESS, stoveMemberNo);
            } else {
                return new DefaultResponse<>(StatusCode.BAD_REQUEST, ResponseMessage.STOVE_CERTIFICATION_FAIL, "자기소개글에서 인증 코드를 찾을 수 없습니다.");
            }

        } catch (IOException e) {
            e.printStackTrace();
            return new DefaultResponse<>(StatusCode.INTERNAL_SERVER_ERROR, "프로필 정보를 가져오는 중 오류가 발생했습니다.", null);
        }
    }

    /**
     * 스토브 URL에서 멤버 번호를 추출하고 유효성을 검사합니다.
     */
    public DefaultResponse<String> getStoveNo(String url) {
        if (url == null || !url.startsWith("https://profile.onstove.com/ko/")) {
            return new DefaultResponse<>(StatusCode.URL_ERROR, ResponseMessage.STOVE_URL_AGAIN, null);
        }
        String memberNo = url.substring("https://profile.onstove.com/ko/".length());

        try {
            Long.parseLong(memberNo); // 숫자 형식인지 확인
        } catch (NumberFormatException e) {
            return new DefaultResponse<>(StatusCode.URL_ERROR, ResponseMessage.STOVE_URL_AGAIN, null);
        }

        return new DefaultResponse<>(StatusCode.OK, "멤버 번호 추출 성공", memberNo);
    }
}
