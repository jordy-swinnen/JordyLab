package dev.jordy.jordylab.mcp.service;

import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@UtilityClass
class McpJwtTestBuilder {

    public static final String DEFAULT_SUBJECT = "00000000-0000-0000-0000-00000000000a";
    public static final String DEFAULT_AUDIENCE = "jordylab-mcp";
    public static final String DEFAULT_ROLE = "admin";
    public static final Instant DEFAULT_ISSUED_AT = Instant.parse("2026-11-03T10:00:00Z");
    public static final Instant DEFAULT_EXPIRES_AT = Instant.parse("2026-11-03T10:01:00Z");

    public static Jwt aDefaultJwt() {
        return aJwt().build();
    }

    public static Jwt.Builder aJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(DEFAULT_SUBJECT)
                .audience(List.of(DEFAULT_AUDIENCE))
                .issuedAt(DEFAULT_ISSUED_AT)
                .expiresAt(DEFAULT_EXPIRES_AT)
                .claim("realm_access", Map.of("roles", List.of(DEFAULT_ROLE)));
    }
}
