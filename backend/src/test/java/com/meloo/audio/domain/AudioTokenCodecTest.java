package com.meloo.audio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioTokenCodecTest {

  private static final String SECRET =
      "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256!";
  private static final Instant FIXED_NOW = Instant.parse("2026-10-29T10:00:00Z");

  private AudioTokenCodec codec;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    codec = new AudioTokenCodec(SECRET, clock);
  }

  @Test
  @DisplayName("encode creates a valid opaque token matching regex pattern and verifies correctly")
  void validToken_encodesAndVerifiesCorrectly() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    int level = 2;
    Instant expiresAt = FIXED_NOW.plusSeconds(120);

    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    String rawToken = codec.encode(token);

    assertThat(rawToken).isNotNull();
    assertThat(rawToken.length())
        .isGreaterThanOrEqualTo(AudioTokenCodec.MIN_TOKEN_LENGTH)
        .isLessThanOrEqualTo(AudioTokenCodec.MAX_TOKEN_LENGTH);
    assertThat(AudioTokenCodec.TOKEN_PATTERN.matcher(rawToken).matches()).isTrue();

    AudioToken decoded = codec.decodeAndVerify(rawToken);
    assertThat(decoded.roundId()).isEqualTo(roundId);
    assertThat(decoded.playerId()).isEqualTo(playerId);
    assertThat(decoded.level()).isEqualTo(level);
    assertThat(decoded.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("expired token triggers AudioTokenExpiredException (401 AUDIO_TOKEN_EXPIRED)")
  void expiredToken_throwsAudioTokenExpiredException() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    int level = 1;
    Instant expiresAt = FIXED_NOW.minusSeconds(1); // Already expired

    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    String rawToken = codec.encode(token);

    assertThatThrownBy(() -> codec.decodeAndVerify(rawToken))
        .isInstanceOf(AudioTokenExpiredException.class)
        .satisfies(
            ex -> {
              AudioTokenExpiredException expiredEx = (AudioTokenExpiredException) ex;
              assertThat(expiredEx.getStatus().value()).isEqualTo(401);
              assertThat(expiredEx.getCode()).isEqualTo("AUDIO_TOKEN_EXPIRED");
            });
  }

  @Test
  @DisplayName("tampered signature triggers AudioTokenInvalidException (401 AUDIO_TOKEN_INVALID)")
  void tamperedSignature_throwsAudioTokenInvalidException() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    int level = 3;
    Instant expiresAt = FIXED_NOW.plusSeconds(120);

    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    String rawToken = codec.encode(token);

    byte[] bytes = Base64.getUrlDecoder().decode(rawToken);
    // Flip a bit in the signature (last 32 bytes)
    bytes[bytes.length - 1] ^= 0xFF;
    String tamperedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    assertThatThrownBy(() -> codec.decodeAndVerify(tamperedToken))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> {
              AudioTokenInvalidException invalidEx = (AudioTokenInvalidException) ex;
              assertThat(invalidEx.getStatus().value()).isEqualTo(401);
              assertThat(invalidEx.getCode()).isEqualTo("AUDIO_TOKEN_INVALID");
            });
  }

  @Test
  @DisplayName("tampered payload triggers AudioTokenInvalidException (401 AUDIO_TOKEN_INVALID)")
  void tamperedPayload_throwsAudioTokenInvalidException() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    int level = 1;
    Instant expiresAt = FIXED_NOW.plusSeconds(120);

    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    String rawToken = codec.encode(token);

    byte[] bytes = Base64.getUrlDecoder().decode(rawToken);
    // Flip a bit in the payload (first 41 bytes)
    bytes[0] ^= 0xFF;
    String tamperedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    assertThatThrownBy(() -> codec.decodeAndVerify(tamperedToken))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> {
              AudioTokenInvalidException invalidEx = (AudioTokenInvalidException) ex;
              assertThat(invalidEx.getStatus().value()).isEqualTo(401);
              assertThat(invalidEx.getCode()).isEqualTo("AUDIO_TOKEN_INVALID");
            });
  }

  @Test
  @DisplayName("malformed tokens trigger AudioTokenInvalidException (400 AUDIO_TOKEN_INVALID)")
  void malformedToken_throwsAudioTokenInvalidException() {
    // Too short (< 16 chars)
    assertThatThrownBy(() -> codec.decodeAndVerify("too-short"))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> assertThat(((AudioTokenInvalidException) ex).getStatus().value()).isEqualTo(400));

    // Invalid characters (not in regex ^[A-Za-z0-9_-]+$)
    assertThatThrownBy(() -> codec.decodeAndVerify("invalid@token!with#symbols$length>=16"))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> assertThat(((AudioTokenInvalidException) ex).getStatus().value()).isEqualTo(400));

    // Too long (> 256 chars)
    String tooLong = "a".repeat(257);
    assertThatThrownBy(() -> codec.decodeAndVerify(tooLong))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> assertThat(((AudioTokenInvalidException) ex).getStatus().value()).isEqualTo(400));
  }

  @Test
  @DisplayName("different secret rejects tokens signed with another key")
  void differentSecret_rejectsToken() {
    AudioTokenCodec otherCodec =
        new AudioTokenCodec(
            "different-secret-key-that-is-at-least-256-bits-long!",
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    AudioToken token = new AudioToken(roundId, playerId, 1, FIXED_NOW.plusSeconds(60));

    String tokenFromOther = otherCodec.encode(token);

    assertThatThrownBy(() -> codec.decodeAndVerify(tokenFromOther))
        .isInstanceOf(AudioTokenInvalidException.class)
        .satisfies(
            ex -> assertThat(((AudioTokenInvalidException) ex).getStatus().value()).isEqualTo(401));
  }

  @Test
  @DisplayName("secret with less than 32 bytes throws IllegalArgumentException")
  void shortSecret_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new AudioTokenCodec("short-secret-under-32-bytes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");
  }
}
