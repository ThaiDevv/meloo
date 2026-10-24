package com.meloo.catalog.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meloo.catalog.provider")
public record CatalogProviderProperties(
    @DefaultValue("deezer") String type,
    @DefaultValue DeezerProperties deezer,
    @DefaultValue ItunesProperties itunes,
    @DefaultValue FakeProperties fake,
    @DefaultValue CacheProperties cache) {

  public record DeezerProperties(
      @DefaultValue("https://api.deezer.com") String baseUrl,
      @DefaultValue("5s") Duration timeout,
      @DefaultValue("3") int maxAttempts,
      @DefaultValue("500ms") Duration backoff,
      @DefaultValue CircuitBreakerProperties circuitBreaker) {}

  public record ItunesProperties(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("https://itunes.apple.com") String baseUrl,
      @DefaultValue("VN") String storefront,
      @DefaultValue("5s") Duration timeout,
      @DefaultValue("3") int maxAttempts,
      @DefaultValue("500ms") Duration backoff,
      @DefaultValue CircuitBreakerProperties circuitBreaker) {}

  public record FakeProperties(@DefaultValue("classpath:fake-media/") String mediaLocation) {}

  public record CacheProperties(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("10m") Duration ttl,
      @DefaultValue("1000") long maxSize) {}

  public record CircuitBreakerProperties(
      @DefaultValue("10") int slidingWindowSize,
      @DefaultValue("50.0") float failureRateThreshold,
      @DefaultValue("10s") Duration waitDurationInOpenState) {}
}
