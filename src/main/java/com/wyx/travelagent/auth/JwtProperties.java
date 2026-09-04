package com.wyx.travelagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("travel.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
    public static final String REQUIRED_ISSUER = "travel-planning-agent";
    public static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofHours(2);

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = REQUIRED_ISSUER;
        }
        if (!REQUIRED_ISSUER.equals(issuer)) {
            throw new IllegalArgumentException("travel.jwt.issuer must be " + REQUIRED_ISSUER);
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = DEFAULT_ACCESS_TOKEN_TTL;
        }
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("travel.jwt.access-token-ttl must be positive");
        }
    }
}
