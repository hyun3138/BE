package com.example.Loark.Security;

import com.example.Loark.Entity.User;
import com.example.Loark.Repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor

public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtTokenService;
    private final UserRepository userRepository;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String token = extractJwtFromCookie(req);

        if (StringUtils.hasText(token)) {
            try {
                Long userId = jwtTokenService.parseUserId(token);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    Authentication auth = new UserAuthentication(user);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {}
        }

        chain.doFilter(request, response);
    }

    private String extractJwtFromCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("LOARK_JWT".equals(c.getName())) return c.getValue();
        }
        return null;
    }

    static class UserAuthentication extends AbstractAuthenticationToken {
        private final User principal;
        UserAuthentication(User user) {
            super(List.of()); // 권한 필요 없으면 빈 리스트
            this.principal = user;
            setAuthenticated(true);
        }
        @Override public Object getCredentials() { return ""; }
        @Override public Object getPrincipal() { return principal; }
    }
}
