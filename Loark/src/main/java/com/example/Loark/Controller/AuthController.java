package com.example.Loark.Controller;


import com.example.Loark.Entity.User;
import com.example.Loark.Security.CookieUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor

public class AuthController {

    private final CookieUtil cookieUtil;

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User u)) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "userId", u.getUserId(),
                "displayName", u.getDisplayName(),
                "email", u.getUserEmail(),
                "pictureUrl", u.getPictureUrl()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse res) {
        cookieUtil.clearJwtCookie(res);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
