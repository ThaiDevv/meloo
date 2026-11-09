package com.meloo.progression.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PlayerStatsPersistenceIntegrationTest extends IntegrationTest {
  @PersistenceContext private EntityManager entityManager;

  @Test
  void persistsStatsAndCompositeAchievementId() {
    UUID user = UUID.randomUUID();
    var stats =
        PlayerStats.builder()
            .userId(user)
            .guest(false)
            .xp(100)
            .rankedScore(20)
            .rankedReachedAt(Instant.parse("2026-11-09T00:00:00Z"))
            .build();
    entityManager.persist(stats);
    var id = new UserAchievementId(user, "FIRST_WIN");
    entityManager.persist(UserAchievement.builder().id(id).unlockedAt(Instant.now()).build());
    entityManager.flush();
    entityManager.clear();
    var loaded = entityManager.find(PlayerStats.class, user);
    assertThat(loaded.getRankedScore()).isEqualTo(20);
    assertThat(loaded.getXp()).isEqualTo(100);
    assertThat(loaded.getRankedReachedAt()).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(entityManager.find(UserAchievement.class, id).getUnlockedAt()).isNotNull();
    assertThat(entityManager.find(Achievement.class, "FIRST_WIN").getSortOrder())
        .isEqualTo((short) 1);
  }
}
