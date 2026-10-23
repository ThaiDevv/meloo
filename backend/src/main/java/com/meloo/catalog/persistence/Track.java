package com.meloo.catalog.persistence;

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
@Table(name = "track")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Track implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MusicProvider provider;

  @Column(name = "provider_track_id", nullable = false, length = 64)
  private String providerTrackId;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(name = "title_normalized", nullable = false, length = 300)
  private String titleNormalized;

  @Column(name = "primary_artist_id", nullable = false)
  private UUID primaryArtistId;

  @Nullable @Column(name = "genre_id")
  private UUID genreId;

  @Nullable @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Nullable @Column(name = "release_year")
  private Short releaseYear;

  @Nullable @Column(name = "popularity_rank")
  private Integer popularityRank;

  @Builder.Default
  @Column(nullable = false)
  private boolean enabled = true;

  @Nullable @Column(name = "disabled_reason", length = 200)
  private String disabledReason;

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
