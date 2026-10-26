package com.meloo.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "meloo.identity")
@Validated
public record IdentityProperties(
    @NotNull @Valid JwtProperties jwt,
    CookieProperties cookie,
    RateLimitProperties rateLimit,
    List<String> bannedWords) {

  public IdentityProperties {
    if (jwt == null) {
      throw new IllegalArgumentException("meloo.identity.jwt configuration is required");
    }
    if (cookie == null) {
      cookie = new CookieProperties("meloo_refresh", "/api/v1/auth", true, "Lax");
    }
    if (rateLimit == null) {
      rateLimit = new RateLimitProperties(5);
    }
    if (bannedWords == null) {
      bannedWords = List.of();
    }
  }

  public record JwtProperties(
      @NotBlank String secret,
      @DefaultValue("15m") Duration accessTokenTtl,
      @DefaultValue("30d") Duration refreshTokenTtl,
      @DefaultValue("10s") Duration rotationGrace) {

    public JwtProperties {
      if (secret == null || secret.isBlank()) {
        throw new IllegalArgumentException("JWT secret must not be blank");
      }
      if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits)");
      }
      if (accessTokenTtl == null) {
        accessTokenTtl = Duration.ofMinutes(15);
      }
      if (refreshTokenTtl == null) {
        refreshTokenTtl = Duration.ofDays(30);
      }
      if (rotationGrace == null) {
        rotationGrace = Duration.ofSeconds(10);
      }
    }
  }

  public record CookieProperties(
      @DefaultValue("meloo_refresh") String name,
      @DefaultValue("/api/v1/auth") String path,
      @DefaultValue("true") boolean secure,
      @DefaultValue("Lax") String sameSite) {

    public CookieProperties {
      if (name == null || name.isBlank()) {
        name = "meloo_refresh";
      }
      if (path == null || path.isBlank()) {
        path = "/api/v1/auth";
      }
      if (sameSite == null || sameSite.isBlank()) {
        sameSite = "Lax";
      }
    }
  }

  public record RateLimitProperties(@DefaultValue("5") int guestCreatePerHour) {

    public RateLimitProperties {
      if (guestCreatePerHour <= 0) {
        guestCreatePerHour = 5;
      }
    }
  }
}
