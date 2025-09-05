package com.example.Loark.Security;

import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtTokenService;
    private final CookieUtil cookieUtil;

    // 환경변수로 프론트 주소 주입 (없으면 루트로)
    @Value("${app.frontend.redirect:/}")
    private String redirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 Login success. Starting to process user information.");
        try {
            OAuth2User oUser = (OAuth2User) authentication.getPrincipal();

            log.info("OAuth2User attributes: {}", oUser.getAttributes());

            String sub     = strAttr(oUser, "sub");
            String email   = strAttr(oUser, "email");   // 공개 비활성 가능
            String name    = strAttr(oUser, "name");
            String picture = strAttr(oUser, "picture");

            if (sub == null || sub.isBlank()) {
                log.error("Google OAuth2 login without 'sub' attribute");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Google login (missing sub).");
                return;
            }
            log.info("User sub: {}", sub);


            LocalDateTime now = LocalDateTime.now();

            // 존재하면 업데이트, 없으면 생성
            log.info("Finding or creating user...");
            User user = userRepository.findByGoogleSub(sub)
                    .map(u -> {
                        log.info("User found. Updating user information.");
                        u.setUserEmail(email);      // null 허용
                        u.setDisplayName(name);
                        u.setPictureUrl(picture);
                        u.setLastLoginAt(now);
                        return u;
                    })
                    .orElseGet(() -> {
                        log.info("User not found. Creating new user.");
                        return User.builder()
                                .googleSub(sub)         // ✅ 필수
                                .userEmail(email)
                                .displayName(name)
                                .pictureUrl(picture)
                                .userPassword("social_login") // 소셜 로그인 사용자는 비밀번호 없으므로 임의값 지정
                                .lastLoginAt(now)
                                .build();
                    });

            // @CreationTimestamp가 createdAt 채워줌
            log.info("Saving user to database...");
            User saved = userRepository.save(user);
            log.info("User saved successfully. User ID: {}", saved.getUserId());


            // JWT 생성 및 쿠키 세팅
            log.info("Creating JWT token...");
            String jwt = jwtTokenService.createToken(saved.getUserId());
            log.info("JWT token created. Adding cookie to response.");
            cookieUtil.addJwtCookie(response, jwt);

            // 프론트로 리다이렉트
            log.info("Redirecting to frontend: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("Error occurred in OAuth2SuccessHandler", e);
            // 에러 발생 시 리다이렉트 또는 에러 응답
            response.sendRedirect("/login?error=handler_error");
        }
    }

    private static String strAttr(OAuth2User user, String key) {
        Object v = user.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }
}
