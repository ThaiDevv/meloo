package com.meloo.room.match;

import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.*;
import com.meloo.game.events.*;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.room.api.ActiveRoundQuery;
import com.meloo.room.api.message.*;
import com.meloo.room.lobby.RoomLobbyService;
import com.meloo.room.lobby.RoomPlayer;
import com.meloo.room.lobby.RoomSession;
import com.meloo.room.match.RoomMatch.Participant;
import com.meloo.room.realtime.RoomEventPublisher;
import com.meloo.room.realtime.RoomEventPublisher.PrivateQueue;
import com.meloo.room.realtime.RoomEventPublisher.RoomTopic;
import com.meloo.room.realtime.RoomRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates a match exclusively on the existing per-room command queue. */
@Service
public class RoomMatchService implements ActiveRoundQuery {
  private final RoomLobbyService lobby;
  private final RoomRegistry registry;
  private final RoomEventPublisher messages;
  private final CatalogService catalog;
  private final AudioService audio;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final Executor io;
  private final RoomMatchProperties properties;
  private final ConcurrentMap<UUID, RoomMatch> matches = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> activeAnswers = new ConcurrentHashMap<>();

  public RoomMatchService(
      RoomLobbyService lobby,
      RoomRegistry registry,
      RoomEventPublisher messages,
      CatalogService catalog,
      AudioService audio,
      ApplicationEventPublisher events,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      @Qualifier("clock") Clock clock,
      @Qualifier("roomMatchIoExecutor") Executor io,
      RoomMatchProperties properties) {
    this.lobby = lobby;
    this.registry = registry;
    this.messages = messages;
    this.catalog = catalog;
    this.audio = audio;
    this.events = events;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
    this.io = io;
    this.properties = properties;
  }

  public void start(UUID roomId, UUID userId) {
    registry
        .actor(roomId)
        .ifPresent(
            actor ->
                actor.execute(
                    () -> {
                      RoomSession room = lobby.getRoom(roomId).orElse(null);
                      if (room == null || !room.hasMember(userId)) return;
                      if (room.isQuickMatch() || !userId.equals(room.hostId())) {
                        error(userId, "NOT_HOST", "Only the host can start this room");
                        return;
                      }
                      startOnQueue(room, userId);
                    }));
  }

  /** Entry point for MEL-70; validates connected membership again on the actor queue. */
  public void startQuickMatch(UUID roomId) {
    registry
        .actor(roomId)
        .ifPresent(
            actor ->
                actor.execute(
                    () -> {
                      RoomSession room = lobby.getRoom(roomId).orElse(null);
                      if (room != null && room.isQuickMatch()) startOnQueue(room, null);
                    }));
  }

  private void startOnQueue(RoomSession room, @Nullable UUID requester) {
    if (room.status() != RoomStatusDto.LOBBY || matches.containsKey(room.roomId())) {
      if (requester != null) error(requester, "ROOM_NOT_LOBBY", "Room is not in the lobby");
      return;
    }
    long connected = room.players().stream().filter(RoomPlayer::isConnected).count();
    if (connected < 2
        || (!room.isQuickMatch() && room.players().stream().anyMatch(p -> !p.isReady()))) {
      if (requester != null)
        startError(requester, connected < 2 ? "NOT_ENOUGH_PLAYERS" : "NOT_ALL_READY", null);
      return;
    }
    var actor = registry.actor(room.roomId()).orElseThrow();
    RoomMatch match = new RoomMatch(room, actor);
    matches.put(room.roomId(), match);
    room.cancelIdleTimer();
    room.setStatus(RoomStatusDto.PLAYING);
    room.setCurrentRound(1);
    room.players().forEach(p -> p.setScore(0));
    // Capture settings on the actor; neither network I/O nor its callbacks mutate room state.
    UUID playlist = room.playlistId();
    TrackFilter filter = filter(room);
    int count = room.roundCount();
    CompletableFuture.supplyAsync(() -> select(playlist, filter, count, Set.of()), io)
        .whenComplete(
            (tracks, failure) ->
                onQueue(
                    match,
                    () -> {
                      if (failure != null || tracks == null || tracks.size() < count) {
                        preparationFailed(
                            match, requester, tracks == null ? count : count - tracks.size());
                        return;
                      }
                      match.tracks = List.copyOf(tracks);
                      tracks.forEach(t -> match.excluded.add(t.id()));
                      prepareAudio(match, false);
                    }));
  }

