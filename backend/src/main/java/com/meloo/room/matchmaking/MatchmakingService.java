package com.meloo.room.matchmaking;

import com.meloo.generated.model.QuickMatchRequestDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.room.api.message.MatchCountdownEvent;
import com.meloo.room.api.message.MatchCountdownStoppedEvent;
import com.meloo.room.api.message.MatchSoloSuggestionEvent;
import com.meloo.room.lobby.RoomLobbyService;
import com.meloo.room.lobby.RoomPlayer;
import com.meloo.room.lobby.RoomSession;
import com.meloo.room.match.RoomMatchService;
import com.meloo.room.match.RoomMembershipChanged;
import com.meloo.room.realtime.RoomActor;
import com.meloo.room.realtime.RoomEventPublisher;
import com.meloo.room.realtime.RoomEventPublisher.RoomTopic;
import com.meloo.room.realtime.RoomRegistry;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * BR-13 timers and lifecycle. Internal events are synchronous and emitted on the room actor;
 * listeners must run there to observe every disconnect before a subsequent reconnect.
 */
@Service
@EnableConfigurationProperties(MatchmakingProperties.class)
public class MatchmakingService {
  private final RoomLobbyService lobby;
  private final RoomRegistry registry;
  private final RoomMatchService matches;
  private final RoomEventPublisher messages;
  private final MatchmakingProperties properties;
  private final Clock clock;
  private final ConcurrentMap<UUID, Waiting> waiting = new ConcurrentHashMap<>();

  public MatchmakingService(
      RoomLobbyService lobby,
      RoomRegistry registry,
      RoomMatchService matches,
      RoomEventPublisher messages,
      MatchmakingProperties properties,
      Clock clock) {
    this.lobby = lobby;
    this.registry = registry;
    this.matches = matches;
    this.messages = messages;
    this.properties = properties;
    this.clock = clock;
  }

