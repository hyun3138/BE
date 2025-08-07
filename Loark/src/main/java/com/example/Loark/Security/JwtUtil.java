package com.example.Loark.Security;

import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final String secretKey = "skX2mS7gVbT0Pq9Fj3HsLwZy1NcRt8Ux"; // 환경변수로 바꾸기
    private final long EXPIRATION = 1000 * 60 * 60;

    public String createToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRATION))
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes())
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes())
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
