package com.motorsure.coreapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.type.AnnotationMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfiguredConditionTest {
    private final JwtConfiguredCondition condition = new JwtConfiguredCondition();
    private final AnnotationMetadata metadata = AnnotationMetadata.introspect(JwtSecurityProperties.class);

    @Test
    void requiresBothIssuerAndAudienceBeforeCreatingAProductionDecoder() {
        var environment = new MockEnvironment();
        var context = new Context(environment);
        assertThat(condition.matches(context, metadata)).isFalse();

        environment.setProperty("app.security.jwt.issuer-uri", "https://issuer.example.test");
        assertThat(condition.matches(context, metadata)).isFalse();

        environment.setProperty("app.security.jwt.issuer-uri", "");
        environment.setProperty("app.security.jwt.audience", "motorsure-api");
        assertThat(condition.matches(context, metadata)).isFalse();

        environment.setProperty("app.security.jwt.issuer-uri", "https://issuer.example.test");
        assertThat(condition.matches(context, metadata)).isTrue();
    }

    @Test
    void malformedConfiguredIssuerFailsDecoderCreationInsteadOfProvidingAnUnverifiedDecoder() {
        assertThatThrownBy(() -> new SecurityConfiguration().jwtDecoder(
                new JwtSecurityProperties("file:///not-an-oidc-issuer", "motorsure-api")))
                .isInstanceOf(RuntimeException.class);
    }

    private static final class Context implements org.springframework.context.annotation.ConditionContext {
        private final org.springframework.core.env.Environment environment;
        private Context(org.springframework.core.env.Environment environment) { this.environment = environment; }
        @Override public org.springframework.core.env.Environment getEnvironment() { return environment; }
        @Override public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() { return null; }
        @Override public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getBeanFactory() { return null; }
        @Override public org.springframework.core.io.ResourceLoader getResourceLoader() { return null; }
        @Override public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    }
}
