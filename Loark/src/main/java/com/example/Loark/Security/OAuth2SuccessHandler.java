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

        OAuth2User oUser = (OAuth2User) authentication.getPrincipal();

        String sub     = strAttr(oUser, "sub");
        String email   = strAttr(oUser, "email");   // 공개 비활성 가능
        String name    = strAttr(oUser, "name");
        String picture = strAttr(oUser, "picture");

        if (sub == null || sub.isBlank()) {
            log.error("Google OAuth2 login without 'sub' attribute");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Google login (missing sub).");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 존재하면 업데이트, 없으면 생성
        User user = userRepository.findByGoogleSub(sub)
                .map(u -> {
                    u.setUserEmail(email);      // null 허용
                    u.setDisplayName(name);
                    u.setPictureUrl(picture);
                    u.setLastLoginAt(now);
                    return u;
                })
                .orElseGet(() -> User.builder()
                        .googleSub(sub)         // ✅ 필수
                        .userEmail(email)
                        .displayName(name)
                        .pictureUrl(picture)
                        .lastLoginAt(now)
                        .build());

        // @CreationTimestamp가 createdAt 채워줌
        User saved = userRepository.save(user);

        // JWT 생성 및 쿠키 세팅
        String jwt = jwtTokenService.createToken(saved.getUserId());
        cookieUtil.addJwtCookie(response, jwt);

        // 프론트로 리다이렉트
        response.sendRedirect(redirectUrl);
    }

    private static String strAttr(OAuth2User user, String key) {
        Object v = user.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }
}
