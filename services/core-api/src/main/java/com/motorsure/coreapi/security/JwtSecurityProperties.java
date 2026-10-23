package com.motorsure.coreapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtSecurityProperties(String issuerUri, String audience) {
    public boolean isConfigured() {
        return issuerUri != null && !issuerUri.isBlank() && audience != null && !audience.isBlank();
    }
}
