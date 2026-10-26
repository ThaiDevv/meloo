package com.meloo.catalog.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

public class DeezerMusicProvider implements MusicProvider {

  public static final String PROVIDER_NAME = "DEEZER";

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public DeezerMusicProvider(
      CatalogProviderProperties properties,
      RestClient.Builder restClientBuilder,
      @Nullable CircuitBreakerRegistry circuitBreakerRegistry,
      @Nullable RetryRegistry retryRegistry) {

    var timeout = properties.deezer().timeout();
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);

    this.restClient =
        restClientBuilder
            .clone()
            .baseUrl(properties.deezer().baseUrl())
            .requestFactory(requestFactory)
            .build();

    var retryConfig =
        RetryConfig.custom()
            .maxAttempts(properties.deezer().maxAttempts())
            .waitDuration(properties.deezer().backoff())
            .retryOnException(DeezerMusicProvider::shouldRetry)
            .build();

    var cbProperties = properties.deezer().circuitBreaker();
    var cbConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(cbProperties.slidingWindowSize())
            .failureRateThreshold(cbProperties.failureRateThreshold())
            .waitDurationInOpenState(cbProperties.waitDurationInOpenState())
            .recordException(DeezerMusicProvider::shouldRecordFailure)
            .ignoreExceptions(HttpClientErrorException.NotFound.class)
            .build();

    this.retry =
        retryRegistry != null
            ? retryRegistry.retry("deezer", retryConfig)
            : Retry.of("deezer", retryConfig);

