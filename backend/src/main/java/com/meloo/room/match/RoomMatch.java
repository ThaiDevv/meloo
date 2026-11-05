package com.meloo.room.match;

import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.ComboTracker;
import com.meloo.game.domain.Round;
import com.meloo.game.events.RoundPlayerResult;
import com.meloo.game.events.RoundSummary;
import com.meloo.room.api.message.PlayerRoundResultPayload;
import com.meloo.room.api.message.RoundAnswerPayload;
import com.meloo.room.lobby.RoomPlayer;
import com.meloo.room.lobby.RoomSession;
import com.meloo.room.realtime.RoomActor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

/** Mutable match state confined to its room actor. */
final class RoomMatch {
  final UUID id = UUID.randomUUID();
  final RoomSession room;
  final RoomActor actor;
  final Map<UUID, Participant> players = new LinkedHashMap<>();
  final Set<UUID> excluded = new HashSet<>();
  final List<RoundSummary> rounds = new ArrayList<>();
  List<TrackInfo> tracks = List.of();
  String phase = "ROUND_PREPARE";
  int roundNo = 1;
  long generation;
  long departureSequence;
  @Nullable UUID audioRoundId;
  @Nullable TrackInfo track;
  @Nullable Instant startedAt;
  @Nullable Instant roundStartedAt;
  @Nullable Instant prepareDeadline;
  @Nullable ScheduledFuture<?> timer;
  @Nullable RoundAnswerPayload answer;
  List<PlayerRoundResultPayload> lastResults = List.of();
  boolean loading = true;
  boolean published;

  RoomMatch(RoomSession room, RoomActor actor) {
    this.room = room;
    this.actor = actor;
    room.players().forEach(p -> players.put(p.userId(), new Participant(p)));
  }

  void cancelTimer() {
    if (timer != null) timer.cancel(false);
    timer = null;
  }

  static final class Participant {
    final RoomPlayer lobbyPlayer;
    final List<RoundPlayerResult> results = new ArrayList<>();
    ComboTracker combo = new ComboTracker(true);
    @Nullable Round round;
    long departure;
    boolean prepared;

    Participant(RoomPlayer player) {
      lobbyPlayer = player;
    }

    boolean left() {
      return departure > 0;
    }

    int totalScore() {
      int score = results.stream().mapToInt(RoundPlayerResult::roundScore).sum();
      if (round != null && results.size() < round.roundNo())
        score += round.scoreDetail().roundScore();
      return score;
    }

    long correctTimeMs() {
      long committed =
          results.stream()
              .filter(r -> r.answerTimeMs() != null)
              .mapToLong(r -> r.answerTimeMs())
              .sum();
      if (round != null
          && results.size() < round.roundNo()
          && round.outcome() == com.meloo.game.domain.RoundOutcome.CORRECT) {
        committed += java.time.Duration.between(round.startedAt(), round.endedAt()).toMillis();
      }
      return committed;
    }

    int correctCount() {
      int committed = (int) results.stream().filter(r -> r.correctLevel() != null).count();
      return committed
          + (round != null
                  && results.size() < round.roundNo()
                  && round.outcome() == com.meloo.game.domain.RoundOutcome.CORRECT
              ? 1
              : 0);
    }

    void restoreCombo() {
      combo = new ComboTracker(true);
      results.forEach(
          r -> combo.recordRound(r.outcome(), r.correctLevel() == null ? 0 : r.correctLevel()));
    }
  }
}
