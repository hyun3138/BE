package com.example.Loark.Controller;

import com.example.Loark.DTO.DefaultResponse;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Security.CookieUtil;
import com.example.Loark.Service.LostArkAuth;
import com.example.Loark.Util.ResponseMessage;
import com.example.Loark.Util.StatusCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final LostArkAuth lostArkAuth;
    private final UserRepository userRepository;
    private final CookieUtil cookieUtil;

    @GetMapping("/auth/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User u)) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("authenticated", true);
        responseBody.put("userId", u.getUserId());
        responseBody.put("displayName", u.getDisplayName());
        responseBody.put("email", u.getUserEmail());
        responseBody.put("pictureUrl", u.getPictureUrl());
        responseBody.put("stoveMemberNo", u.getStoveMemberNo());
        responseBody.put("mainCharacter", u.getMainCharacter()); // 대표 캐릭터 정보 추가

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletResponse res) {
        cookieUtil.clearJwtCookie(res);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Transactional
    @GetMapping("/api/auth/code")
    public ResponseEntity<DefaultResponse<String>> generateAuthCode() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return new ResponseEntity<>(
                    new DefaultResponse<>(StatusCode.UNAUTHORIZED, ResponseMessage.LOGIN_FAIL, null),
                    HttpStatus.UNAUTHORIZED);
        }

        // User 파라미터 없이 메소드 호출
        String authCode = lostArkAuth.genRandCode();
        user.setAuthCode(authCode);
        userRepository.save(user);

        if (authCode != null && !authCode.isEmpty() && !authCode.equals("RANDOM_CODE_GENERATION_ERROR")) {
            return new ResponseEntity<>(
                    new DefaultResponse<>(StatusCode.OK, ResponseMessage.CERTIFICATION_CODE_SUCCESS, authCode),
                    HttpStatus.OK);
        } else {
            return new ResponseEntity<>(
                    new DefaultResponse<>(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.CERTIFICATION_CODE_FAIL, null),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PostMapping("/api/auth/verify")
    public ResponseEntity<DefaultResponse<String>> verifyStove(@RequestBody Map<String, String> payload) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return new ResponseEntity<>(
                    new DefaultResponse<>(StatusCode.UNAUTHORIZED, ResponseMessage.LOGIN_FAIL, null),
                    HttpStatus.UNAUTHORIZED);
        }

        String stoveUrl = payload.get("stoveUrl");
        if (stoveUrl == null || stoveUrl.isBlank()) {
            return new ResponseEntity<>(
                    new DefaultResponse<>(StatusCode.BAD_REQUEST, "스토브 URL을 입력해주세요.", null),
                    HttpStatus.BAD_REQUEST);
        }

        // 서비스 호출 (이제 성공 시 대표 캐릭터명을 반환)
        DefaultResponse<String> verificationResponse = lostArkAuth.verifyStoveProfile(user, stoveUrl);

        if (verificationResponse.getStatusCode() == StatusCode.OK) {
            // 인증 성공 시, 사용자 정보 업데이트
            String mainCharacter = verificationResponse.getData();
            String stoveMemberNo = lostArkAuth.getStoveNo(stoveUrl).getData(); // stoveMemberNo 다시 추출

            user.setMainCharacter(mainCharacter);
            user.setStoveMemberNo(stoveMemberNo);
            user.setStoveProfileUrl(stoveUrl);
            userRepository.save(user);
        }

        // 서비스의 응답을 그대로 클라이언트에게 반환
        return new ResponseEntity<>(verificationResponse, HttpStatus.valueOf(verificationResponse.getStatusCode()));
    }
}
