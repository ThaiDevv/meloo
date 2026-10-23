package com.meloo.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
@Table(name = "playlist_track")
@IdClass(PlaylistTrackId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlaylistTrack {

  @Id
  @Column(name = "playlist_id", updatable = false)
  private UUID playlistId;

  @Id
  @Column(name = "track_id", updatable = false)
  private UUID trackId;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;
}
