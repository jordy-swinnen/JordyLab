package dev.jordy.jordylab.mcp.service;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Accepts a token only when it was issued for the connector (FR-021, MCP authorization: a server validates that a token
 * was issued for it). The platform's own tokens carry no connector audience, so they are refused here.
 */
public record McpAudienceValidator(String audience) implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error("invalid_token", "The token was not issued for this resource", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
