package com.meloo.room.api.message;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Private result of a player's guess attempt (BR-04, BR-05, BR-06, BR-07). */
public record GuessResultMessage(
    String type,
    int roundNo,
    String result,
    boolean isCorrect,
    boolean isCorrectArtist,
    @Nullable RoundScoreDetailPayload scoreDetail,
    @Nullable Integer nextLevel,
    @Nullable AudioClipPayload nextAudioClip,
    Instant serverTime)
    implements RoomMessage {

  public GuessResultMessage(
      int roundNo,
      String result,
      boolean isCorrect,
      boolean isCorrectArtist,
      @Nullable RoundScoreDetailPayload scoreDetail,
      @Nullable Integer nextLevel,
      @Nullable AudioClipPayload nextAudioClip,
      Instant serverTime) {
    this(
        "GUESS_RESULT_PRIVATE",
        roundNo,
        result,
        isCorrect,
        isCorrectArtist,
        scoreDetail,
        nextLevel,
        nextAudioClip,
        serverTime);
  }

  public GuessResultMessage(
      int roundNo,
      String result,
      boolean isCorrect,
      boolean isCorrectArtist,
      @Nullable RoundScoreDetailPayload scoreDetail,
      Instant serverTime) {
    this(roundNo, result, isCorrect, isCorrectArtist, scoreDetail, null, null, serverTime);
  }
}
