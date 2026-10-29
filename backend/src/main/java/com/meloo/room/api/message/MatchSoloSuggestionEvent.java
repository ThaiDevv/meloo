package com.meloo.room.api.message;

import java.time.Instant;

/** Suggestion to play solo or create a private room after waiting 60s alone (BR-13). */
public record MatchSoloSuggestionEvent(String type, String message, Instant serverTime)
    implements RoomMessage {

  public MatchSoloSuggestionEvent(String message, Instant serverTime) {
    this("MATCH_SOLO_SUGGESTION", message, serverTime);
  }
}
