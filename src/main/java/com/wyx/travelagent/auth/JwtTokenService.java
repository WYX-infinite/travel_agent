package com.wyx.travelagent.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtTokenService {
    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = decodeKey(properties.secret());
    }

    public IssuedToken issueToken(CurrentUser currentUser) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        String value = Jwts.builder()
                .subject(currentUser.userId().toString())
                .claim("username", currentUser.username())
                .claim("role", currentUser.role().name())
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(value, expiresAt);
    }

    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = required(claims.getSubject(), "sub");
        String username = required(claims.get("username", String.class), "username");
        String role = required(claims.get("role", String.class), "role");
        if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new IllegalArgumentException("JWT must contain iat and exp");
        }
        return new CurrentUser(Integer.valueOf(subject), username, Role.valueOf(role));
    }

    private SecretKey decodeKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("travel.jwt.secret is required");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("travel.jwt.secret must be Base64 encoded", exception);
        }
        if (decoded.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException("travel.jwt.secret must decode to at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    private String required(String value, String claimName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT claim is required: " + claimName);
        }
        return value;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
