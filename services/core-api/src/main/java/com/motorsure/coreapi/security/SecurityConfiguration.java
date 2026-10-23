package com.motorsure.coreapi.security;

import com.motorsure.coreapi.common.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtSecurityProperties.class, CorsProperties.class})
public class SecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtSecurityProperties properties,
                                                SecurityProblemWriter problemWriter) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                problemWriter.write(request, response, HttpStatus.UNAUTHORIZED);
        AccessDeniedHandler deniedHandler = (request, response, exception) ->
                problemWriter.write(request, response, HttpStatus.FORBIDDEN);

        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers", "/api/v1/customers/*")
                            .hasAuthority("SCOPE_customers:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers")
                            .hasAuthority("SCOPE_customers:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/*")
                            .hasAuthority("SCOPE_customers:write")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/customers/*/status")
                            .hasAuthority("SCOPE_customers:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/available",
                                "/api/v1/products/*", "/api/v1/products/*/versions", "/api/v1/products/*/versions/*")
                            .hasAuthority("SCOPE_products:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/products/*/versions",
                                "/api/v1/products/*/versions/*/activate", "/api/v1/products/*/versions/*/deactivate",
                                "/api/v1/products/*/deactivate")
                            .hasAuthority("SCOPE_products:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*", "/api/v1/products/*/versions/*")
                            .hasAuthority("SCOPE_products:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/quotes", "/api/v1/quotes/*")
                            .hasAuthority("SCOPE_quotes:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/quotes", "/api/v1/quotes/*/submit", "/api/v1/quotes/*/cancel")
                            .hasAuthority("SCOPE_quotes:write")
                        .requestMatchers("/actuator/info").hasAuthority("SCOPE_core:ops")
                        .anyRequest().denyAll());

        if (properties.isConfigured()) {
            http.oauth2ResourceServer(resource -> resource
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        } else {
            // A missing identity-provider configuration never enables anonymous access to business APIs.
            log.warn("JWT issuer/audience are not configured; protected Core API routes will reject all requests");
            http.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint));
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER_NAME));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER_NAME));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Profile("!test")
    @Conditional(JwtConfiguredCondition.class)
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder).setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuerUri()), audienceValidator));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope");
        scopes.setAuthorityPrefix("SCOPE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }
}
