package io.wisoft.ignoa_api.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final JwtParser jwtParser;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, jwtProperties.refreshExpiration());
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, jwtProperties.accessExpiration());
    }

    private String createToken(Long userId, long expiration) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    public void validateToken(String token) {
        jwtParser.parseSignedClaims(token);
    }
}
