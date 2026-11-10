package com.meloo.catalog.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DeezerMusicProviderTest {

  private WireMockServer wireMockServer;
  private DeezerMusicProvider provider;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();

    var properties =
        new CatalogProviderProperties(
            "deezer",
            new CatalogProviderProperties.DeezerProperties(
                wireMockServer.baseUrl(),
                Duration.ofMillis(300),
                3,
                Duration.ofMillis(20),
                new CatalogProviderProperties.CircuitBreakerProperties(
                    2, 50.0f, Duration.ofSeconds(5))),
            new CatalogProviderProperties.ItunesProperties(
                false,
                "https://itunes.apple.com",
                "VN",
                Duration.ofSeconds(5),
                3,
                Duration.ofMillis(500),
                new CatalogProviderProperties.CircuitBreakerProperties(
                    10, 50.0f, Duration.ofSeconds(10))),
            new CatalogProviderProperties.FakeProperties("classpath:fake-media/"),
            new CatalogProviderProperties.CacheProperties(true, Duration.ofMinutes(10), 1000));

    provider = new DeezerMusicProvider(properties, RestClient.builder());
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void normalizesPopularityToOneHundredWithUnknownAndOverflowSafeBoundaries() {
    assertThat(DeezerMusicProvider.normalizePopularity(null)).isNull();
    assertThat(DeezerMusicProvider.normalizePopularity(0)).isNull();
    assertThat(DeezerMusicProvider.normalizePopularity(-1)).isNull();
    assertThat(DeezerMusicProvider.normalizePopularity(1)).isEqualTo(1);
    assertThat(DeezerMusicProvider.normalizePopularity(10000)).isEqualTo(1);
    assertThat(DeezerMusicProvider.normalizePopularity(10001)).isEqualTo(2);
    assertThat(DeezerMusicProvider.normalizePopularity(896464)).isEqualTo(90);
    assertThat(DeezerMusicProvider.normalizePopularity(Integer.MAX_VALUE)).isEqualTo(100);
  }

  @Test
  @DisplayName("search successfully returns parsed ProviderTracks with coverUrl")
  void search_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=Adele&limit=1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": 138545995,
                              "title": "Hello (Single)",
                              "title_short": "Hello",
                              "duration": 295,
                              "rank": 896464,
                              "preview": "https://cdnt-preview.dzcdn.net/preview.mp3?hdnea=exp=1792840565",
                              "artist": {
                                "id": 75798,
                                "name": "Adele"
                              },
                              "album": {
                                "id": 14880539,
                                "title": "25",
                                "cover_big": "https://cdn-images.dzcdn.net/images/cover/eaeadce7932a97533fe495881d2fcd7a/500x500.jpg"
                              }
                            }
                          ],
                          "total": 1
                        }
                        """)));

    List<ProviderTrack> result = provider.search("Adele", 1);

    assertThat(result).hasSize(1);
    ProviderTrack track = result.getFirst();
    assertThat(track.providerTrackId()).isEqualTo("138545995");
    assertThat(track.title()).isEqualTo("Hello");
    assertThat(track.artistName()).isEqualTo("Adele");
    assertThat(track.artistId()).isEqualTo("75798");
    assertThat(track.albumTitle()).isEqualTo("25");
    assertThat(track.durationSeconds()).isEqualTo(295);
    assertThat(track.previewUrl())
        .isEqualTo("https://cdnt-preview.dzcdn.net/preview.mp3?hdnea=exp=1792840565");
    assertThat(track.coverUrl())
        .isEqualTo(
            "https://cdn-images.dzcdn.net/images/cover/eaeadce7932a97533fe495881d2fcd7a/500x500.jpg");
    assertThat(track.popularityRank()).isEqualTo(90);
  }

  @Test
  @DisplayName("search with missing album cover returns track with empty coverUrl")
  void search_missingCover_returnsEmptyCoverUrl() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=Unknown&limit=1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": 999999,
                              "title": "Acapella Track",
                              "title_short": "Acapella Track",
                              "duration": 120,
                              "artist": {
                                "id": 100,
                                "name": "Solo Singer"
                              },
                              "album": null
                            }
                          ],
                          "total": 1
                        }
                        """)));

    List<ProviderTrack> result = provider.search("Unknown", 1);

    assertThat(result).hasSize(1);
    ProviderTrack track = result.getFirst();
    assertThat(track.providerTrackId()).isEqualTo("999999");
    assertThat(track.title()).isEqualTo("Acapella Track");
    assertThat(track.coverUrl()).isNull();
  }

  @Test
  @DisplayName("getTrack returns track with releaseYear and coverUrl")
  void getTrack_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/track/138545995"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": 138545995,
                          "title": "Hello",
                          "title_short": "Hello",
                          "duration": 295,
                          "rank": 896464,
                          "preview": "https://cdnt-preview.dzcdn.net/preview.mp3",
                          "release_date": "2015-10-23",
                          "artist": {
                            "id": 75798,
                            "name": "Adele"
                          },
                          "album": {
                            "id": 14880539,
                            "title": "25",
                            "cover_big": "https://cdn-images.dzcdn.net/images/cover/500x500.jpg"
                          }
                        }
                        """)));

    Optional<ProviderTrack> trackOpt = provider.getTrack("138545995");

    assertThat(trackOpt).isPresent();
    ProviderTrack track = trackOpt.get();
    assertThat(track.providerTrackId()).isEqualTo("138545995");
    assertThat(track.title()).isEqualTo("Hello");
    assertThat(track.releaseYear()).isEqualTo((short) 2015);
    assertThat(track.coverUrl()).isEqualTo("https://cdn-images.dzcdn.net/images/cover/500x500.jpg");
  }

  @Test
  @DisplayName("getTrack returns empty Optional when track not found (HTTP 404)")
  void getTrack_notFoundHttp404() {
    wireMockServer.stubFor(
        get(urlEqualTo("/track/not-found")).willReturn(aResponse().withStatus(404)));

    Optional<ProviderTrack> trackOpt = provider.getTrack("not-found");
    assertThat(trackOpt).isEmpty();
  }

  @Test
  @DisplayName("getTrack returns empty Optional when Deezer returns 200 with DataException error")
  void getTrack_deezerDataException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/track/888888"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "type": "DataException",
                            "message": "no data",
                            "code": 800
                          }
                        }
                        """)));

    Optional<ProviderTrack> trackOpt = provider.getTrack("888888");
    assertThat(trackOpt).isEmpty();
  }

  @Test
  @DisplayName("chart successfully returns top tracks")
  void chart_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/chart/0/tracks?limit=2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": 1,
                              "title": "Song 1",
                              "artist": { "id": 10, "name": "Artist 1" }
                            },
                            {
                              "id": 2,
                              "title": "Song 2",
                              "artist": { "id": 20, "name": "Artist 2" }
                            }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.chart(2);
    assertThat(tracks).hasSize(2);
    assertThat(tracks.get(0).title()).isEqualTo("Song 1");
    assertThat(tracks.get(1).title()).isEqualTo("Song 2");
  }

  @Test
  @DisplayName("playlist successfully returns tracks")
  void playlist_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/playlist/908622995/tracks?limit=5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": 101,
                              "title": "Playlist Song",
                              "artist": { "id": 1, "name": "Singer" }
                            }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.playlist("908622995", 5);
    assertThat(tracks).hasSize(1);
    assertThat(tracks.getFirst().title()).isEqualTo("Playlist Song");
  }

  @Test
  @DisplayName("artistTopTracks successfully returns top tracks for artist")
  void artistTopTracks_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/artist/75798/top?limit=3"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "data": [
                            {
                              "id": 138545995,
                              "title": "Hello",
                              "artist": { "id": 75798, "name": "Adele" }
                            }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.artistTopTracks("75798", 3);
    assertThat(tracks).hasSize(1);
    assertThat(tracks.getFirst().artistName()).isEqualTo("Adele");
  }

  @Test
  @DisplayName("server 5xx errors trigger retries and finally throw MusicProviderException")
  void server5xx_triggersRetryAndThrows() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=Error500&limit=1"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    assertThatThrownBy(() -> provider.search("Error500", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("Deezer API error: 500");

    wireMockServer.verify(3, getRequestedFor(urlEqualTo("/search?q=Error500&limit=1")));
  }

  @Test
  @DisplayName("rate limit (HTTP 429) throws MusicProviderRateLimitException")
  void rateLimit429_throwsMusicProviderRateLimitException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=RateLimit&limit=1"))
            .willReturn(aResponse().withStatus(429).withBody("Too Many Requests")));

    assertThatThrownBy(() -> provider.search("RateLimit", 1))
        .isInstanceOf(MusicProviderRateLimitException.class)
        .hasMessageContaining("HTTP 429");
  }

  @Test
  @DisplayName("quota limit (Deezer error code 4) in search throws MusicProviderRateLimitException")
  void quotaLimitCode4_searchThrowsMusicProviderRateLimitException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=QuotaSearch&limit=5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "type": "Exception",
                            "message": "Quota limit exceeded",
                            "code": 4
                          }
                        }
                        """)));

    assertThatThrownBy(() -> provider.search("QuotaSearch", 5))
        .isInstanceOf(MusicProviderRateLimitException.class)
        .hasMessageContaining("Deezer quota exceeded");
  }

  @Test
  @DisplayName("quota limit (Deezer error code 4) in chart throws MusicProviderRateLimitException")
  void quotaLimitCode4_chartThrowsMusicProviderRateLimitException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/chart/0/tracks?limit=10"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "type": "Exception",
                            "message": "Quota limit exceeded",
                            "code": 4
                          }
                        }
                        """)));

    assertThatThrownBy(() -> provider.chart(10))
        .isInstanceOf(MusicProviderRateLimitException.class)
        .hasMessageContaining("Deezer quota exceeded");
  }

  @Test
  @DisplayName(
      "quota limit (Deezer error code 4) in getTrack throws MusicProviderRateLimitException")
  void quotaLimitCode4_getTrackThrowsMusicProviderRateLimitException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/track/444444"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "error": {
                            "type": "Exception",
                            "message": "Quota limit exceeded",
                            "code": 4
                          }
                        }
                        """)));

    assertThatThrownBy(() -> provider.getTrack("444444"))
        .isInstanceOf(MusicProviderRateLimitException.class)
        .hasMessageContaining("Deezer quota exceeded");
  }

  @Test
  @DisplayName("timeout triggers retries and throws MusicProviderException")
  void timeout_triggersRetryAndThrows() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?q=Slow&limit=1"))
            .willReturn(
                aResponse()
                    .withFixedDelay(600)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\":[]}")));

    assertThatThrownBy(() -> provider.search("Slow", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("timed out");
  }

  @Test
  @DisplayName("circuit breaker opens after failure threshold and fails fast")
  void circuitBreaker_opensAndFailsFast() {
    wireMockServer.stubFor(
        get(urlMatching("/search\\?q=Fail.*")).willReturn(aResponse().withStatus(503)));

    // slidingWindowSize is 2. Both calls fail.
    assertThatThrownBy(() -> provider.search("Fail1", 1))
        .isInstanceOf(MusicProviderException.class);
    assertThatThrownBy(() -> provider.search("Fail2", 1))
        .isInstanceOf(MusicProviderException.class);

    assertThat(provider.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // Subsequent call fails fast without touching wiremock
    assertThatThrownBy(() -> provider.search("Fail3", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("circuit breaker is open");
  }
}
