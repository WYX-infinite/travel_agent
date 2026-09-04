package com.wyx.travelagent.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T06:00:00Z");
    private static final String SECRET = java.util.Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void issuesRequiredClaimsAndParsesCurrentUser() {
        JwtTokenService service = service(SECRET, "travel-planning-agent", Duration.ofHours(2));

        JwtTokenService.IssuedToken issued = service.issueToken(
                new CurrentUser(42, "demo-user", Role.ADMIN));

        CurrentUser currentUser = service.parse(issued.value());
        SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(SECRET));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer("travel-planning-agent")
                .clock(() -> java.util.Date.from(NOW))
                .build()
                .parseSignedClaims(issued.value())
                .getPayload();

        assertThat(currentUser).isEqualTo(new CurrentUser(42, "demo-user", Role.ADMIN));
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuer()).isEqualTo("travel-planning-agent");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenService service = service(SECRET, "travel-planning-agent", Duration.ofHours(2));
        String expired = token(SECRET, "travel-planning-agent", NOW.minusSeconds(120), NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenFromWrongIssuer() {
        JwtTokenService service = service(SECRET, "travel-planning-agent", Duration.ofHours(2));
        String wrongIssuer = token(SECRET, "another-issuer", NOW, NOW.plusSeconds(3600));

        assertThatThrownBy(() -> service.parse(wrongIssuer))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rejectsSecretShorterThanHs256Requirement() {
        String weakSecret = java.util.Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service(weakSecret, "travel-planning-agent", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void alwaysSignsWithHs256EvenWhenSecretIsLonger() {
        String longSecret = encodedSecret(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtTokenService service = service(longSecret, "travel-planning-agent", Duration.ofHours(2));

        JwtTokenService.IssuedToken issued = service.issueToken(
                new CurrentUser(42, "demo-user", Role.ADMIN));
        SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(longSecret));

        assertThat(Jwts.parser().verifyWith(key)
                .clock(() -> java.util.Date.from(NOW)).build()
                .parseSignedClaims(issued.value()).getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void rejectsTokenSignedWithAnotherHmacAlgorithm() {
        String longSecret = encodedSecret(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(longSecret));
        String hs512Token = Jwts.builder()
                .subject("42")
                .claim("username", "demo-user")
                .claim("role", "ADMIN")
                .issuer("travel-planning-agent")
                .issuedAt(java.util.Date.from(NOW))
                .expiration(java.util.Date.from(NOW.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        assertThatThrownBy(() -> service(longSecret, "travel-planning-agent", Duration.ofHours(2))
                .parse(hs512Token)).isInstanceOf(JwtException.class);
    }

    private JwtTokenService service(String secret, String issuer, Duration ttl) {
        return new JwtTokenService(new JwtProperties(secret, issuer, ttl), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String encodedSecret(String raw) {
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static String token(String secret, String issuer, Instant issuedAt, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        return Jwts.builder()
                .subject("42")
                .claim("username", "demo-user")
                .claim("role", "ADMIN")
                .issuer(issuer)
                .issuedAt(java.util.Date.from(issuedAt))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(key)
                .compact();
    }
}
