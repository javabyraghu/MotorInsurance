package com.motorsure.coreapi.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Fixed, non-production signing fixture used only by offline tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {
    public static final String ISSUER = "https://identity.test.motorsure.invalid";
    public static final String AUDIENCE = "motorsure-core-api-test";

    @Bean
    TestKey testKey() throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                readPem("/security/test-private-key.pem", "PRIVATE KEY")));
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(
                readPem("/security/test-public-key.pem", "PUBLIC KEY")));
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("core-api-test-key").build();
        return new TestKey(jwk);
    }

    private byte[] readPem(String resource, String type) throws Exception {
        try (var stream = TestJwtConfiguration.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing test-only JWT key fixture: " + resource);
            String pem = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII);
            String encoded = pem.replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "").replaceAll("\\s", "");
            return Base64.getDecoder().decode(encoded);
        }
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(TestKey key) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.jwk().toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audience));
        return decoder;
    }

    @Bean
    JwtEncoder testJwtEncoder(TestKey key) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(key.jwk()));
        return new NimbusJwtEncoder(source);
    }

    public record TestKey(RSAKey jwk) { }
}
