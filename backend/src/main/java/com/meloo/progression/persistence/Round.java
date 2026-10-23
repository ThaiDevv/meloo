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

@Entity
@Table(name = "round")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Round {

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

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
