package dev.jordy.jordylab.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    private static Jwt aJwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .claims(map -> map.putAll(claims))
                .issuedAt(Instant.parse("2026-08-02T10:00:00Z"))
                .expiresAt(Instant.parse("2026-08-02T11:00:00Z"))
                .build();
    }

    // JwtAuthenticationConverter.convert() adds its own FACTOR_BEARER authority alongside
    // whatever the realm-roles converter under test returns, so assertions here only look at
    // the ROLE_* authorities extractRealmRoles is actually responsible for.
    private List<String> realmRoleAuthorities(Jwt jwt) {
        Converter<Jwt, AbstractAuthenticationToken> converter = securityConfig.jwtAuthenticationConverter();
        Collection<GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
    }

    @Test
    void mapsRealmRolesToRolePrefixedAuthorities() {
        Jwt jwt = aJwtWithClaims(Map.of(
                "sub", "jordy",
                "realm_access", Map.of("roles", List.of("jordylab-user", "gamecatalog-scanner"))));

        assertThat(realmRoleAuthorities(jwt)).containsExactlyInAnyOrder("ROLE_jordylab-user", "ROLE_gamecatalog-scanner");
    }

    @Test
    void returnsNoAuthoritiesWhenRealmAccessClaimIsMissing() {
        Jwt jwt = aJwtWithClaims(Map.of("sub", "jordy"));

        assertThat(realmRoleAuthorities(jwt)).isEmpty();
    }

    @Test
    void returnsNoAuthoritiesWhenRealmAccessIsNotAMap() {
        Jwt jwt = aJwtWithClaims(Map.of("sub", "jordy", "realm_access", "not-a-map"));

        assertThat(realmRoleAuthorities(jwt)).isEmpty();
    }

    @Test
    void returnsNoAuthoritiesWhenRolesClaimIsNotACollection() {
        Jwt jwt = aJwtWithClaims(Map.of("sub", "jordy", "realm_access", Map.of("roles", "not-a-list")));

        assertThat(realmRoleAuthorities(jwt)).isEmpty();
    }

    @Test
    void ignoresNonStringEntriesInTheRolesList() {
        List<Object> rolesWithANonStringEntry = new java.util.ArrayList<>();
        rolesWithANonStringEntry.add("jordylab-user");
        rolesWithANonStringEntry.add(42);
        Jwt jwt = aJwtWithClaims(Map.of("sub", "jordy", "realm_access", Map.of("roles", rolesWithANonStringEntry)));

        assertThat(realmRoleAuthorities(jwt)).containsExactly("ROLE_jordylab-user");
    }
}
