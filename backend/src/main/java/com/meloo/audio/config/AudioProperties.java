package com.meloo.audio.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meloo.audio")
public record AudioProperties(
    @NotBlank(message = "Audio HMAC secret must not be blank") String hmacSecret,
    @DefaultValue("2m") Duration tokenTtl,
    @DefaultValue("15m") Duration previewCacheTtl,
    @DefaultValue("1000") long previewCacheMaxSize,
    @DefaultValue("45m") Duration activeRoundsTtl,
    @DefaultValue("2000") long activeRoundsMaxSize,
    @DefaultValue RateLimitProperties rateLimit,
    @DefaultValue ProviderProperties provider) {

  public AudioProperties {
    if (hmacSecret != null) {
      if (hmacSecret.startsWith("change-me-")) {
        throw new IllegalArgumentException(
            "Audio HMAC secret must not use the placeholder key; set MELOO_AUDIO_HMAC_SECRET in environment");
      }
      if (hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalArgumentException(
            "Audio HMAC secret must be at least 32 bytes (256 bits) for HMAC-SHA256");
      }
    }
    if (tokenTtl != null && tokenTtl.compareTo(Duration.ofMinutes(2)) > 0) {
      throw new IllegalArgumentException("Audio token TTL must not exceed 2 minutes per AC1");
    }
  }

  public record RateLimitProperties(
      @DefaultValue("5") int tokenCapacity,
      @DefaultValue("5") int tokenRefillTokens,
      @DefaultValue("1m") Duration tokenRefillDuration,
      @DefaultValue("30") int playerCapacity,
      @DefaultValue("30") int playerRefillTokens,
      @DefaultValue("1m") Duration playerRefillDuration) {}

  public record ProviderProperties(
      @DefaultValue("deezer") String type,
      @DefaultValue DeezerProperties deezer,
      @DefaultValue FakeProperties fake) {}

  public record DeezerProperties(
      @DefaultValue("https://api.deezer.com") String baseUrl,
      @DefaultValue("5s") Duration timeout,
      @DefaultValue("3") int maxAttempts,
      @DefaultValue("500ms") Duration backoff,
      @DefaultValue CircuitBreakerProperties circuitBreaker) {}

  public record FakeProperties(@DefaultValue("classpath:fake-media/") String mediaLocation) {}

  public record CircuitBreakerProperties(
      @DefaultValue("10") int slidingWindowSize,
      @DefaultValue("50.0") float failureRateThreshold,
      @DefaultValue("10s") Duration waitDurationInOpenState) {}
}
