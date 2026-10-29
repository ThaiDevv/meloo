package com.meloo.game.solo;

import java.util.Objects;
import java.util.UUID;

/** Ngoại lệ khi người chơi đã có một phiên chơi đơn đang diễn ra (BR-23, HTTP 409). */
public class MatchInProgressException extends RuntimeException {

  private final UUID matchId;

  public MatchInProgressException(UUID matchId) {
    super("Finish or resume the match in progress before starting another.");
    this.matchId = Objects.requireNonNull(matchId, "matchId must not be null");
  }

  public UUID matchId() {
    return matchId;
  }
}
