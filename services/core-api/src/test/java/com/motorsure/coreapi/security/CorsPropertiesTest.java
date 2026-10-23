package com.motorsure.coreapi.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {
    @Test
    void defaultsToNoCrossOriginAccessAndNormalizesConfiguredOrigins() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(List.of(" https://app.example.test ", "", "https://app.example.test"))
                .allowedOrigins()).containsExactly("https://app.example.test");
    }

    @Test
    void rejectsWildcardCorsOrigins() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit");
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.test")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