  public void prepared(UUID roomId, UUID userId, int roundNo) {
    command(
        roomId,
        userId,
        match -> {
          Participant player = participant(match, userId, roundNo, "ROUND_PREPARE");
          if (player == null || match.loading) return;
          if (!ensurePlayable(match)) return;
          player.prepared = true;
          if (match.players.values().stream()
              .filter(p -> !p.left() && p.lobbyPlayer.isConnected())
              .allMatch(p -> p.prepared)) startRound(match);
        });
  }

  /** The controller captures receivedAt before entering the room queue. */
  public void guess(UUID roomId, UUID userId, GuessCommand command, Instant receivedAt) {
    command(
        roomId,
        userId,
        match -> {
          Participant player = playableParticipant(match, userId, command.roundNo(), receivedAt);
          if (player == null || !ensurePlayable(match)) return;
          TrackInfo guess =
              command.trackId() == null
                  ? null
                  : catalog.getTrackById(command.trackId()).orElse(null);
          if (guess == null) {
            error(userId, "INVALID_TRACK", "Track is not available");
            return;
          }
          Round round = Objects.requireNonNull(player.round);
          GuessEvaluation result =
              round.submitGuess(guess.title(), guess.artistName(), receivedAt, player.combo);
          Instant now = clock.instant();
          AudioClipPayload next =
              result.roundEnded() ? null : clip(match, player, round.currentLevel());
          messages.sendPrivate(
              userId,
              PrivateQueue.PRIVATE,
              new GuessResultMessage(
                  match.roundNo,
                  result.correct()
                      ? "CORRECT"
                      : (result.roundEnded()
                          ? "WRONG_OUT"
                          : (result.rightArtist() ? "WRONG_ARTIST" : "WRONG")),
                  result.correct(),
                  result.rightArtist(),
                  result.correct() ? detail(round.scoreDetail()) : null,
                  result.roundEnded() ? null : seconds(round.currentLevel()),
                  next,
                  now));
          if (result.correct()) {
            messages.publish(
                roomId,
                RoomTopic.ROUND,
                new PlayerGuessedEvent(
                    userId,
                    player.lobbyPlayer.nickname(),
                    seconds(round.currentLevel()),
                    round.scoreDetail().roundScore(),
                    player.totalScore(),
                    now));
          } else if (!result.roundEnded()) {
            messages.publish(
                roomId,
                RoomTopic.ROUND,
                new ListenMoreOpenedEvent(userId, seconds(round.currentLevel()), now));
          }
          scoreboard(match);
          finishIfEveryoneDone(match);
        });
  }

  public void listenMore(
      UUID roomId, UUID userId, int roundNo, int currentLevel, Instant receivedAt) {
    command(
        roomId,
        userId,
        match -> {
          Participant player = playableParticipant(match, userId, roundNo, receivedAt);
          if (player == null || !ensurePlayable(match)) return;
          Round round = Objects.requireNonNull(player.round);
          if (currentLevel != seconds(round.currentLevel())
              || round.currentLevel() == round.gameRules().maxLevel()) {
            error(userId, "INVALID_LEVEL", "No further listening level");
            return;
          }
          round.listenMore();
          messages.sendPrivate(
              userId,
              PrivateQueue.PRIVATE,
              new ListenMoreResponseMessage(
                  roundNo,
                  seconds(round.currentLevel()),
                  clip(match, player, round.currentLevel()),
                  clock.instant()));
          messages.publish(
              roomId,
              RoomTopic.ROUND,
              new ListenMoreOpenedEvent(userId, seconds(round.currentLevel()), clock.instant()));
          scoreboard(match);
        });
  }

