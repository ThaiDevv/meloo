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
@Table(name = "round")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Round implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Column(name = "round_no", nullable = false)
  private short roundNo;

  @Column(name = "track_id", nullable = false)
  private UUID trackId;

  @Column(name = "track_title", nullable = false, length = 300)
  private String trackTitle;

  @Column(name = "artist_name", nullable = false, length = 200)
  private String artistName;

  @Nullable @Column(length = 16)
  private String provider;

  @Nullable @Column(name = "provider_track_id", length = 200)
  private String providerTrackId;

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
