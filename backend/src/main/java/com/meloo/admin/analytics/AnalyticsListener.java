package com.meloo.admin.analytics;

import com.meloo.game.events.MatchEndStatus;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchStarted;
import com.meloo.identity.events.GuestCreated;
import com.meloo.identity.events.GuestMerged;
import com.meloo.identity.events.UserRegistered;
import java.util.HashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsListener {
  private final AnalyticsWriter writer;

  public AnalyticsListener(AnalyticsWriter writer) {
    this.writer = writer;
  }

  @ApplicationModuleListener
  public void on(GuestCreated event) {
    writer.record(event.eventId(), "GUEST_CREATED", event.userId(), event.createdAt(), Map.of());
  }

  @ApplicationModuleListener
  public void on(UserRegistered event) {
    writer.record(event.eventId(), "USER_REGISTERED", event.userId(), event.occurredAt(), Map.of());
  }

  @ApplicationModuleListener
  public void on(GuestMerged event) {
    writer.record(
        event.eventId(),
        "GUEST_MERGED",
        event.guestId(),
        event.occurredAt(),
        Map.of("userId", event.userId()));
  }

  @ApplicationModuleListener
  public void on(MatchStarted event) {
    writer.record(
        event.eventId(),
        "MATCH_STARTED",
        null,
        event.startedAt(),
        Map.of(
            "matchId", event.matchId(), "players", event.playerIds().stream().distinct().toList()));
  }

  @ApplicationModuleListener
  public void on(MatchFinished event) {
    // Keep analytics free of round answers, names, scores, audio URLs and preview tokens.
    var props = new HashMap<String, Object>();
    props.put("matchId", event.matchId());
    props.put("status", event.status());
    if (event.playlistId() != null) props.put("playlistId", event.playlistId());
    props.put(
        "players",
        event.status() == MatchEndStatus.FINISHED
            ? event.players().stream()
                .filter(p -> !p.left() && p.userId() != null)
                .map(p -> p.userId())
                .distinct()
                .toList()
            : java.util.List.of());
    writer.record(event.eventId(), "MATCH_FINISHED", null, event.endedAt(), props);
  }
}