  public void giveUp(UUID roomId, UUID userId, int roundNo, Instant receivedAt) {
    command(
        roomId,
        userId,
        match -> {
          Participant player = playableParticipant(match, userId, roundNo, receivedAt);
          if (player == null || !ensurePlayable(match)) return;
          Round round = Objects.requireNonNull(player.round);
          if (round.currentLevel() != round.gameRules().maxLevel()) {
            error(userId, "INVALID_LEVEL", "Give up requires the last listening level");
            return;
          }
          round.skip(receivedAt, player.combo);
          messages.publish(
              roomId, RoomTopic.ROUND, new PlayerGaveUpEvent(userId, roundNo, clock.instant()));
          scoreboard(match);
          finishIfEveryoneDone(match);
        });
  }

  public void resync(UUID roomId, UUID userId) {
    registry
        .actor(roomId)
        .ifPresent(
            actor ->
                actor.execute(
                    () -> {
                      RoomSession room = lobby.getRoom(roomId).orElse(null);
                      if (room == null || !room.hasMember(userId)) return;
                      RoomMatch match = matches.get(roomId);
                      if (match != null
                          && !match.loading
                          && (match.phase.equals("ROUND_PREPARE")
                              || match.phase.equals("ROUND_IN_PROGRESS"))) {
                        if (!ensurePlayable(match)) return;
                      }
                      sendSnapshot(room, match, userId);
                    }));
  }

  @EventListener
  public void membershipChanged(RoomMembershipChanged change) {
    // The lobby publishes this synchronously while already on the shared room actor.
    RoomMatch match = matches.get(change.roomId());
    if (match == null) return;
    Participant player = match.players.get(change.userId());
    if (change.change() == RoomMembershipChanged.Change.CONNECTED) {
      if (!match.loading
          && (match.phase.equals("ROUND_PREPARE") || match.phase.equals("ROUND_IN_PROGRESS"))
          && !ensurePlayable(match)) return;
      sendSnapshot(match.room, match, change.userId());
      return;
    }
    if (player == null || player.left() || match.phase.equals("FINISHED")) return;
    if (change.change() == RoomMembershipChanged.Change.LEFT) {
      player.departure = ++match.departureSequence;
      List<Participant> remaining = match.players.values().stream().filter(p -> !p.left()).toList();
      if (remaining.size() <= 1) {
        if (!remaining.isEmpty())
          messages.publish(
              change.roomId(),
              RoomTopic.ROUND,
              new MatchEarlyEndedEvent(
                  "ONLY_ONE_PLAYER_REMAINED",
                  remaining.getFirst().lobbyPlayer.userId(),
                  clock.instant()));
        finishMatch(match);
      } else finishIfEveryoneDone(match);
    }
  }

  @Override
  public Optional<String> findActiveAnswerTitle(UUID roomId) {
    return Optional.ofNullable(activeAnswers.get(roomId));
  }