    this.circuitBreaker =
        circuitBreakerRegistry != null
            ? circuitBreakerRegistry.circuitBreaker("deezer", cbConfig)
            : CircuitBreaker.of("deezer", cbConfig);
  }

  public DeezerMusicProvider(
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
          DeezerSearchResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/search")
                              .queryParam("q", query)
                              .queryParam("limit", limit)
                              .build())
                  .retrieve()
                  .body(DeezerSearchResponse.class);

          return mapList(response);
        });
  }

  @Override
  public Optional<ProviderTrack> getTrack(String providerTrackId) {
    return execute(
        () -> {
          try {
            DeezerTrackItem response =
                restClient
                    .get()
                    .uri("/track/{id}", providerTrackId)
                    .retrieve()
                    .body(DeezerTrackItem.class);

            if (response == null) {
              return Optional.empty();
            }
            checkApiError(response.error());
            if (response.id() == null) {
              return Optional.empty();
            }
            return Optional.of(toProviderTrack(response));
          } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
          }
        });
  }

  @Override
  public List<ProviderTrack> chart(int limit) {
    return execute(
        () -> {
          DeezerSearchResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder.path("/chart/0/tracks").queryParam("limit", limit).build())
                  .retrieve()
                  .body(DeezerSearchResponse.class);

          return mapList(response);
        });
  }

  @Override
  public List<ProviderTrack> playlist(String playlistId, int limit) {
    return execute(
        () -> {
          DeezerSearchResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/playlist/{id}/tracks")
                              .queryParam("limit", limit)
                              .build(playlistId))
                  .retrieve()
                  .body(DeezerSearchResponse.class);

          return mapList(response);
        });
  }

  @Override
  public List<ProviderTrack> artistTopTracks(String artistId, int limit) {
    return execute(
        () -> {
          DeezerSearchResponse response =
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/artist/{id}/top")
                              .queryParam("limit", limit)
                              .build(artistId))
                  .retrieve()
                  .body(DeezerSearchResponse.class);

          return mapList(response);
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
      throw new MusicProviderException("Deezer circuit breaker is open: " + ex.getMessage(), ex);
    } catch (HttpClientErrorException.TooManyRequests ex) {
      throw new MusicProviderRateLimitException("Deezer rate limit exceeded (HTTP 429)", ex);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 429) {
        throw new MusicProviderRateLimitException("Deezer rate limit exceeded (HTTP 429)", ex);
      }
      throw new MusicProviderException(
          "Deezer API error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
    } catch (ResourceAccessException ex) {
      throw new MusicProviderException(
          "Deezer request failed or timed out: " + ex.getMessage(), ex);
    } catch (MusicProviderException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new MusicProviderException("Deezer call failed: " + ex.getMessage(), ex);
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

  private List<ProviderTrack> mapList(@Nullable DeezerSearchResponse response) {
    if (response == null) {
      return List.of();
    }
    checkApiError(response.error());
    if (response.data() == null) {
      return List.of();
    }
    return response.data().stream()
        .filter(item -> item.error() == null)
        .map(this::toProviderTrack)
        .toList();
  }

  private void checkApiError(@Nullable DeezerError error) {
    if (error == null) {
      return;
    }
    if (error.code() != null && error.code() == 4) {
      throw new MusicProviderRateLimitException("Deezer quota exceeded: " + error.message());
    }
  }

  private ProviderTrack toProviderTrack(DeezerTrackItem item) {
    String coverUrl = null;
    if (item.album() != null) {
      if (item.album().coverBig() != null && !item.album().coverBig().isBlank()) {
        coverUrl = item.album().coverBig();
      } else if (item.album().coverXl() != null && !item.album().coverXl().isBlank()) {
        coverUrl = item.album().coverXl();
      } else if (item.album().coverMedium() != null && !item.album().coverMedium().isBlank()) {
        coverUrl = item.album().coverMedium();
      } else if (item.album().cover() != null && !item.album().cover().isBlank()) {
        coverUrl = item.album().cover();
      }
    }

    Short releaseYear = null;
    if (item.releaseDate() != null && item.releaseDate().length() >= 4) {
      try {
        releaseYear = Short.parseShort(item.releaseDate().substring(0, 4));
      } catch (NumberFormatException ignored) {
      }
    }

    return ProviderTrack.builder()
        .providerTrackId(String.valueOf(item.id()))
        .title(
            item.titleShort() != null && !item.titleShort().isBlank()
                ? item.titleShort()
                : item.title())
        .artistName(item.artist() != null ? item.artist().name() : "Unknown")
        .artistId(
            item.artist() != null && item.artist().id() != null
                ? String.valueOf(item.artist().id())
                : null)
        .albumTitle(item.album() != null ? item.album().title() : null)
        .durationSeconds(item.duration())
        .previewUrl(item.preview() != null && !item.preview().isBlank() ? item.preview() : null)
        .coverUrl(coverUrl)
        .popularityRank(item.rank())
        .releaseYear(releaseYear)
        .build();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerSearchResponse(
      @Nullable List<DeezerTrackItem> data, @Nullable Integer total, @Nullable DeezerError error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerTrackItem(
      @Nullable Long id,
      @Nullable String title,
      @JsonProperty("title_short") @Nullable String titleShort,
      @Nullable Integer duration,
      @Nullable Integer rank,
      @Nullable String preview,
      @JsonProperty("release_date") @Nullable String releaseDate,
      @Nullable DeezerArtistItem artist,
      @Nullable DeezerAlbumItem album,
      @Nullable DeezerError error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerArtistItem(
      @Nullable Long id,
      @Nullable String name,
      @Nullable String picture,
      @JsonProperty("picture_medium") @Nullable String pictureMedium,
      @JsonProperty("picture_big") @Nullable String pictureBig) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerAlbumItem(
      @Nullable Long id,
      @Nullable String title,
      @Nullable String cover,
      @JsonProperty("cover_small") @Nullable String coverSmall,
      @JsonProperty("cover_medium") @Nullable String coverMedium,
      @JsonProperty("cover_big") @Nullable String coverBig,
      @JsonProperty("cover_xl") @Nullable String coverXl) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeezerError(@Nullable String type, @Nullable String message, @Nullable Integer code) {}
}
