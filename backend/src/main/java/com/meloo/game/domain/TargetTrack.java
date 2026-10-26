package com.meloo.game.domain;

import java.util.UUID;

/** Bài hát mục tiêu của vòng chơi. */
public record TargetTrack(UUID trackId, String title, String artistName) {

  public TargetTrack {
    if (trackId == null) {
      throw new IllegalArgumentException("trackId must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (artistName == null || artistName.isBlank()) {
      throw new IllegalArgumentException("artistName must not be blank");
    }
  }
}
