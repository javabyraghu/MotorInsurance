package com.motorsure.coreapi.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class JwtConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String issuer = context.getEnvironment().getProperty("app.security.jwt.issuer-uri", "");
        String audience = context.getEnvironment().getProperty("app.security.jwt.audience", "");
        return !issuer.isBlank() && !audience.isBlank();
    }
}
