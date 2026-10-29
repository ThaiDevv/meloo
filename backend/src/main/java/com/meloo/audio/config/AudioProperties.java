package com.meloo.audio.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meloo.audio")
public record AudioProperties(
    @DefaultValue("change-me-to-a-very-secure-random-secret-key-at-least-256-bits")
        String hmacSecret,
    @DefaultValue("2m") Duration tokenTtl,
    @DefaultValue("15m") Duration previewCacheTtl,
    @DefaultValue("1000") long previewCacheMaxSize,
    @DefaultValue RateLimitProperties rateLimit,
    @DefaultValue ProviderProperties provider) {

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
