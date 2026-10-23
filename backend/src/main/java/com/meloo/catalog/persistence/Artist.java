package com.meloo.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "artist")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Artist {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "name_normalized", nullable = false, length = 200)
  private String nameNormalized;

  @Column(nullable = false, length = 16)
  private String provider;

  @Column(name = "provider_artist_id", nullable = false, length = 64)
  private String providerArtistId;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;
}
