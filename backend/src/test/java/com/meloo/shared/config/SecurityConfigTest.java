package com.meloo.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

  @Test
  void corsUsesConfiguredAllowedOrigins() {
    SecurityConfig securityConfig = new SecurityConfig();
    CorsConfigurationSource source =
        securityConfig.corsConfigurationSource(
            new CorsProperties(List.of("https://app.meloo.example")));

    CorsConfiguration configuration =
        source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/me"));

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.meloo.example");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }
}
