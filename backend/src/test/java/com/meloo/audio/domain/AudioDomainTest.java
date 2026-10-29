package com.meloo.audio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AudioDomainTest {

  @Test
  @DisplayName("AudioException getters and constructor variants work properly")
  void audioException_allConstructorsAndGetters() {
    AudioException ex1 = new AudioException(HttpStatus.BAD_REQUEST, "CODE1", "Title 1", "Detail 1");
    assertThat(ex1.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex1.getCode()).isEqualTo("CODE1");
    assertThat(ex1.getTitle()).isEqualTo("Title 1");
    assertThat(ex1.getMessage()).isEqualTo("Detail 1");
    assertThat(ex1.getCause()).isNull();

    Throwable cause = new RuntimeException("Root cause");
    AudioException ex2 =
        new AudioException(HttpStatus.INTERNAL_SERVER_ERROR, "CODE2", "Title 2", "Detail 2", cause);
    assertThat(ex2.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(ex2.getCode()).isEqualTo("CODE2");
    assertThat(ex2.getTitle()).isEqualTo("Title 2");
    assertThat(ex2.getMessage()).isEqualTo("Detail 2");
    assertThat(ex2.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("ListeningLevel properties and fromLevel mapping")
  void listeningLevel_propertiesAndFromLevel() {
    for (int lvl = 1; lvl <= 5; lvl++) {
      ListeningLevel level = ListeningLevel.fromLevel(lvl);
      assertThat(level.getLevel()).isEqualTo(lvl);
      assertThat(level.getDurationSeconds()).isPositive();
      assertThat(level.getMaxBytes()).isPositive();
    }

    assertThatThrownBy(() -> ListeningLevel.fromLevel(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ListeningLevel.fromLevel(6))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AudioToken isExpired correctly evaluates based on instant")
  void audioToken_isExpired() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-29T12:00:00Z");

    AudioToken active = new AudioToken(roundId, playerId, 1, now.plusSeconds(30));
    assertThat(active.isExpired(now)).isFalse();

    AudioToken expired = new AudioToken(roundId, playerId, 1, now.minusSeconds(1));
    assertThat(expired.isExpired(now)).isTrue();
  }

  @Test
  @DisplayName("AudioException subclasses have expected status, code, and properties")
  void audioExceptionSubclasses_properties() {
    AudioTokenInvalidException malformed = AudioTokenInvalidException.malformed();
    assertThat(malformed.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(malformed.getCode()).isEqualTo("AUDIO_TOKEN_INVALID");

    AudioTokenInvalidException invalidSig = AudioTokenInvalidException.invalidSignature();
    assertThat(invalidSig.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(invalidSig.getCode()).isEqualTo("AUDIO_TOKEN_INVALID");

    AudioTokenExpiredException expired = new AudioTokenExpiredException();
    assertThat(expired.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(expired.getCode()).isEqualTo("AUDIO_TOKEN_EXPIRED");

    AccountLockedException locked = new AccountLockedException();
    assertThat(locked.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(locked.getCode()).isEqualTo("ACCOUNT_LOCKED");

    AudioRangeNotSatisfiableException rangeEx = new AudioRangeNotSatisfiableException(32768);
    assertThat(rangeEx.getStatus()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    assertThat(rangeEx.getCode()).isEqualTo("RANGE_NOT_SATISFIABLE");
    assertThat(rangeEx.getTotalLength()).isEqualTo(32768);

    AudioRateLimitException rateLimitEx = new AudioRateLimitException(5);
    assertThat(rateLimitEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(rateLimitEx.getCode()).isEqualTo("RATE_LIMITED");
    assertThat(rateLimitEx.getRetryAfterSeconds()).isEqualTo(5);
  }
}
