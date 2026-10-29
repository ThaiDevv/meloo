package com.meloo.audio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioException;
import com.meloo.audio.domain.AudioRangeNotSatisfiableException;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.ListeningLevel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AudioStreamServiceTest {

  private AudioProperties properties;
  private FakeAudioSourceResolver fakeResolver;
  private AudioStreamService streamService;

  @BeforeEach
  void setUp() {
    properties =
        new AudioProperties(
            "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256!",
            Duration.ofMinutes(2),
            Duration.ofMinutes(15),
            1000,
            Duration.ofMinutes(45),
            2000,
            new AudioProperties.RateLimitProperties(
                5, 5, Duration.ofMinutes(1), 30, 30, Duration.ofMinutes(1)),
            new AudioProperties.ProviderProperties(
                "fake",
                new AudioProperties.DeezerProperties(
                    "https://api.deezer.com",
                    Duration.ofSeconds(5),
                    3,
                    Duration.ofMillis(500),
                    new AudioProperties.CircuitBreakerProperties(
                        10, 50.0f, Duration.ofSeconds(10))),
                new AudioProperties.FakeProperties("classpath:fake-media/")));

    fakeResolver = new FakeAudioSourceResolver("classpath:fake-media/");

    streamService =
        new AudioStreamService(
            properties, fakeResolver, new DefaultResourceLoader(), RestClient.builder());
  }

  @Test
  @DisplayName("ListeningLevel byte caps match ADR-008 specifications")
  void listeningLevelByteCaps_matchAdr008Spec() {
    assertThat(ListeningLevel.LEVEL_1.getMaxBytes()).isEqualTo(32 * 1024);
    assertThat(ListeningLevel.LEVEL_2.getMaxBytes()).isEqualTo(80 * 1024);
    assertThat(ListeningLevel.LEVEL_3.getMaxBytes()).isEqualTo(160 * 1024);
    assertThat(ListeningLevel.LEVEL_4.getMaxBytes()).isEqualTo(320 * 1024);
    assertThat(ListeningLevel.LEVEL_5.getMaxBytes()).isEqualTo(480 * 1024);
  }

  @ParameterizedTest(name = "Level {0} caps audio stream to {1} bytes from a 600KB source")
  @CsvSource({"1, 32768", "2, 81920", "3, 163840", "4, 327680", "5, 491520"})
  @DisplayName("M4 & AC3: Server-side slicing caps output to exact bytes across all 5 levels")
  void streamAudioClip_allLevels_enforcesByteCaps(int level, int expectedMaxBytes)
      throws IOException {
    String remoteUrl = "https://cdns-preview.dzcdn.net/stream/600kb-track.mp3";
    byte[] full600KbAudio = new byte[600 * 1024];
    for (int i = 0; i < full600KbAudio.length; i++) {
      full600KbAudio[i] = (byte) (i % 127);
    }

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // HEAD probe returns 600KB
    HttpHeaders headHeaders = new HttpHeaders();
    headHeaders.setContentLength(full600KbAudio.length);
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).headers(headHeaders));

    // GET with Range header returns 206 Partial Content
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.RANGE, "bytes=0-" + (expectedMaxBytes - 1)))
        .andRespond(
            withStatus(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(
                    new InputStreamResource(
                        new ByteArrayInputStream(full600KbAudio, 0, expectedMaxBytes))));

    AudioSourceResolver resolver = (trackId, provider, id) -> Optional.of(remoteUrl);
    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    AudioPreparationResult prep =
        service.prepareRoundAudio(roundId, trackId, "DEEZER", "track-600");
    assertThat(prep.playable()).isTrue();

    AudioToken token = new AudioToken(roundId, playerId, level, Instant.now().plusSeconds(120));
    AudioStreamService.SlicedStreamResult result = service.streamAudioClip(token, null);

    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    assertThat(result.contentLength()).isEqualTo(expectedMaxBytes);

    try (InputStream is = result.resource().getInputStream()) {
      byte[] bytes = is.readAllBytes();
      assertThat(bytes.length).isEqualTo(expectedMaxBytes);
    }
  }

  @Test
  @DisplayName("prepareRoundAudio resolves and verifies reachable fake-media sample")
  void prepareRoundAudio_validFakeSource_returnsSuccess() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    AudioPreparationResult result =
        streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");

    assertThat(result.playable()).isTrue();
    assertThat(streamService.isRoundPlayable(roundId)).isTrue();
  }

  @Test
  @DisplayName("prepareRoundAudio with broken/missing source returns failure (BR-27)")
  void prepareRoundAudio_missingSource_returnsFailure() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    AudioPreparationResult result =
        streamService.prepareRoundAudio(roundId, trackId, "FAKE", "broken-track");

    assertThat(result.playable()).isFalse();
    assertThat(result.failureReason()).contains("could not be resolved");
    assertThat(streamService.isRoundPlayable(roundId)).isFalse();
  }

  @Test
  @DisplayName("streamAudioClip serves at most 32KB for Level 1 (2s) on full stream (HTTP 200)")
  void streamAudioClip_level1_servesAtMost32KB() throws IOException {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));

    AudioStreamService.SlicedStreamResult result = streamService.streamAudioClip(token, null);

    assertThat(result.status()).isEqualTo(HttpStatus.OK);
    assertThat(result.contentRange()).isNull();
    assertThat(result.contentLength()).isEqualTo(32 * 1024);

    try (InputStream is = result.resource().getInputStream()) {
      byte[] bytes = is.readAllBytes();
      assertThat(bytes.length).isEqualTo(32 * 1024);
    }
  }

  @Test
  @DisplayName("streamAudioClip supports HTTP Range request within slice boundary (HTTP 206)")
  void streamAudioClip_validRange_returnsPartialContent206() throws IOException {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));

    AudioStreamService.SlicedStreamResult result =
        streamService.streamAudioClip(token, "bytes=100-500");

    assertThat(result.status()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    assertThat(result.contentRange()).isEqualTo("bytes 100-500/32768");
    assertThat(result.contentLength()).isEqualTo(401);

    try (InputStream is = result.resource().getInputStream()) {
      byte[] bytes = is.readAllBytes();
      assertThat(bytes.length).isEqualTo(401);
    }
  }

  @Test
  @DisplayName("streamAudioClip throws 416 RANGE_NOT_SATISFIABLE when range exceeds level limit")
  void streamAudioClip_rangeBeyondLimit_throws416() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));

    // Level 1 max bytes is 32,768. Range starting at 40,000 exceeds clip size
    assertThatThrownBy(() -> streamService.streamAudioClip(token, "bytes=40000-50000"))
        .isInstanceOf(AudioRangeNotSatisfiableException.class)
        .satisfies(
            ex -> {
              AudioRangeNotSatisfiableException rangeEx = (AudioRangeNotSatisfiableException) ex;
              assertThat(rangeEx.getStatus().value()).isEqualTo(416);
              assertThat(rangeEx.getCode()).isEqualTo("RANGE_NOT_SATISFIABLE");
              assertThat(rangeEx.getTotalLength()).isEqualTo(32 * 1024);
            });
  }

  @Test
  @DisplayName("M2: Cached URL invalidates and re-resolves when cached preview URL expires")
  void prepareRoundAudio_cachedUrlExpires_invalidatesAndReResolves() {
    String expiredUrl = "https://cdns-preview.dzcdn.net/stream/expired.mp3";
    String freshUrl = "https://cdns-preview.dzcdn.net/stream/fresh.mp3";

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // 1st HEAD probe for expiredUrl succeeds
    server
        .expect(once(), requestTo(expiredUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "327680"));

    // 2nd HEAD probe for expiredUrl returns 403 Forbidden (expired token URL)
    server
        .expect(once(), requestTo(expiredUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withRawStatus(403));

    // Range fallback probe for expiredUrl also fails
    server
        .expect(once(), requestTo(expiredUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withRawStatus(403));

    // 3rd HEAD probe for freshUrl succeeds
    server
        .expect(once(), requestTo(freshUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "327680"));

    AtomicLong resolveCount = new AtomicLong();
    AudioSourceResolver resolver =
        (trackId, provider, id) -> {
          long count = resolveCount.incrementAndGet();
          return count == 1 ? Optional.of(expiredUrl) : Optional.of(freshUrl);
        };

    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    UUID trackId = UUID.randomUUID();
    // 1. Initial round preparation caches expiredUrl
    AudioPreparationResult r1 =
        service.prepareRoundAudio(UUID.randomUUID(), trackId, "DEEZER", "track-1");
    assertThat(r1.playable()).isTrue();

    // 2. Second round preparation encounters expiredUrl failure, re-resolves freshUrl
    AudioPreparationResult r2 =
        service.prepareRoundAudio(UUID.randomUUID(), trackId, "DEEZER", "track-1");
    assertThat(r2.playable()).isTrue();
    assertThat(resolveCount.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("M4: Upstream responding with 200 OK skips start offset correctly")
  void streamAudioClip_upstreamReturns200_skipsToStartOffset() throws IOException {
    String remoteUrl = "https://cdns-preview.dzcdn.net/stream/full-ignore-range.mp3";
    byte[] fullAudio = new byte[32 * 1024];
    for (int i = 0; i < fullAudio.length; i++) {
      fullAudio[i] = (byte) (i & 0xFF);
    }

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // HEAD probe
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "32768"));

    // GET Range request returns 200 OK (server ignored Range header)
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                new InputStreamResource(new ByteArrayInputStream(fullAudio)),
                MediaType.valueOf("audio/mpeg")));

    AudioSourceResolver resolver = (trackId, provider, id) -> Optional.of(remoteUrl);
    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    service.prepareRoundAudio(roundId, trackId, "DEEZER", "track-ignoring-range");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));
    AudioStreamService.SlicedStreamResult result =
        service.streamAudioClip(token, "bytes=10-19"); // length = 10, offset = 10

    assertThat(result.status()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    assertThat(result.contentLength()).isEqualTo(10);

    try (InputStream is = result.resource().getInputStream()) {
      byte[] bytes = is.readAllBytes();
      assertThat(bytes.length).isEqualTo(10);
      for (int i = 0; i < 10; i++) {
        assertThat(bytes[i]).isEqualTo((byte) (10 + i));
      }
    }
  }

  @Test
  @DisplayName("m7: Range GET probe extracts Content-Range total when HEAD lacks Content-Length")
  void probeAndGetContentLength_headLacksLength_rangeProbeFallback() {
    String remoteUrl = "https://cdns-preview.dzcdn.net/stream/no-head-length.mp3";

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // HEAD without Content-Length
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK));

    // GET Range bytes=0-0 returns Content-Range: bytes 0-0/320000
    HttpHeaders rangeHeaders = new HttpHeaders();
    rangeHeaders.set(HttpHeaders.CONTENT_RANGE, "bytes 0-0/320000");
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.RANGE, "bytes=0-0"))
        .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT).headers(rangeHeaders));

    AudioSourceResolver resolver = (trackId, provider, id) -> Optional.of(remoteUrl);
    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    AudioPreparationResult result =
        service.prepareRoundAudio(UUID.randomUUID(), UUID.randomUUID(), "DEEZER", "track-range");
    assertThat(result.playable()).isTrue();
  }

  @Test
  @DisplayName("m1 (a): stream re-resolves and retries when initial remote preview URL returns 403")
  void streamAudioClip_remote403_reResolvesAndSucceeds() throws IOException {
    String expiredUrl = "https://cdns-preview.dzcdn.net/stream/expired-stream.mp3";
    String freshUrl = "https://cdns-preview.dzcdn.net/stream/fresh-stream.mp3";
    byte[] audioContent = new byte[32 * 1024];

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // 1. Initial HEAD probe succeeds for expiredUrl
    server
        .expect(once(), requestTo(expiredUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "32768"));

    // 2. Stream request to expiredUrl fails with 403 Forbidden
    server
        .expect(once(), requestTo(expiredUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withRawStatus(403));

    // 3. Re-probe freshUrl via HEAD succeeds
    server
        .expect(once(), requestTo(freshUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "32768"));

    // 4. Stream request to freshUrl succeeds with 206 Partial Content
    server
        .expect(once(), requestTo(freshUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(new InputStreamResource(new ByteArrayInputStream(audioContent))));

    AtomicLong resolveCount = new AtomicLong();
    AudioSourceResolver resolver =
        (trackId, provider, id) -> {
          long count = resolveCount.incrementAndGet();
          return count == 1 ? Optional.of(expiredUrl) : Optional.of(freshUrl);
        };

    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    service.prepareRoundAudio(roundId, trackId, "DEEZER", "track-re-resolve");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));
    AudioStreamService.SlicedStreamResult result = service.streamAudioClip(token, null);

    try (InputStream is = result.resource().getInputStream()) {
      byte[] bytes = is.readAllBytes();
      assertThat(bytes.length).isEqualTo(32 * 1024);
    }
    assertThat(resolveCount.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("m1 (b): stream failure on upstream 5xx maps to 502 UPSTREAM_ERROR")
  void streamAudioClip_upstreamServerError_throwsAudioException502() throws IOException {
    String remoteUrl = "https://cdns-preview.dzcdn.net/stream/server-error.mp3";

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    // HEAD probe succeeds
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(withStatus(HttpStatus.OK).header(HttpHeaders.CONTENT_LENGTH, "32768"));

    // GET fails with 500
    server
        .expect(once(), requestTo(remoteUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    AtomicLong resolveCount = new AtomicLong();
    AudioSourceResolver resolver =
        (trackId, provider, id) -> {
          long count = resolveCount.incrementAndGet();
          return count == 1 ? Optional.of(remoteUrl) : Optional.empty();
        };
    AudioStreamService service =
        new AudioStreamService(properties, resolver, new DefaultResourceLoader(), builder);

    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    service.prepareRoundAudio(roundId, trackId, "DEEZER", "track-500");

    AudioToken token = new AudioToken(roundId, playerId, 1, Instant.now().plusSeconds(120));
    AudioStreamService.SlicedStreamResult result = service.streamAudioClip(token, null);

    assertThatThrownBy(() -> result.resource().getInputStream())
        .isInstanceOf(AudioException.class)
        .satisfies(
            ex -> {
              AudioException ae = (AudioException) ex;
              assertThat(ae.getStatus().value()).isEqualTo(502);
              assertThat(ae.getCode()).isEqualTo("UPSTREAM_ERROR");
            });
  }

  @Test
  @DisplayName("m1 (c): path traversal with .. in fake media is rejected")
  void prepareRoundAudio_pathTraversalFakeMedia_returnsFailure() {
    AudioSourceResolver resolver =
        (trackId, provider, id) -> Optional.of("classpath:fake-media/../config/audio.yml");
    AudioStreamService service =
        new AudioStreamService(
            properties, resolver, new DefaultResourceLoader(), RestClient.builder());

    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    AudioPreparationResult result =
        service.prepareRoundAudio(roundId, trackId, "FAKE", "traversal-track");

    assertThat(result.playable()).isFalse();
    assertThat(service.isRoundPlayable(roundId)).isFalse();
  }

  @Test
  @DisplayName("releaseRoundAudio clears round mapping from memory")
  void releaseRoundAudio_clearsRoundMapping() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    assertThat(streamService.isRoundPlayable(roundId)).isTrue();

    streamService.releaseRoundAudio(roundId);
    assertThat(streamService.isRoundPlayable(roundId)).isFalse();
  }
}
