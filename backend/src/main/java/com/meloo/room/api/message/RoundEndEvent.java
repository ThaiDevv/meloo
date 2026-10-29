package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Round conclusion with revealed answer and full score breakdown (BR-02, BR-07, BR-08, BR-29). */
public record RoundEndEvent(
    String type,
    int roundNo,
    RoundAnswerPayload answer,
    List<PlayerRoundResultPayload> results,
    @Nullable Instant nextRoundAt,
    Instant serverTime)
    implements RoomMessage {

  public RoundEndEvent(
      int roundNo,
      RoundAnswerPayload answer,
      List<PlayerRoundResultPayload> results,
      @Nullable Instant nextRoundAt,
      Instant serverTime) {
    this("ROUND_END", roundNo, answer, results, nextRoundAt, serverTime);
  }
}
