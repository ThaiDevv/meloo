package com.meloo.catalog.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class ItunesMusicProvider implements MusicProvider {

  public static final String PROVIDER_NAME = "ITUNES";

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;
  private final String storefront;

  public ItunesMusicProvider(
      CatalogProviderProperties properties,
      RestClient.Builder restClientBuilder,
      @Nullable CircuitBreakerRegistry circuitBreakerRegistry,
      @Nullable RetryRegistry retryRegistry) {

    var timeout = properties.itunes().timeout();
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);

    this.restClient =
        restClientBuilder
            .baseUrl(properties.itunes().baseUrl())
            .requestFactory(requestFactory)
            .build();
    this.storefront = properties.itunes().storefront();

    var retryConfig =
        RetryConfig.custom()
            .maxAttempts(properties.itunes().maxAttempts())
            .waitDuration(properties.itunes().backoff())
            .retryOnException(ItunesMusicProvider::shouldRetry)
            .build();

    var cbProperties = properties.itunes().circuitBreaker();
    var cbConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(cbProperties.slidingWindowSize())
            .failureRateThreshold(cbProperties.failureRateThreshold())
            .waitDurationInOpenState(cbProperties.waitDurationInOpenState())
            .recordException(ItunesMusicProvider::shouldRecordFailure)
            .ignoreExceptions(HttpClientErrorException.NotFound.class)
            .build();

    this.retry =
        retryRegistry != null
            ? retryRegistry.retry("itunes", retryConfig)
            : Retry.of("itunes", retryConfig);

    this.circuitBreaker =
        circuitBreakerRegistry != null
            ? circuitBreakerRegistry.circuitBreaker("itunes", cbConfig)
            : CircuitBreaker.of("itunes", cbConfig);
  }

  public ItunesMusicProvider(
      CatalogProviderProperties properties, RestClient.Builder restClientBuilder) {
    this(properties, restClientBuilder, null, null);
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public List<ProviderTrack> search(String query, int limit) {
    return execute(
        () -> {
          ItunesResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/search")
                              .queryParam("term", query)
                              .queryParam("media", "music")
                              .queryParam("entity", "song")
                              .queryParam("country", storefront)
                              .queryParam("limit", limit)
                              .build())
                  .retrieve()
                  .body(ItunesResponse.class);

          if (response == null || response.results() == null) {
            return List.of();
          }
          return response.results().stream()
              .filter(this::isSongTrack)
              .map(this::toProviderTrack)
              .toList();
        });
  }

  @Override
  public Optional<ProviderTrack> getTrack(String providerTrackId) {
    return execute(
        () -> {
          try {
            ItunesResponse response =
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/lookup")
                                .queryParam("id", providerTrackId)
                                .queryParam("entity", "song")
                                .build())
                    .retrieve()
                    .body(ItunesResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
              return Optional.empty();
            }
            return response.results().stream()
                .filter(this::isSongTrack)
                .map(this::toProviderTrack)
                .findFirst();
          } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
          }
        });
  }

  @Override
  public List<ProviderTrack> chart(int limit) {
    return execute(
        () -> {
          ItunesResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/search")
                              .queryParam("term", "Top Hits")
                              .queryParam("media", "music")
                              .queryParam("entity", "song")
                              .queryParam("country", storefront)
                              .queryParam("limit", limit)
                              .build())
                  .retrieve()
                  .body(ItunesResponse.class);

          if (response == null || response.results() == null) {
            return List.of();
          }
          return response.results().stream()
              .filter(this::isSongTrack)
              .map(this::toProviderTrack)
              .toList();
        });
  }

  @Override
  public List<ProviderTrack> playlist(String playlistId, int limit) {
    return execute(
        () -> {
          ItunesResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/lookup")
                              .queryParam("id", playlistId)
                              .queryParam("entity", "song")
                              .queryParam("limit", limit)
                              .build())
                  .retrieve()
                  .body(ItunesResponse.class);

          if (response == null || response.results() == null) {
            return List.of();
          }
          return response.results().stream()
              .filter(this::isSongTrack)
              .map(this::toProviderTrack)
              .toList();
        });
  }

  @Override
  public List<ProviderTrack> artistTopTracks(String artistId, int limit) {
    return execute(
        () -> {
          ItunesResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/lookup")
                              .queryParam("id", artistId)
                              .queryParam("entity", "song")
                              .queryParam("limit", limit)
                              .build())
                  .retrieve()
                  .body(ItunesResponse.class);

          if (response == null || response.results() == null) {
            return List.of();
          }
          return response.results().stream()
              .filter(this::isSongTrack)
              .map(this::toProviderTrack)
              .toList();
        });
  }

  public CircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }

  public Retry getRetry() {
    return retry;
  }

  private <T> T execute(Supplier<T> supplier) {
    try {
      return circuitBreaker.executeSupplier(Retry.decorateSupplier(retry, supplier));
    } catch (CallNotPermittedException ex) {
      throw new MusicProviderException("iTunes circuit breaker is open: " + ex.getMessage(), ex);
    } catch (HttpClientErrorException.TooManyRequests ex) {
      throw new MusicProviderRateLimitException("iTunes rate limit exceeded (HTTP 429)", ex);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 429) {
        throw new MusicProviderRateLimitException("iTunes rate limit exceeded (HTTP 429)", ex);
      }
      throw new MusicProviderException(
          "iTunes API error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
    } catch (ResourceAccessException ex) {
      throw new MusicProviderException(
          "iTunes request failed or timed out: " + ex.getMessage(), ex);
    } catch (MusicProviderException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new MusicProviderException("iTunes call failed: " + ex.getMessage(), ex);
    }
  }

  private static boolean shouldRetry(Throwable throwable) {
    if (throwable instanceof ResourceAccessException || throwable instanceof IOException) {
      return true;
    }
    if (throwable instanceof HttpServerErrorException) {
      return true;
    }
    if (throwable instanceof RestClientResponseException resEx) {
      return resEx.getStatusCode().is5xxServerError();
    }
    return false;
  }

  private static boolean shouldRecordFailure(Throwable throwable) {
    if (throwable instanceof ResourceAccessException || throwable instanceof IOException) {
      return true;
    }
    if (throwable instanceof HttpServerErrorException) {
      return true;
    }
    if (throwable instanceof RestClientResponseException resEx) {
      return resEx.getStatusCode().is5xxServerError();
    }
    return false;
  }

  private boolean isSongTrack(ItunesTrackItem item) {
    if (item.trackId() == null) {
      return false;
    }
    return item.wrapperType() == null || "track".equalsIgnoreCase(item.wrapperType());
  }

  private ProviderTrack toProviderTrack(ItunesTrackItem item) {
    String coverUrl = null;
    if (item.artworkUrl100() != null && !item.artworkUrl100().isBlank()) {
      coverUrl = item.artworkUrl100();
    } else if (item.artworkUrl60() != null && !item.artworkUrl60().isBlank()) {
      coverUrl = item.artworkUrl60();
    }

    Integer durationSeconds = null;
    if (item.trackTimeMillis() != null) {
      durationSeconds = item.trackTimeMillis() / 1000;
    }

    Short releaseYear = null;
    if (item.releaseDate() != null && item.releaseDate().length() >= 4) {
      try {
        releaseYear = Short.parseShort(item.releaseDate().substring(0, 4));
      } catch (NumberFormatException ignored) {
      }
    }

    return ProviderTrack.builder()
        .providerTrackId(String.valueOf(item.trackId()))
        .title(item.trackName() != null ? item.trackName() : "")
        .artistName(item.artistName() != null ? item.artistName() : "Unknown")
        .artistId(item.artistId() != null ? String.valueOf(item.artistId()) : null)
        .albumTitle(item.collectionName())
        .durationSeconds(durationSeconds)
        .previewUrl(
            item.previewUrl() != null && !item.previewUrl().isBlank() ? item.previewUrl() : null)
        .coverUrl(coverUrl)
        .popularityRank(null)
        .releaseYear(releaseYear)
        .countryCode(item.country())
        .build();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ItunesResponse(@Nullable Integer resultCount, @Nullable List<ItunesTrackItem> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ItunesTrackItem(
      @Nullable String wrapperType,
      @Nullable String kind,
      @Nullable Long trackId,
      @Nullable String trackName,
      @Nullable String trackCensoredName,
      @Nullable Long artistId,
      @Nullable String artistName,
      @Nullable String collectionName,
      @Nullable String previewUrl,
      @Nullable String artworkUrl100,
      @Nullable String artworkUrl60,
      @Nullable Integer trackTimeMillis,
      @Nullable String releaseDate,
      @Nullable String country) {}
}