  private void prepareAudio(RoomMatch match, boolean replacement) {
    match.cancelTimer();
    activeAnswers.remove(match.room.roomId());
    match.loading = true;
    match.phase = "ROUND_PREPARE";
    match.answer = null;
    match.lastResults = List.of();
    match.roundStartedAt = null;
    match
        .players
        .values()
        .forEach(
            player -> {
              player.round = null;
              player.restoreCombo();
            });
    if (replacement) scoreboard(match);
    long generation = ++match.generation;
    UUID audioId = UUID.randomUUID();
    UUID previousId = match.audioRoundId;
    match.audioRoundId = audioId;
    TrackInfo initial = replacement ? null : match.tracks.get(match.roundNo - 1);
    UUID playlist = match.room.playlistId();
    TrackFilter filter = filter(match.room);
    Set<UUID> excluded = new HashSet<>(match.excluded);
    CompletableFuture.supplyAsync(
            () -> {
              if (previousId != null) audio.releaseRoundAudio(previousId);
              TrackInfo track = initial;
              boolean changed = replacement;
              // Bound external failures just as the solo service does; never penalize players for
              // I/O.
              for (int attempt = 0; attempt < (initial == null ? 5 : 6); attempt++) {
                if (track == null) {
                  List<TrackInfo> selected = select(playlist, filter, 1, excluded);
                  if (selected.isEmpty())
                    throw new IllegalStateException("No playable replacement");
                  track = selected.getFirst();
                }
                excluded.add(track.id());
                if (audio
                    .prepareRoundAudio(
                        audioId, track.id(), track.provider(), track.providerTrackId())
                    .playable()) return new PreparedAudio(track, changed, Set.copyOf(excluded));
                track = null;
                changed = true;
              }
              throw new IllegalStateException("Audio preparation exhausted");
            },
            io)
        .whenComplete(
            (prepared, failure) ->
                match
                    .actor
                    .execute(
                        () -> {
                          if (matches.get(match.room.roomId()) != match
                              || generation != match.generation
                              || match.phase.equals("FINISHED")) {
                            audio.releaseRoundAudio(audioId);
                            return;
                          }
                          if (failure != null || prepared == null) {
                            preparationFailed(match, match.room.hostId(), 1);
                            return;
                          }
                          match.track = prepared.track();
                          match.excluded.addAll(prepared.excluded());
                          match.loading = false;
                          if (match.startedAt == null) {
                            match.startedAt = clock.instant();
                            transaction.executeWithoutResult(
                                status ->
                                    events.publishEvent(
                                        new MatchStarted(
                                            UUID.randomUUID(),
                                            match.id,
                                            MatchType.valueOf(match.room.roomType().name()),
                                            match.room.isQuickMatch(),
                                            match.room.roundCount(),
                                            match.startedAt,
                                            List.copyOf(match.players.keySet()))));
                            messages.publish(
                                match.room.roomId(),
                                RoomTopic.LOBBY,
                                new MatchStartingEvent(0, clock.instant()));
                          }
                          if (prepared.changed()) {
                            messages.publish(
                                match.room.roomId(),
                                RoomTopic.ROUND,
                                new RoundTrackReplacedEvent(
                                    match.roundNo, "AUDIO_UNAVAILABLE", clock.instant()));
                          }
                          beginPrepare(match);
                        })
                    .whenComplete(
                        (ignored, queueFailure) -> {
                          if (queueFailure != null) audio.releaseRoundAudio(audioId);
                        }));
  }

  private void beginPrepare(RoomMatch match) {
    TrackInfo track = Objects.requireNonNull(match.track);
    GameRules rules =
        new GameRules(
            GameRules.DEFAULT_LEVELS,
            properties.roundDuration().toMillis(),
            properties.roundDuration().toMillis(),
            GameRules.DEFAULT_MAX_SPEED_BONUS);
    match.roundStartedAt = null;
    match.prepareDeadline = clock.instant().plus(properties.prepareTimeout());
    match
        .players
        .values()
        .forEach(
            player -> {
              player.restoreCombo();
              player.round =
                  new Round(
                      match.roundNo,
                      new TargetTrack(track.id(), track.title(), track.artistName()),
                      rules,
                      true);
              player.prepared = false;
              player.lobbyPlayer.setScore(player.totalScore());
            });
    activeAnswers.put(match.room.roomId(), track.title());
    messages.publish(
        match.room.roomId(),
        RoomTopic.ROUND,
        new RoundPrepareEvent(
            match.roundNo,
            match.room.roundCount(),
            (int) properties.prepareTimeout().toMillis(),
            clock.instant()));
    for (Participant player : match.players.values())
      if (!player.left()) {
        messages.sendPrivate(
            player.lobbyPlayer.userId(),
            PrivateQueue.PRIVATE,
            new RoundAudioClipMessage(match.roundNo, 2, clip(match, player, 1), clock.instant()));
      }
    scoreboard(match);
    long generation = match.generation;
    match.timer =
        match.actor.schedule(
            properties.prepareTimeout(),
            () -> {
              if (current(match, generation, "ROUND_PREPARE")) startRound(match);
            });
  }

