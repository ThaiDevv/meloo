package com.meloo.progression.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "match_player")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MatchPlayer implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Nullable @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 20)
  private String nickname;

  @Column(name = "is_guest", nullable = false)
  private boolean guest;

  @Column(name = "total_score", nullable = false)
  private int totalScore;

  @Column(nullable = false)
  private short rank;

  @Column(name = "correct_count", nullable = false)
  private short correctCount;

  @Column(name = "correct_time_ms", nullable = false)
  private long correctTimeMs;

  @Column(name = "counts_for_leaderboard", nullable = false)
  private boolean countsForLeaderboard;

  @Column(name = "ended_at", nullable = false)
  private Instant endedAt;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Transient
  @Builder.Default
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private boolean newEntity = true;

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    newEntity = false;
  }
}
