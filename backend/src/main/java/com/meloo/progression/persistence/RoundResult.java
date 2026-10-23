package com.meloo.progression.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "round_result")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RoundResult implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(name = "round_id", nullable = false)
  private UUID roundId;

  @Column(name = "match_player_id", nullable = false)
  private UUID matchPlayerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private RoundOutcome outcome;

  @Nullable @Column(name = "correct_level")
  private Short correctLevel;

  @Column(name = "base_score", nullable = false)
  private int baseScore;

  @Builder.Default
  @Column(name = "combo_percent", nullable = false)
  private short comboPercent = 0;

  @Builder.Default
  @Column(name = "speed_bonus", nullable = false)
  private int speedBonus = 0;

  @Column(name = "round_score", nullable = false)
  private int roundScore;

  @Builder.Default
  @Column(name = "wrong_count", nullable = false)
  private short wrongCount = 0;

  @Nullable @Column(name = "answer_time_ms")
  private Integer answerTimeMs;

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