  private void startRound(RoomMatch match) {
    if (!match.phase.equals("ROUND_PREPARE") || match.loading || !ensurePlayable(match)) return;
    match.cancelTimer();
    Instant now = clock.instant();
    match.phase = "ROUND_IN_PROGRESS";
    match.roundStartedAt = now;
    match
        .players
        .values()
        .forEach(
            p -> {
              Objects.requireNonNull(p.round).start(now);
              if (p.left()) p.round.timeout(now.plus(properties.roundDuration()), p.combo);
            });
    messages.publish(
        match.room.roomId(),
        RoomTopic.ROUND,
        new RoundStartEvent(match.roundNo, (int) properties.roundDuration().toMillis(), now));
    long generation = match.generation;
    match.timer =
        match.actor.schedule(
            properties.roundDuration().plus(properties.timeoutBuffer()),
            () -> {
              if (!current(match, generation, "ROUND_IN_PROGRESS") || !ensurePlayable(match))
                return;
              finishRound(match, false);
            });
  }

  private boolean ensurePlayable(RoomMatch match) {
    if (match.audioRoundId != null && audio.isRoundPlayable(match.audioRoundId)) return true;
    if (!match.loading) prepareAudio(match, true);
    return false;
  }

  private void finishIfEveryoneDone(RoomMatch match) {
    if (!match.phase.equals("ROUND_IN_PROGRESS") || match.loading) return;
    if (match.players.values().stream()
        .filter(p -> !p.left())
        .allMatch(p -> p.round != null && p.round.status() != RoundStatus.IN_PROGRESS))
      finishRound(match, false);
  }

  private void finishRound(RoomMatch match, boolean early) {
    match.cancelTimer();
    TrackInfo track = Objects.requireNonNull(match.track);
    Instant now = clock.instant();
    Instant deadline =
        Objects.requireNonNull(match.roundStartedAt).plus(properties.roundDuration());
    List<PlayerRoundResultPayload> results = new ArrayList<>();
    for (Participant player : match.players.values()) {
      Round round = Objects.requireNonNull(player.round);
      if (round.status() == RoundStatus.IN_PROGRESS)
        round.timeout(now.isBefore(deadline) ? deadline : now, player.combo);
      RoundScoreDetail score = round.scoreDetail();
      Integer level = round.outcome() == RoundOutcome.CORRECT ? round.currentLevel() : null;
      Long time =
          level == null
              ? null
              : Math.max(0, Duration.between(round.startedAt(), round.endedAt()).toMillis());
      RoundPlayerResult result =
          new RoundPlayerResult(
              match.roundNo,
              Objects.requireNonNull(round.outcome()),
              level,
              score.baseScore(),
              score.comboPercent(),
              score.speedBonus(),
              score.roundScore(),
              round.wrongCount(),
              time);
      player.results.add(result);
      results.add(
          new PlayerRoundResultPayload(
              player.lobbyPlayer.userId(),
              result.outcome().name(),
              level == null ? null : seconds(level),
              score.baseScore(),
              score.comboPercent(),
              score.speedBonus(),
              score.roundScore(),
              round.wrongCount(),
              time));
    }
    match.rounds.add(
        new RoundSummary(
            match.roundNo,
            track.id(),
            track.title(),
            track.artistName(),
            track.provider(),
            track.providerTrackId()));
    match.phase = "ROUND_REVEAL";
    activeAnswers.remove(match.room.roomId());
    match.answer =
        new RoundAnswerPayload(
            track.id(),
            track.title(),
            track.artistName(),
            null,
            track.coverUrl(),
            "https://www.deezer.com/track/" + track.providerTrackId());
    match.lastResults = List.copyOf(results);
    boolean last = early || match.roundNo == match.room.roundCount();
    messages.publish(
        match.room.roomId(),
        RoomTopic.ROUND,
        new RoundEndEvent(
            match.roundNo,
            match.answer,
            results,
            last ? null : now.plus(properties.revealDuration()),
            now));
    scoreboard(match);
    if (last) {
      publishFinished(match);
      return;
    }
    long generation = match.generation;
    match.timer =
        match.actor.schedule(
            properties.revealDuration(),
            () -> {
              if (!current(match, generation, "ROUND_REVEAL")) return;
              match.roundNo++;
              match.room.setCurrentRound(match.roundNo);
              prepareAudio(match, false);
            });
  }

