package com.example.Loark.Security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-minutes:60}")
    private long expiryMinutes;

    public String createToken(Long userId) {
        long now = System.currentTimeMillis();
        Date iat = new Date(now);
        Date exp = new Date(now + expiryMinutes * 60_000);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))   // 주제: userId
                .setIssuedAt(iat)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes())
                .compact();
    }

    public Long parseUserId(String token) {
        Claims c = Jwts.parser()
                .setSigningKey(secret.getBytes())
                .parseClaimsJws(token)
                .getBody();
        return Long.valueOf(c.getSubject());
    }
}
