package com.meloo.progression.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "player_stats")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerStats {
  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "is_guest", nullable = false)
  private boolean guest;

  @Column(name = "xp", nullable = false)
  private int xp;

  @Column(name = "matches_played", nullable = false)
  private int matchesPlayed;

  @Column(name = "matches_won", nullable = false)
  private int matchesWon;

  @Column(name = "rounds_correct", nullable = false)
  private int roundsCorrect;

  @Column(name = "rounds_played", nullable = false)
  private int roundsPlayed;

  @Column(name = "correct_level_total", nullable = false)
  private long correctLevelTotal;

  @Column(name = "listen_seconds_total", nullable = false)
  private long listenSecondsTotal;

  @Column(name = "ranked_matches", nullable = false)
  private int rankedMatches;

  @Column(name = "best_combo", nullable = false)
  private short bestCombo;

  @Column(name = "ranked_score", nullable = false)
  private long rankedScore;

  @Nullable @Column(name = "ranked_reached_at", nullable = true)
  private Instant rankedReachedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
