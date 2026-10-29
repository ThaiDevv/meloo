package com.meloo.audio.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meloo.audio.config.AudioProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class DeezerAudioSourceResolver implements AudioSourceResolver {

  private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceResolver.class);

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public DeezerAudioSourceResolver(
      AudioProperties.DeezerProperties properties,
      RestClient.Builder restClientBuilder,
      @Nullable CircuitBreakerRegistry circuitBreakerRegistry,
      @Nullable RetryRegistry retryRegistry) {

    this.restClient = restClientBuilder.clone().baseUrl(properties.baseUrl()).build();

    var retryConfig =
        RetryConfig.custom()
            .maxAttempts(properties.maxAttempts())
            .waitDuration(properties.backoff())
            .retryOnException(DeezerAudioSourceResolver::shouldRetry)
            .build();

    var cbProperties = properties.circuitBreaker();
    var cbConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(cbProperties.slidingWindowSize())
            .failureRateThreshold(cbProperties.failureRateThreshold())
            .waitDurationInOpenState(cbProperties.waitDurationInOpenState())
            .recordExceptions(
                HttpServerErrorException.class, ResourceAccessException.class, IOException.class)
            .ignoreExceptions(HttpClientErrorException.NotFound.class)
            .build();

    this.circuitBreaker =
        circuitBreakerRegistry != null
            ? circuitBreakerRegistry.circuitBreaker("deezerAudioSourceResolver", cbConfig)
            : CircuitBreaker.of("deezerAudioSourceResolver", cbConfig);

    this.retry =
        retryRegistry != null
            ? retryRegistry.retry("deezerAudioSourceResolver", retryConfig)
            : Retry.of("deezerAudioSourceResolver", retryConfig);
  }

  @Override
  public Optional<String> resolvePreviewUrl(UUID trackId, String provider, String providerTrackId) {
    if (providerTrackId == null || providerTrackId.isBlank()) {
      return Optional.empty();
    }

    try {
      return execute(
          () -> {
            try {
              DeezerTrackResponse response =
                  restClient
                      .get()
                      .uri("/track/{id}", providerTrackId)
                      .retrieve()
                      .body(DeezerTrackResponse.class);

              if (response == null || response.preview() == null || response.preview().isBlank()) {
                return Optional.empty();
              }
              if (response.error() != null) {
                return Optional.empty();
              }
              return Optional.of(response.preview());
            } catch (HttpClientErrorException.NotFound e) {
              return Optional.empty();
            }
          });
    } catch (Exception e) {
      // M1 log hygiene: do not log trackId, providerTrackId or e.getMessage() which may leak
      // provider details
      log.warn("Failed to resolve Deezer preview URL: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private <T> T execute(java.util.function.Supplier<T> supplier) {
    var decorated =
        CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, supplier));
    return decorated.get();
  }

  private static boolean shouldRetry(Throwable throwable) {
    if (throwable instanceof CallNotPermittedException) {
      return false;
    }
    if (throwable instanceof HttpClientErrorException.TooManyRequests) {
      return false;
    }
    if (throwable instanceof HttpClientErrorException.NotFound) {
      return false;
    }
    if (throwable instanceof HttpServerErrorException) {
      return true;
    }
    if (throwable instanceof ResourceAccessException) {
      return true;
    }
    if (throwable instanceof RestClientResponseException rcre) {
      return rcre.getStatusCode().is5xxServerError();
    }
    return throwable instanceof IOException;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerTrackResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("preview") String preview,
      @JsonProperty("error") DeezerError error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerError(
      @JsonProperty("type") String type,
      @JsonProperty("message") String message,
      @JsonProperty("code") Integer code) {}
}
