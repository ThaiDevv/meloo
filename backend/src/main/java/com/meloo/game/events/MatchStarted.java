package com.meloo.game.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sự kiện MatchStarted phát khi trận đấu bắt đầu. Module sở hữu: game. Định nghĩa ở BE-GAME-01;
 * chơi đơn phát ở BE-SOLO-01, phòng phát ở BE-ROOM-02. Ai nghe: admin.
 */
public record MatchStarted(
    UUID eventId,
    UUID matchId,
    MatchType type,
    boolean ranked,
    int roundCount,
    Instant startedAt,
    List<UUID> playerIds) {

  public MatchStarted {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    if (roundCount < 1) {
      throw new IllegalArgumentException("roundCount must be >= 1");
    }
    playerIds = playerIds == null ? List.of() : List.copyOf(playerIds);
  }
}
