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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "match")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MatchEntity implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MatchType type;

  @Nullable @Column(length = 16)
  private String mode;

  @Column(nullable = false)
  private boolean ranked;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MatchStatus status;

  @Column(name = "round_count", nullable = false)
  private short roundCount;

  @Nullable @Column(name = "playlist_id")
  private UUID playlistId;

  @Nullable @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filter")
  private String filter;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

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
