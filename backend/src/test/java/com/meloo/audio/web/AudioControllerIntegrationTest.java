package com.meloo.audio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.audio.domain.AudioTokenCodec;
import com.meloo.audio.service.AccountLockChecker;
import com.meloo.generated.model.AudioClipDto;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AudioControllerIntegrationTest extends IntegrationTest {

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "MELOO_JWT_SECRET", () -> "test-meloo-jwt-secret-key-with-minimum-32-bytes-length-xyz");
    registry.add("meloo.audio.provider.type", () -> "fake");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private AudioService audioService;
  @Autowired private AudioTokenCodec audioTokenCodec;

  @MockitoBean private AccountLockChecker accountLockChecker;

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} with valid token returns HTTP 200 audio/mpeg capped to 32KB for Level 1")
  void getAudioClip_validToken_returnsAudioMpeg200() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    AudioPreparationResult prep =
        audioService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    assertThat(prep.playable()).isTrue();

    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);
    assertThat(clip.getUrl()).startsWith("/api/v1/audio/");

    MvcResult result =
        mockMvc
            .perform(get(clip.getUrl()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(32 * 1024)))
            .andReturn();

    byte[] content = result.getResponse().getContentAsByteArray();
    assertThat(content.length).isEqualTo(32 * 1024);

    // Verify response headers do not leak trackId, artist or title (AC 2)
    for (String headerName : result.getResponse().getHeaderNames()) {
      String val = result.getResponse().getHeader(headerName);
      assertThat(val).doesNotContain(trackId.toString());
      assertThat(val).doesNotContainIgnoringCase("See Tình");
      assertThat(val).doesNotContainIgnoringCase("Hoàng Thùy Linh");
    }
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} supports HTTP Range requests with HTTP 206 Partial Content")
  void getAudioClip_rangeRequest_returns206PartialContent() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    audioService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);

    MvcResult result =
        mockMvc
            .perform(get(clip.getUrl()).header(HttpHeaders.RANGE, "bytes=100-1099"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 100-1099/32768"))
            .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "1000"))
            .andReturn();

    byte[] content = result.getResponse().getContentAsByteArray();
    assertThat(content.length).isEqualTo(1000);
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} with Range beyond level limit returns HTTP 416 RANGE_NOT_SATISFIABLE")
  void getAudioClip_rangeBeyondLimit_returns416() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    audioService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);

    mockMvc
        .perform(get(clip.getUrl()).header(HttpHeaders.RANGE, "bytes=40000-50000"))
        .andExpect(status().isRequestedRangeNotSatisfiable())
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */32768"))
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("RANGE_NOT_SATISFIABLE"))
        .andExpect(jsonPath("$.title").value("Range Not Satisfiable"))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} with malformed token returns HTTP 400 AUDIO_TOKEN_INVALID without echoing raw token")
  void getAudioClip_malformedToken_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/audio/bad@token!format#123"))
        .andExpect(status().isBadRequest())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("AUDIO_TOKEN_INVALID"))
        .andExpect(jsonPath("$.title").value("Malformed audio token"))
        .andExpect(
            jsonPath("$.detail")
                .value("The audio preview token format is malformed or exceeds maximum length."))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} with invalid HMAC signature returns HTTP 401 AUDIO_TOKEN_INVALID")
  void getAudioClip_invalidHmac_returns401() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    audioService.prepareRoundAudio(roundId, UUID.randomUUID(), "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);

    String rawToken = clip.getUrl().replace("/api/v1/audio/", "");
    byte[] bytes = Base64.getUrlDecoder().decode(rawToken);
    bytes[bytes.length - 1] ^= 0x5A; // Corrupt signature
    String tamperedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    mockMvc
        .perform(get("/api/v1/audio/" + tamperedToken))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("AUDIO_TOKEN_INVALID"))
        .andExpect(jsonPath("$.title").value("Invalid audio token"))
        .andExpect(
            jsonPath("$.detail")
                .value("The audio preview token signature is invalid or HMAC verification failed."))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName("GET /api/v1/audio/{token} with expired token returns HTTP 401 AUDIO_TOKEN_EXPIRED")
  void getAudioClip_expiredToken_returns401() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    audioService.prepareRoundAudio(roundId, UUID.randomUUID(), "FAKE", "fake-1");

    Instant past = Instant.now().minusSeconds(10);
    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1, past);

    mockMvc
        .perform(get(clip.getUrl()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("AUDIO_TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.title").value("Audio token expired"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The audio preview token has expired. Request a fresh round state to get a new token."))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} when authenticated caller is not the token owner returns HTTP 401 AUDIO_TOKEN_INVALID")
  @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
  void getAudioClip_otherPlayerToken_returns401() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID tokenPlayerId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    audioService.prepareRoundAudio(roundId, UUID.randomUUID(), "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, tokenPlayerId, 1);

    mockMvc
        .perform(get(clip.getUrl()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("AUDIO_TOKEN_INVALID"))
        .andExpect(jsonPath("$.title").value("Invalid audio token"))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} when player account is locked returns HTTP 403 ACCOUNT_LOCKED (BR-28)")
  void getAudioClip_accountLocked_returns403() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID lockedPlayerId = UUID.randomUUID();

    when(accountLockChecker.isAccountLocked(lockedPlayerId)).thenReturn(true);

    audioService.prepareRoundAudio(roundId, UUID.randomUUID(), "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, lockedPlayerId, 1);

    mockMvc
        .perform(get(clip.getUrl()))
        .andExpect(status().isForbidden())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
        .andExpect(jsonPath("$.title").value("Account locked"))
        .andExpect(
            jsonPath("$.detail")
                .value("The account associated with this session is locked (BR-28)."))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName(
      "GET /api/v1/audio/{token} exceeding token rate limit returns HTTP 429 RATE_LIMITED with Retry-After header")
  void getAudioClip_rateLimitExceeded_returns429() throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    audioService.prepareRoundAudio(roundId, UUID.randomUUID(), "FAKE", "fake-1");
    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);

    // Make rapid requests to consume token burst capacity (capacity = 5)
    for (int i = 0; i < 5; i++) {
      mockMvc.perform(get(clip.getUrl())).andExpect(status().isOk());
    }

    // 6th request triggers rate limit
    mockMvc
        .perform(get(clip.getUrl()))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(jsonPath("$.instance").value("/api/v1/audio/%7Btoken%7D"));
  }

  @Test
  @DisplayName("prepareRoundAudio detects unreachable/unresolvable sources for BR-27")
  void prepareRoundAudio_unreachableSource_returnsFailure() {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();

    AudioPreparationResult result =
        audioService.prepareRoundAudio(roundId, trackId, "FAKE", "broken-missing-track");

    assertThat(result.playable()).isFalse();
    assertThat(result.failureReason()).isNotEmpty();
    assertThat(audioService.isRoundPlayable(roundId)).isFalse();
  }
}
