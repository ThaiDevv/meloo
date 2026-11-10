package com.meloo.daily.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "daily_attempt")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailyAttempt {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "challenge_date", nullable = false)
  private LocalDate challengeDate;

  @Nullable @Column(name = "user_id", nullable = true)
  private UUID userId;

  @Column(name = "is_guest", nullable = false)
  private boolean guest;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 11)
  private AttemptStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Nullable @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Nullable @Column(name = "correct_level", nullable = true)
  private Short correctLevel;

  @Nullable @Column(name = "elapsed_ms", nullable = true)
  private Integer elapsedMs;

  @Builder.Default
  @Column(name = "current_level", nullable = false)
  private short currentLevel = 1;

  @Column(name = "wrong_count", nullable = false)
  private int wrongCount;

  @Nullable @Column(name = "outcome", length = 12)
  private String outcome;

  @Nullable @Column(name = "track_snapshot", columnDefinition = "text")
  private String trackSnapshot;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