  public RoomResponseDto quickMatch(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      boolean guest,
      @Nullable QuickMatchRequestDto request) {
    if (guest) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "QUICK_MATCH_REQUIRES_ACCOUNT",
          "Sign in to play a ranked quick match");
    }
    return lobby.quickMatch(userId, nickname, avatarUrl, false, request);
  }

  @EventListener
  public void lobbyChanged(QuickMatchLobbyChanged event) {
    registry
        .actor(event.roomId())
        .ifPresent(
            actor -> {
              if (event.returnedToLobby()) clear(event.roomId());
              reconcile(event.roomId(), actor, !event.returnedToLobby());
            });
  }

  @EventListener
  public void membershipChanged(RoomMembershipChanged event) {
    registry.actor(event.roomId()).ifPresent(actor -> reconcile(event.roomId(), actor, true));
  }

  private void reconcile(UUID roomId, RoomActor actor, boolean startWhenFull) {
    RoomSession room = lobby.getRoom(roomId).orElse(null);
    if (room == null || !room.isQuickMatch() || room.status() != RoomStatusDto.LOBBY) {
      clear(roomId);
      return;
    }
    var connected = room.players().stream().filter(RoomPlayer::isConnected).toList();
    Waiting state = waiting.computeIfAbsent(roomId, ignored -> new Waiting());
    if (connected.size() < 2) {
      state.starting = false;
      if (state.countdown != null) {
        state.countdown.cancel(false);
        cancel(state.tick);
        state.tick = null;
        state.countdown = null;
        state.countdownGeneration++;
        messages.publish(
            roomId,
            RoomTopic.LOBBY,
            new MatchCountdownStoppedEvent("NOT_ENOUGH_PLAYERS", clock.instant()));
      }
      if (connected.isEmpty()) {
        clear(roomId);
        return;
      }
      UUID alone = connected.getFirst().userId();
      if (!Objects.equals(state.alone, alone)) {
        cancel(state.suggestion);
        state.alone = alone;
        long generation = ++state.suggestionGeneration;
        state.suggestion =
            actor.schedule(
                properties.soloSuggestionDelay(),
                () -> {
                  if (waiting.get(roomId) != state
                      || state.suggestionGeneration != generation
                      || !Objects.equals(state.alone, alone)
                      || room.status() != RoomStatusDto.LOBBY) return;
                  var remaining = room.players().stream().filter(RoomPlayer::isConnected).toList();
                  if (remaining.size() == 1 && remaining.getFirst().userId().equals(alone)) {
                    messages.publish(
                        roomId,
                        RoomTopic.LOBBY,
                        new MatchSoloSuggestionEvent(
                            "Play solo or create a private room with the same filters to invite friends; "
                                + "these games do not affect rank. You can also keep waiting here.",
                            clock.instant()));
                  }
                });
      }
      return;
    }
    cancel(state.suggestion);
    state.suggestion = null;
    state.suggestionGeneration++;
    state.alone = null;
    if (state.starting) return;
    if (startWhenFull && connected.size() >= room.maxPlayers()) {
      start(roomId, state);
    } else if (state.countdown == null) {
      long generation = ++state.countdownGeneration;
      scheduleTick(roomId, actor, state, generation, clock.instant().plus(properties.countdown()));
      state.countdown =
          actor.schedule(
              properties.countdown(),
              () -> {
                if (waiting.get(roomId) != state || state.countdownGeneration != generation) return;
                // A cancelled callback may already be queued; identity guards it after re-entry.
                if (room.status() == RoomStatusDto.LOBBY
                    && room.players().stream().filter(RoomPlayer::isConnected).count() >= 2) {
                  start(roomId, state);
                } else {
                  reconcile(roomId, actor, false);
                }
              });
      messages.publish(
          roomId,
          RoomTopic.LOBBY,
          new MatchCountdownEvent(
              (int) Math.ceil(properties.countdown().toMillis() / 1000.0), clock.instant()));
    }
  }

  private void scheduleTick(
      UUID roomId, RoomActor actor, Waiting state, long generation, Instant deadline) {
    Duration left = Duration.between(clock.instant(), deadline);
    if (left.isNegative() || left.isZero()) return;
    state.tick =
        actor.schedule(
            left.compareTo(Duration.ofSeconds(1)) < 0 ? left : Duration.ofSeconds(1),
            () -> {
              if (waiting.get(roomId) != state || state.countdownGeneration != generation) return;
              Duration remaining = Duration.between(clock.instant(), deadline);
              if (remaining.isNegative() || remaining.isZero()) return;
              messages.publish(
                  roomId,
                  RoomTopic.LOBBY,
                  new MatchCountdownEvent(
                      (int) Math.ceil(remaining.toNanos() / 1_000_000_000.0), clock.instant()));
              scheduleTick(roomId, actor, state, generation, deadline);
            });
  }

  private void start(UUID roomId, Waiting state) {
    cancel(state.countdown);
    cancel(state.tick);
    state.tick = null;
    state.countdown = null;
    state.countdownGeneration++;
    state.starting = true;
    long generation = state.countdownGeneration;
    matches.startQuickMatch(
        roomId,
        () ->
            waiting.get(roomId) == state
                && state.starting
                && state.countdownGeneration == generation);
  }

  private void clear(UUID roomId) {
    Waiting state = waiting.remove(roomId);
    if (state != null) {
      cancel(state.countdown);
      cancel(state.tick);
      cancel(state.suggestion);
    }
  }

  private static void cancel(@Nullable ScheduledFuture<?> timer) {
    if (timer != null) timer.cancel(false);
  }

  private static final class Waiting {
    @Nullable ScheduledFuture<?> countdown;
    @Nullable ScheduledFuture<?> tick;
    @Nullable ScheduledFuture<?> suggestion;
    @Nullable UUID alone;
    boolean starting;
    long countdownGeneration;
    long suggestionGeneration;
  }
}
