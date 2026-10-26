package com.meloo.game.events;

import java.util.Objects;
import java.util.UUID;

/** Thông tin tóm tắt một vòng trong trận đấu theo mục 2 tài liệu 04-su-kien-va-api-module.md. */
public record RoundSummary(int roundNo, UUID trackId, String trackTitle, String artistName) {

  public RoundSummary {
    if (roundNo < 1) {
      throw new IllegalArgumentException("roundNo must be >= 1");
    }
    Objects.requireNonNull(trackId, "trackId must not be null");
    Objects.requireNonNull(trackTitle, "trackTitle must not be null");
    Objects.requireNonNull(artistName, "artistName must not be null");
  }
}