  private void finishMatch(RoomMatch match) {
    if (match.published) return;
    if (match.phase.equals("ROUND_IN_PROGRESS") && !match.loading) {
      finishRound(match, true);
      return;
    }
    publishFinished(match);
  }

  private void publishFinished(RoomMatch match) {
    match.cancelTimer();
    match.generation++;
    activeAnswers.remove(match.room.roomId());
    if (match.startedAt == null) {
      returnToLobby(match);
      return;
    }
    List<PlayerRankingPayload> rankings = rankings(match);
    List<PlayerSummary> players =
        rankings.stream()
            .map(
                rank -> {
                  Participant player = match.players.get(rank.userId());
                  return new PlayerSummary(
                      rank.userId(),
                      rank.nickname(),
                      player.lobbyPlayer.isGuest(),
                      rank.totalScore(),
                      rank.rank(),
                      rank.correctCount(),
                      rank.totalCorrectTimeMs(),
                      player.results,
                      rank.left());
                })
            .toList();
    MatchFinished finished =
        new MatchFinished(
            UUID.randomUUID(),
            match.id,
            MatchType.valueOf(match.room.roomType().name()),
            match.room.isQuickMatch() ? "RANKED" : "PRACTICE",
            match.room.isQuickMatch(),
            MatchEndStatus.FINISHED,
            match.room.roundCount(),
            match.room.playlistId(),
            match.room.filter() == null
                ? null
                : tools.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .writeValueAsString(match.room.filter()),
            match.startedAt,
            clock.instant(),
            match.rounds,
            players,
            match.room.roomId());
    transaction.executeWithoutResult(status -> events.publishEvent(finished));
    match.published = true;
    match.loading = false;
    match.phase = "FINISHED";
    match.room.setStatus(RoomStatusDto.FINISHED);
    messages.publish(
        match.room.roomId(),
        RoomTopic.SCOREBOARD,
        new MatchFinishedEvent(match.id, rankings, clock.instant()));
    match.timer = match.actor.schedule(properties.podiumDuration(), () -> returnToLobby(match));
  }

  private void returnToLobby(RoomMatch match) {
    match.cancelTimer();
    matches.remove(match.room.roomId(), match);
    activeAnswers.remove(match.room.roomId());
    match.generation++;
    if (match.audioRoundId != null) audio.releaseRoundAudio(match.audioRoundId);
    match.room.setStatus(RoomStatusDto.LOBBY);
    match.room.setCurrentRound(0);
    match.room.promoteWaitingPlayers();
    messages.publish(match.room.roomId(), RoomTopic.LOBBY, new ReturnToLobbyEvent(clock.instant()));
  }

  private void preparationFailed(RoomMatch match, @Nullable UUID requester, int missing) {
    if (match.startedAt == null) {
      if (requester != null) startError(requester, "NOT_ENOUGH_TRACKS", missing);
      returnToLobby(match);
    } else {
      match.players.values().stream()
          .filter(p -> !p.left())
          .forEach(
              p ->
                  error(
                      p.lobbyPlayer.userId(),
                      "NOT_ENOUGH_TRACKS",
                      "No playable replacement remains"));
      finishMatch(match);
    }
  }

  private void startError(UUID userId, String reason, @Nullable Integer missing) {
    messages.sendPrivate(
        userId,
        PrivateQueue.ERRORS,
        new ErrorMessage(
            reason.equals("NOT_ENOUGH_TRACKS") ? "NOT_ENOUGH_TRACKS" : "ROOM_CANNOT_START",
            "Room cannot start",
            null,
            reason,
            missing,
            clock.instant()));
  }

