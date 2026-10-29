package com.meloo.audio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioRangeNotSatisfiableException;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.ListeningLevel;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class AudioStreamServiceTest {

  private AudioStreamService streamService;

  @BeforeEach
  void setUp() {
    AudioProperties properties =
        new AudioProperties(
            "secret",
            Duration.ofMinutes(2),
            Duration.ofMinutes(15),
            1000,
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

    FakeAudioSourceResolver fakeResolver = new FakeAudioSourceResolver("classpath:fake-media/");

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
  @DisplayName("releaseRoundAudio frees round mapping from memory")
  void releaseRoundAudio_clearsRoundMapping() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    streamService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    assertThat(streamService.isRoundPlayable(roundId)).isTrue();

    streamService.releaseRoundAudio(roundId);
    assertThat(streamService.isRoundPlayable(roundId)).isFalse();
  }
}
