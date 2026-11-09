package com.meloo.daily.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DailyPersistenceIntegrationTest extends IntegrationTest {
  @PersistenceContext private EntityManager entityManager;

  @Test
  void persistsChallengeAndAttemptWithNullableAnonymousUser() {
    LocalDate date = LocalDate.of(2026, 11, 9);
    UUID track = UUID.randomUUID();
    entityManager.persist(
        DailyChallenge.builder()
            .challengeDate(date)
            .trackId(track)
            .source(ChallengeSource.AUTO)
            .build());
    UUID id = UUID.randomUUID();
    entityManager.persist(
        DailyAttempt.builder()
            .id(id)
            .challengeDate(date)
            .guest(true)
            .status(AttemptStatus.SOLVED)
            .startedAt(Instant.now())
            .finishedAt(Instant.now())
            .correctLevel((short) 2)
            .elapsedMs(5000)
            .build());
    entityManager.flush();
    entityManager.clear();
    var loaded = entityManager.find(DailyAttempt.class, id);
    assertThat(loaded.getStatus()).isEqualTo(AttemptStatus.SOLVED);
    assertThat(loaded.getUserId()).isNull();
    assertThat(loaded.getCorrectLevel()).isEqualTo((short) 2);
    assertThat(loaded.getElapsedMs()).isEqualTo(5000);
    assertThat(loaded.getCreatedAt()).isNotNull();
    var challenge = entityManager.find(DailyChallenge.class, date);
    assertThat(challenge.getTrackId()).isEqualTo(track);
    assertThat(challenge.getSource()).isEqualTo(ChallengeSource.AUTO);
  }
}