  private void error(UUID userId, String code, String message) {
    messages.sendPrivate(
        userId, PrivateQueue.ERRORS, new ErrorMessage(code, message, null, clock.instant()));
  }

  private void command(UUID roomId, UUID userId, Consumer<RoomMatch> command) {
    registry
        .actor(roomId)
        .ifPresent(
            actor ->
                actor.execute(
                    () -> {
                      RoomMatch match = matches.get(roomId);
                      if (match == null) {
                        error(userId, "INVALID_STATE", "No active match");
                        return;
                      }
                      if (!match.room.hasMember(userId)) {
                        error(userId, "NOT_IN_ROOM", "Room membership required");
                        return;
                      }
                      command.accept(match);
                    }));
  }

  private @Nullable Participant participant(
      RoomMatch match, UUID userId, int roundNo, String phase) {
    Participant player = match.players.get(userId);
    if (match.roundNo != roundNo) {
      error(userId, "INVALID_ROUND_NO", "Round has changed");
      return null;
    }
    if (player == null || player.left() || !match.phase.equals(phase) || match.loading) {
      error(userId, "INVALID_STATE", "Command is not valid in the current phase");
      return null;
    }
    return player;
  }

  private @Nullable Participant playableParticipant(
      RoomMatch match, UUID userId, int roundNo, Instant receivedAt) {
    Participant player = participant(match, userId, roundNo, "ROUND_IN_PROGRESS");
    if (player == null) return null;
    Round round = Objects.requireNonNull(player.round);
    Instant start = Objects.requireNonNull(round.startedAt());
    if (round.status() != RoundStatus.IN_PROGRESS
        || receivedAt.isBefore(start)
        || !receivedAt.isBefore(start.plusMillis(round.gameRules().timeoutCutoffMs()))) {
      error(userId, "INVALID_STATE", "Round is finished or its deadline has passed");
      return null;
    }
    return player;
  }

  private void onQueue(RoomMatch match, Runnable work) {
    match.actor.execute(
        () -> {
          if (matches.get(match.room.roomId()) == match && !match.phase.equals("FINISHED"))
            work.run();
        });
  }

  private boolean current(RoomMatch match, long generation, String phase) {
    return matches.get(match.room.roomId()) == match
        && match.generation == generation
        && match.phase.equals(phase);
  }

  private List<TrackInfo> select(
      @Nullable UUID playlist, @Nullable TrackFilter filter, int count, Set<UUID> excluded) {
    return playlist == null
        ? catalog.selectRandomTracks(filter, count, excluded)
        : catalog.selectRandomTracksByPlaylist(playlist, count, excluded);
  }

  private static @Nullable TrackFilter filter(RoomSession room) {
    RoomFilterPayload filter = room.filter();
    return filter == null
        ? null
        : new TrackFilter(
            filter.genreCodes(), filter.countryCodes(), filter.decades(), filter.artistIds());
  }

  private AudioClipPayload clip(RoomMatch match, Participant player, int level) {
    var clip =
        audio.createAudioClip(
            Objects.requireNonNull(match.audioRoundId), player.lobbyPlayer.userId(), level);
    return new AudioClipPayload(clip.getUrl(), seconds(level), clip.getExpiresAt().toInstant());
  }

  private static int seconds(int index) {
    return GameRules.DEFAULT_LEVELS.get(index - 1).durationSeconds();
  }

  private static RoundScoreDetailPayload detail(RoundScoreDetail score) {
    return new RoundScoreDetailPayload(
        score.baseScore(), score.comboPercent(), score.speedBonus(), score.roundScore());
  }

