package dev.jordy.jordylab.mcp.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import static org.assertj.core.api.Assertions.assertThat;

class McpAudienceValidatorTest {

    private final McpAudienceValidator validator = new McpAudienceValidator("jordylab-mcp");

    @Test
    void acceptsATokenIssuedForTheConnector() {
        OAuth2TokenValidatorResult result = validator.validate(McpJwtTestBuilder.aDefaultJwt());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void refusesATokenWithoutTheConnectorAudience() {
        OAuth2TokenValidatorResult result = validator.validate(McpJwtTestBuilder.aJwt()
                .audience(java.util.List.of("account"))
                .build());

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void refusesATokenWithNoAudienceAtAll() {
        OAuth2TokenValidatorResult result = validator.validate(McpJwtTestBuilder.aJwt()
                .audience(java.util.List.of())
                .build());

        assertThat(result.hasErrors()).isTrue();
    }
}
