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

class ItunesMusicProviderTest {

  private WireMockServer wireMockServer;
  private ItunesMusicProvider provider;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();

    var properties =
        new CatalogProviderProperties(
            "itunes",
            new CatalogProviderProperties.DeezerProperties(
                "https://api.deezer.com",
                Duration.ofSeconds(5),
                3,
                Duration.ofMillis(500),
                new CatalogProviderProperties.CircuitBreakerProperties(
                    10, 50.0f, Duration.ofSeconds(10))),
            new CatalogProviderProperties.ItunesProperties(
                true,
                wireMockServer.baseUrl(),
                "VN",
                Duration.ofMillis(300),
                3,
                Duration.ofMillis(20),
                new CatalogProviderProperties.CircuitBreakerProperties(
                    2, 50.0f, Duration.ofSeconds(5))),
            new CatalogProviderProperties.FakeProperties("classpath:fake-media/"),
            new CatalogProviderProperties.CacheProperties(true, Duration.ofMinutes(10), 1000));

    provider = new ItunesMusicProvider(properties, RestClient.builder());
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  @DisplayName("search successfully returns parsed ProviderTracks with coverUrl")
  void search_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=Adele&media=music&entity=song&country=VN&limit=1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 1,
                          "results": [
                            {
                              "wrapperType": "track",
                              "kind": "song",
                              "trackId": 1544494392,
                              "trackName": "Hello",
                              "artistId": 262836961,
                              "artistName": "Adele",
                              "collectionName": "25",
                              "previewUrl": "https://audio-ssl.itunes.apple.com/preview.m4a",
                              "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/100x100bb.jpg",
                              "trackTimeMillis": 295502,
                              "releaseDate": "2015-10-23T07:00:00Z",
                              "country": "VNM"
                            }
                          ]
                        }
                        """)));

    List<ProviderTrack> result = provider.search("Adele", 1);

    assertThat(result).hasSize(1);
    ProviderTrack track = result.getFirst();
    assertThat(track.providerTrackId()).isEqualTo("1544494392");
    assertThat(track.title()).isEqualTo("Hello");
    assertThat(track.artistName()).isEqualTo("Adele");
    assertThat(track.artistId()).isEqualTo("262836961");
    assertThat(track.albumTitle()).isEqualTo("25");
    assertThat(track.durationSeconds()).isEqualTo(295);
    assertThat(track.previewUrl()).isEqualTo("https://audio-ssl.itunes.apple.com/preview.m4a");
    assertThat(track.coverUrl())
        .isEqualTo("https://is1-ssl.mzstatic.com/image/thumb/100x100bb.jpg");
    assertThat(track.releaseYear()).isEqualTo((short) 2015);
    assertThat(track.countryCode()).isEqualTo("VNM");
  }

  @Test
  @DisplayName("search with missing artwork returns track with empty coverUrl")
  void search_missingArtwork_returnsEmptyCoverUrl() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=NoArt&media=music&entity=song&country=VN&limit=1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 1,
                          "results": [
                            {
                              "wrapperType": "track",
                              "kind": "song",
                              "trackId": 777777,
                              "trackName": "No Art Song",
                              "artistName": "Indie Band",
                              "artworkUrl100": null,
                              "artworkUrl60": null
                            }
                          ]
                        }
                        """)));

    List<ProviderTrack> result = provider.search("NoArt", 1);

    assertThat(result).hasSize(1);
    ProviderTrack track = result.getFirst();
    assertThat(track.providerTrackId()).isEqualTo("777777");
    assertThat(track.title()).isEqualTo("No Art Song");
    assertThat(track.coverUrl()).isNull();
  }

  @Test
  @DisplayName("getTrack returns track by ID")
  void getTrack_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/lookup?id=1544494392&entity=song"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 1,
                          "results": [
                            {
                              "wrapperType": "track",
                              "kind": "song",
                              "trackId": 1544494392,
                              "trackName": "Hello",
                              "artistName": "Adele",
                              "artworkUrl100": "https://is1-ssl.mzstatic.com/cover.jpg"
                            }
                          ]
                        }
                        """)));

    Optional<ProviderTrack> trackOpt = provider.getTrack("1544494392");

    assertThat(trackOpt).isPresent();
    ProviderTrack track = trackOpt.get();
    assertThat(track.providerTrackId()).isEqualTo("1544494392");
    assertThat(track.title()).isEqualTo("Hello");
    assertThat(track.coverUrl()).isEqualTo("https://is1-ssl.mzstatic.com/cover.jpg");
  }

  @Test
  @DisplayName("getTrack returns empty Optional when track not found")
  void getTrack_notFound() {
    wireMockServer.stubFor(
        get(urlEqualTo("/lookup?id=000000&entity=song"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 0,
                          "results": []
                        }
                        """)));

    Optional<ProviderTrack> trackOpt = provider.getTrack("000000");
    assertThat(trackOpt).isEmpty();
  }

  @Test
  @DisplayName("chart returns top tracks")
  void chart_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=Top%20Hits&media=music&entity=song&country=VN&limit=2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 2,
                          "results": [
                            { "wrapperType": "track", "trackId": 1, "trackName": "Hit 1" },
                            { "wrapperType": "track", "trackId": 2, "trackName": "Hit 2" }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.chart(2);
    assertThat(tracks).hasSize(2);
    assertThat(tracks.get(0).title()).isEqualTo("Hit 1");
    assertThat(tracks.get(1).title()).isEqualTo("Hit 2");
  }

  @Test
  @DisplayName("playlist returns tracks for collection lookup")
  void playlist_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/lookup?id=col-123&entity=song&limit=5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 1,
                          "results": [
                            { "wrapperType": "track", "trackId": 11, "trackName": "Album Track" }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.playlist("col-123", 5);
    assertThat(tracks).hasSize(1);
    assertThat(tracks.getFirst().title()).isEqualTo("Album Track");
  }

  @Test
  @DisplayName("artistTopTracks returns tracks filtering out artist wrapper")
  void artistTopTracks_success() {
    wireMockServer.stubFor(
        get(urlEqualTo("/lookup?id=art-999&entity=song&limit=5"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "resultCount": 2,
                          "results": [
                            { "wrapperType": "artist", "artistId": 999, "artistName": "Star" },
                            { "wrapperType": "track", "trackId": 55, "trackName": "Star Song" }
                          ]
                        }
                        """)));

    List<ProviderTrack> tracks = provider.artistTopTracks("art-999", 5);
    assertThat(tracks).hasSize(1);
    assertThat(tracks.getFirst().title()).isEqualTo("Star Song");
  }

  @Test
  @DisplayName("server 5xx errors trigger retries and finally throw MusicProviderException")
  void server5xx_triggersRetryAndThrows() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=Error500&media=music&entity=song&country=VN&limit=1"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    assertThatThrownBy(() -> provider.search("Error500", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("iTunes API error: 500");

    wireMockServer.verify(
        3,
        getRequestedFor(
            urlEqualTo("/search?term=Error500&media=music&entity=song&country=VN&limit=1")));
  }

  @Test
  @DisplayName("rate limit (HTTP 429) throws MusicProviderRateLimitException")
  void rateLimit429_throwsMusicProviderRateLimitException() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=RateLimit&media=music&entity=song&country=VN&limit=1"))
            .willReturn(aResponse().withStatus(429).withBody("Too Many Requests")));

    assertThatThrownBy(() -> provider.search("RateLimit", 1))
        .isInstanceOf(MusicProviderRateLimitException.class)
        .hasMessageContaining("HTTP 429");
  }

  @Test
  @DisplayName("timeout triggers retries and throws MusicProviderException")
  void timeout_triggersRetryAndThrows() {
    wireMockServer.stubFor(
        get(urlEqualTo("/search?term=Slow&media=music&entity=song&country=VN&limit=1"))
            .willReturn(
                aResponse()
                    .withFixedDelay(600)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"results\":[]}")));

    assertThatThrownBy(() -> provider.search("Slow", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("timed out");
  }

  @Test
  @DisplayName("circuit breaker opens after failure threshold and fails fast")
  void circuitBreaker_opensAndFailsFast() {
    wireMockServer.stubFor(
        get(urlMatching("/search\\?term=Fail.*")).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> provider.search("Fail1", 1))
        .isInstanceOf(MusicProviderException.class);
    assertThatThrownBy(() -> provider.search("Fail2", 1))
        .isInstanceOf(MusicProviderException.class);

    assertThat(provider.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> provider.search("Fail3", 1))
        .isInstanceOf(MusicProviderException.class)
        .hasMessageContaining("circuit breaker is open");
  }
}