  private List<PlayerRankingPayload> rankings(RoomMatch match) {
    List<Participant> survivors = match.players.values().stream().filter(p -> !p.left()).toList();
    List<MatchRanking.RankedPlayer> ranked =
        MatchRanking.rank(
            survivors.stream()
                .map(
                    p ->
                        new MatchRanking.PlayerScore(
                            p.lobbyPlayer.userId(),
                            p.lobbyPlayer.nickname(),
                            p.lobbyPlayer.isGuest(),
                            p.totalScore(),
                            p.correctTimeMs(),
                            p.correctCount()))
                .toList());
    List<PlayerRankingPayload> result =
        new ArrayList<>(
            ranked.stream()
                .map(
                    p ->
                        new PlayerRankingPayload(
                            p.rank(),
                            Objects.requireNonNull(p.userId()),
                            p.nickname(),
                            p.totalScore(),
                            p.correctCount(),
                            p.correctAnswerTimeMs(),
                            false))
                .toList());
    match.players.values().stream()
        .filter(Participant::left)
        .sorted(Comparator.comparingLong((Participant p) -> p.departure).reversed())
        .forEach(
            p ->
                result.add(
                    new PlayerRankingPayload(
                        result.size() + 1,
                        p.lobbyPlayer.userId(),
                        p.lobbyPlayer.nickname(),
                        p.totalScore(),
                        p.correctCount(),
                        p.correctTimeMs(),
                        true)));
    return List.copyOf(result);
  }

  private List<PlayerScoreSummaryPayload> scores(RoomMatch match) {
    return rankings(match).stream()
        .map(
            p ->
                new PlayerScoreSummaryPayload(
                    p.userId(),
                    p.nickname(),
                    p.totalScore(),
                    match.players.get(p.userId()).combo.currentStreak(),
                    p.rank()))
        .toList();
  }

  private void scoreboard(RoomMatch match) {
    match.players.values().forEach(p -> p.lobbyPlayer.setScore(p.totalScore()));
    messages.publish(
        match.room.roomId(),
        RoomTopic.SCOREBOARD,
        new ScoreboardUpdateEvent(scores(match), clock.instant()));
  }

  private void sendSnapshot(RoomSession room, @Nullable RoomMatch match, UUID userId) {
    Instant now = clock.instant();
    RoomResponsePayload snapshot =
        new RoomResponsePayload(
            room.roomId(),
            room.code(),
            room.roomType().name(),
            room.hostId(),
            room.status().name(),
            room.roundCount(),
            room.maxPlayers(),
            room.currentRound(),
            room.playerCount(),
            java.util.stream.Stream.concat(room.players().stream(), room.waitingPlayers().stream())
                .map(RoomPlayer::toPayload)
                .toList(),
            room.playlistId(),
            room.filter(),
            room.createdAt());
    ActiveRoundStatePayload currentRound = null;
    if (match != null
        && !match.loading
        && (match.phase.equals("ROUND_PREPARE") || match.phase.equals("ROUND_IN_PROGRESS"))) {
      Participant player = match.players.get(userId);
      if (player != null && !player.left() && player.round != null) {
        Instant deadline =
            match.roundStartedAt == null
                ? match.prepareDeadline
                : match.roundStartedAt.plus(properties.roundDuration());
        int remaining =
            (int) Math.max(0, Duration.between(now, Objects.requireNonNull(deadline)).toMillis());
        currentRound =
            new ActiveRoundStatePayload(
                match.roundNo,
                room.roundCount(),
                seconds(player.round.currentLevel()),
                clip(match, player, player.round.currentLevel()),
                remaining,
                player.round.status() != RoundStatus.IN_PROGRESS
                    && player.round.status() != RoundStatus.WAITING);
      }
    }
    boolean reveal =
        match != null && (match.phase.equals("ROUND_REVEAL") || match.phase.equals("FINISHED"));
    messages.sendPrivate(
        userId,
        PrivateQueue.PRIVATE,
        new ResyncStateMessage(
            snapshot,
            match == null ? "LOBBY" : match.phase,
            currentRound,
            match == null ? List.of() : scores(match),
            reveal ? match.answer : null,
            reveal ? match.lastResults : null,
            now));
  }

  private record PreparedAudio(TrackInfo track, boolean changed, Set<UUID> excluded) {}
}
