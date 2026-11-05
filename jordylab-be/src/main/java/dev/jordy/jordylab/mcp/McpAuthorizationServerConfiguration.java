package dev.jordy.jordylab.mcp;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.jordy.jordylab.mcp.service.McpAudienceValidator;
import dev.jordy.jordylab.mcp.service.McpAuthorizationRequestValidator;
import dev.jordy.jordylab.mcp.service.McpAuthorizationService;
import dev.jordy.jordylab.mcp.service.McpRefreshClientAuthenticationConverter;
import dev.jordy.jordylab.mcp.service.McpRefreshClientAuthenticationProvider;
import dev.jordy.jordylab.mcp.service.PublicClientRefreshTokenGenerator;
import dev.jordy.jordylab.mcp.service.McpLoginEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.time.Clock;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * The connector's OAuth 2.1 authorization server (research R2 to R6, contracts/oauth-authorization-server.md). It is part of
 * the backend because spec 019 removed Keycloak: Claude signs in with the admin's JordyLab account through the existing
 * session login, one pre-registered public client, PKCE S256 and no secret.
 *
 * <p>Access tokens are JWTs signed by this server ({@code aud=jordylab-mcp}, {@code sub}=account UUID, {@code roles}), and
 * they live only across the authorization code and refresh grants. Authorizations and consents are stored in the
 * {@code mcp} schema so a deploy does not sign Claude out; the access-token key is generated at start-up, so a deploy
 * invalidates current access tokens and the stored refresh token renews them (T078 option A).
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpAuthorizationServerConfiguration {

    static final String CLIENT_ID = "jordylab-mcp-connector";
    static final String REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    static final String SCOPE = "jordylab.mcp";
    static final String SCHEMA = "mcp";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationServerSettings authorizationServerSettings,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            McpAuthorizationRequestValidator authorizationRequestValidator) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher metadata = new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/.well-known/oauth-authorization-server"),
                PathPatternRequestMatcher.pathPattern("/.well-known/oauth-authorization-server/**"));
        http.securityMatcher(new OrRequestMatcher(authorizationServer.getEndpointsMatcher(), metadata))
                .with(authorizationServer, server -> server
                        .registeredClientRepository(registeredClientRepository)
                        .authorizationService(authorizationService)
                        .authorizationConsentService(authorizationConsentService)
                        .authorizationServerSettings(authorizationServerSettings)
                        .tokenGenerator(tokenGenerator)
                        .clientAuthentication(client -> client
                                .authenticationConverters(converters -> converters.add(0,
                                        new McpRefreshClientAuthenticationConverter()))
                                .authenticationProviders(providers -> providers.add(0,
                                        new McpRefreshClientAuthenticationProvider(registeredClientRepository))))
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers
                                .forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequest) {
                                        codeRequest.setAuthenticationValidator(authorizationRequestValidator);
                                    }
                                }))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new McpLoginEntryPoint(), browserRequests()))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(McpProperties mcpProperties) {
        RegisteredClient connector = RegisteredClient.withId(CLIENT_ID)
                .clientId(CLIENT_ID)
                .clientName("Claude MCP connector")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI)
                .scope(SCOPE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(mcpProperties.accessTokenTtl())
                        .refreshTokenTimeToLive(mcpProperties.refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(connector);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(McpProperties mcpProperties) {
        return AuthorizationServerSettings.builder()
                .issuer(mcpProperties.publicBaseUrl())
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * The connector needs a refresh token even though its client is public (Claude holds no secret); the generator is
     * declared here so the refresh token is always part of the code exchange, not left to the configurer's default.
     */
    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JwtEncoder jwtEncoder,
            OAuth2TokenCustomizer<JwtEncodingContext> connectorTokenCustomizer, Clock clock) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(connectorTokenCustomizer);

        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator(), new PublicClientRefreshTokenGenerator(clock));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, McpProperties mcpProperties) {
        NimbusJwtDecoder decoder =
                (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(mcpProperties.publicBaseUrl()),
                new McpAudienceValidator(mcpProperties.audience())));

        return decoder;
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(DataSource dataSource,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOperations jdbcOperations = new JdbcTemplate(new SchemaDataSource(dataSource, SCHEMA));
        JdbcOAuth2AuthorizationService jdbcAuthorizationService =
                new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);

        return new McpAuthorizationService(jdbcAuthorizationService);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(DataSource dataSource,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOperations jdbcOperations = new JdbcTemplate(new SchemaDataSource(dataSource, SCHEMA));

        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    /**
     * The account claims every access token carries (contracts/oauth-authorization-server.md). The authorization request
     * already refused guests, so the role is {@code admin}; the claim is still written from the account so the chain's
     * {@code hasRole("admin")} is a real second check.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> connectorTokenCustomizer(McpProperties mcpProperties) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            Authentication principal = context.getPrincipal();
            Object accountId = principal == null ? null : principal.getPrincipal();
            if (!(accountId instanceof String id) || id.isBlank()) {
                throw new IllegalStateException("A connector access token needs a JordyLab account");
            }
            boolean admin = principal.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority()));
            context.getClaims().subject(id);
            context.getClaims().audience(new java.util.ArrayList<>(List.of(mcpProperties.audience())));
            context.getClaims().claim("roles",
                    new java.util.ArrayList<>(admin ? List.of("admin") : List.of("guest")));
        };
    }

    /** A browser sends {@code text/html}; API clients send json or nothing, and must get the protocol's 401. */
    private static RequestMatcher browserRequests() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(java.util.Set.of(MediaType.ALL));

        return matcher;
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);

            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate the connector's signing key", exception);
        }
    }

    /**
     * Spring Authorization Server's JDBC services use unqualified table names, and the three other modules' queries expect
     * the default schema. This wrapper sets {@code search_path} for the authorization service's own connections only, and
     * restores it when the connection is released, so the schema ownership rule holds without touching the shared pool.
     */
    static final class SchemaDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {

        private final DataSource delegate;
        private final String schema;

        SchemaDataSource(DataSource delegate, String schema) {
            this.delegate = delegate;
            this.schema = schema;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return withSchema(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return withSchema(delegate.getConnection(username, password));
        }

        private Connection withSchema(Connection connection) throws SQLException {
            String previousSchema = connection.getSchema();
            connection.setSchema(schema);

            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if ("close".equals(method.getName())) {
                                connection.setSchema(previousSchema == null ? "public" : previousSchema);
                            }
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
