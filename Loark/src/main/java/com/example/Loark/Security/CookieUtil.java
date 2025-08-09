package com.example.Loark.Security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {
    @Value("${app.cookie.domain}")
    private String domain;

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.same-site:Strict}")
    private String sameSite;

    public void addJwtCookie(HttpServletResponse res, String token) {
        Cookie cookie = new Cookie("LOARK_JWT", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setDomain(domain);
        cookie.setMaxAge(60 * 60); // 1h
        res.addHeader("Set-Cookie",
                String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; %s; %s%s",
                        cookie.getName(),
                        cookie.getValue(),
                        cookie.getMaxAge(),
                        secure ? "Secure" : "",
                        "Domain="+domain+";",
                        "SameSite="+sameSite));
    }

    public void clearJwtCookie(HttpServletResponse res) {
        res.addHeader("Set-Cookie",
                "LOARK_JWT=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
    }
}
