package com.meloo.room.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchStarted;
import com.meloo.generated.model.*;
import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.message.*;
import com.meloo.room.lobby.RoomLobbyService;
import com.meloo.room.lobby.RoomPlayer;
import com.meloo.room.lobby.RoomSession;
import com.meloo.room.realtime.RoomEventPublisher;
import com.meloo.room.realtime.RoomRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RoomMatchServiceTest {
  static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
  final UUID host = UUID.randomUUID();
  final UUID guest = UUID.randomUUID();
  final UUID third = UUID.randomUUID();
  final Clock clock = mock(Clock.class);
  final AtomicReference<Instant> time = new AtomicReference<>(NOW);
  final TaskScheduler scheduler = mock(TaskScheduler.class);
  final Queue<Timer> timers = new ConcurrentLinkedQueue<>();
  final RoomRegistry registry = new RoomRegistry(scheduler);
  final RoomLobbyService lobby = mock(RoomLobbyService.class);
  final CatalogService catalog = mock(CatalogService.class);
  final AudioService audio = mock(AudioService.class);
  final RoomEventPublisher messages = mock(RoomEventPublisher.class);
  final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  final List<Object> events = new CopyOnWriteArrayList<>();
  final List<RoomMessage> sent = new CopyOnWriteArrayList<>();
  final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
  final List<TrackInfo> tracks = new ArrayList<>();
  RoomSession room;
  RoomMatchService service;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenAnswer(i -> time.get());
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              var cancelled = new AtomicBoolean();
              ScheduledFuture<?> future = mock(ScheduledFuture.class);
              when(future.cancel(anyBoolean()))
                  .thenAnswer(
                      i -> {
                        cancelled.set(true);
                        return true;
                      });
              timers.add(new Timer(invocation.getArgument(0), cancelled));
              return future;
            });
    doAnswer(
            i -> {
              sent.add(i.getArgument(2));
              return null;
            })
        .when(messages)
        .publish(any(), any(), any());
    doAnswer(
            i -> {
              sent.add(i.getArgument(2));
              return null;
            })
        .when(messages)
        .sendPrivate(any(), any(), any());
    doAnswer(
            i -> {
              events.add(i.getArgument(0));
              return null;
            })
        .when(publisher)
        .publishEvent(any(Object.class));
    for (int n = 0; n < 7; n++)
      tracks.add(
          new TrackInfo(
              UUID.randomUUID(),
              "Secret song " + n,
              "Secret artist " + n,
              "FAKE",
              "fake-" + n,
              null,
              null,
              null,
              null));
    when(catalog.selectRandomTracks(any(), anyInt(), anySet())).thenReturn(tracks.subList(0, 5));
    when(catalog.selectRandomTracksByPlaylist(any(), anyInt(), anySet()))
        .thenReturn(tracks.subList(0, 5));
    when(catalog.getTrackById(any()))
        .thenAnswer(i -> tracks.stream().filter(t -> t.id().equals(i.getArgument(0))).findFirst());
    when(audio.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());
    when(audio.isRoundPlayable(any())).thenReturn(true);
    when(audio.createAudioClip(any(), any(), anyInt()))
        .thenAnswer(
            i -> {
              AudioClipDto clip = new AudioClipDto();
              clip.setUrl("/api/v1/audio/opaque-token");
              clip.setExpiresAt(NOW.plusSeconds(120).atOffset(ZoneOffset.UTC));
              return clip;
            });
    room =
        new RoomSession(
            UUID.randomUUID(),
            "ABC123",
            RoomTypeDto.PRIVATE_ROOM,
            host,
            RoomStatusDto.LOBBY,
            5,
            5,
            null,
            null,
            NOW);
    add(host, true);
    add(guest, false);
    registry.register(new RoomDescriptor(room.roomId(), room.allMemberIds(), true));
    when(lobby.getRoom(room.roomId())).thenReturn(Optional.of(room));
    service =
        new RoomMatchService(
            lobby,
            registry,
            messages,
            catalog,
            audio,
            publisher,
            transactionManager,
            clock,
            io,
            new RoomMatchProperties(
                Duration.ofSeconds(5),
                Duration.ofSeconds(45),
                Duration.ofMillis(250),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)));
  }

  @AfterEach
  void close() {
    registry.close();
    io.close();
  }

  @Test
  void prepareWaitsForAllThenUsesServerClockAndKeepsAnswerPrivate() throws Exception {
    service.start(room.roomId(), host);
    awaitType(RoundPrepareEvent.class);
    assertThat(events).anyMatch(MatchStarted.class::isInstance);
    assertThat(types()).containsSubsequence("MATCH_STARTING", "ROUND_PREPARE", "ROUND_AUDIO_CLIP");
    assertThat(sent).noneMatch(RoundStartEvent.class::isInstance);
    service.prepared(room.roomId(), host, 1);
    barrier();
    assertThat(sent).noneMatch(RoundStartEvent.class::isInstance);
    service.prepared(room.roomId(), guest, 1);
    barrier();
    assertThat(last(RoundStartEvent.class).serverTime()).isEqualTo(NOW);
    assertThat(last(RoundStartEvent.class).durationMs()).isEqualTo(45_000);
    assertThat(sent.toString())
        .doesNotContain("Secret song", "Secret artist", tracks.getFirst().id().toString());
    assertThat(service.findActiveAnswerTitle(room.roomId())).contains(tracks.getFirst().title());
    fireTimer();
    barrier();
    assertThat(last(RoundEndEvent.class).answer().trackTitle())
        .isEqualTo(tracks.getFirst().title());
    assertThat(last(RoundEndEvent.class).answer().previewFullUrl())
        .isEqualTo("https://www.deezer.com/track/fake-0");
    service.resync(room.roomId(), guest);
    barrier();
    assertThat(last(ResyncStateMessage.class).lastRoundAnswer().previewFullUrl())
        .isEqualTo("https://www.deezer.com/track/fake-0");
  }

  @Test
  void accepts44999ButRejects45000EvenBeforeTimeoutAndAfterQueueDelay() throws Exception {
    start();
    CountDownLatch release = new CountDownLatch(1);
    var blocking =
        registry
            .actor(room.roomId())
            .orElseThrow()
            .execute(
                () -> {
                  try {
                    release.await(5, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                    throw new AssertionError(e);
                  }
                });
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusMillis(44_999));
    service.guess(
        room.roomId(), guest, new GuessCommand(1, tracks.getFirst().id()), NOW.plusMillis(45_000));
    time.set(NOW.plusSeconds(46));
    release.countDown();
    blocking.get();
    barrier();
    assertThat(last(PlayerGuessedEvent.class).userId()).isEqualTo(host);
    assertThat(last(PlayerGuessedEvent.class).roundScore()).isEqualTo(1000);
    assertThat(last(ErrorMessage.class).code()).isEqualTo("INVALID_STATE");
    assertThat(sent).noneMatch(RoundEndEvent.class::isInstance);
    fireTimer();
    barrier();
    assertThat(last(RoundEndEvent.class).results())
        .extracting(PlayerRoundResultPayload::outcome)
        .containsExactly("CORRECT", "TIMEOUT");
  }

  @Test
  void controllerStampsBeforeRoomEnqueue() {
    RoomMatchService mockService = mock(RoomMatchService.class);
    var controller = new RoomMatchStompController(mockService);
    GuessCommand command = new GuessCommand(1, tracks.getFirst().id());
    controller.guess(room.roomId(), command, () -> host.toString(), NOW);
    verify(mockService).guess(room.roomId(), host, command, NOW);
  }

  @Test
  void twoSimultaneousCorrectGuessesBothScoreAndRevealOnlyWhenEveryoneDone() throws Exception {
    start();
    CountDownLatch start = new CountDownLatch(1);
    try (var senders = Executors.newFixedThreadPool(2)) {
      for (UUID user : List.of(host, guest))
        senders.submit(
            () -> {
              try {
                start.await();
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
              service.guess(
                  room.roomId(),
                  user,
                  new GuessCommand(1, tracks.getFirst().id()),
                  NOW.plusSeconds(1));
            });
      start.countDown();
    }
    barrier();
    assertThat(sent.stream().filter(PlayerGuessedEvent.class::isInstance)).hasSize(2);
    assertThat(last(RoundEndEvent.class).results()).allMatch(p -> p.roundScore() == 1195);
    assertThat(last(ScoreboardUpdateEvent.class).scores())
        .allMatch(p -> p.totalScore() == 1195 && p.rank() == 1);
  }

  @Test
  void independentLevelsWrongGuessGiveUpAndComboUseEngine() throws Exception {
    start();
    service.listenMore(room.roomId(), host, 1, 2, NOW.plusMillis(10));
    barrier();
    service.resync(room.roomId(), guest);
    barrier();
    assertThat(last(ResyncStateMessage.class).currentRound().currentLevel()).isEqualTo(2);
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    service.guess(
        room.roomId(), guest, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    fireTimer();
    awaitRound(2);
    service.prepared(room.roomId(), host, 2);
    service.prepared(room.roomId(), guest, 2);
    barrier();
    service.guess(room.roomId(), host, new GuessCommand(2, tracks.get(1).id()), NOW.plusSeconds(2));
    barrier();
    assertThat(last(GuessResultMessage.class).scoreDetail().comboPercent()).isEqualTo(10);
    service.guess(
        room.roomId(), guest, new GuessCommand(2, tracks.get(6).id()), NOW.plusSeconds(2));
    barrier();
    assertThat(last(GuessResultMessage.class).nextLevel()).isEqualTo(5);
    assertThat(last(GuessResultMessage.class).nextAudioClip()).isNotNull();
    for (int i = 0; i < 3; i++)
      service.listenMore(room.roomId(), guest, 2, new int[] {5, 10, 20}[i], NOW.plusSeconds(3));
    service.giveUp(room.roomId(), guest, 2, NOW.plusSeconds(4));
    barrier();
    assertThat(last(RoundEndEvent.class).results().get(1).outcome()).isEqualTo("GAVE_UP");
    assertThat(last(ScoreboardUpdateEvent.class).scores().get(1).comboStreak()).isZero();
  }

  @Test
  void hostLeavesTransfersHostAndOnlyOneRemainingEndsMatchWithLeaverLast() throws Exception {
    start();
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    registry
        .actor(room.roomId())
        .orElseThrow()
        .execute(
            () -> {
              room.removePlayer(host);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      room.roomId(), host, RoomMembershipChanged.Change.LEFT));
            })
        .get();
    MatchFinished event =
        events.stream()
            .filter(MatchFinished.class::isInstance)
            .map(MatchFinished.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(room.hostId()).isEqualTo(guest);
    assertThat(last(MatchEarlyEndedEvent.class).reason()).isEqualTo("ONLY_ONE_PLAYER_REMAINED");
    assertThat(event.roomId()).isEqualTo(room.roomId());
    assertThat(event.players()).extracting(p -> p.userId()).containsExactly(guest, host);
    assertThat(event.players().getLast().left()).isTrue();
    assertThat(event.players().getLast().totalScore()).isEqualTo(1195);
    assertThat(event.players()).allMatch(p -> p.results().size() == 1);
    assertThat(event.ranked()).isFalse();
    fireTimer();
    barrier();
    assertThat(room.status()).isEqualTo(RoomStatusDto.LOBBY);
    assertThat(room.code()).isEqualTo("ABC123");
    assertThat(room.players().getFirst().isReady()).isTrue();
  }

  @Test
  void reconnectPreservesLevelAndMissedRoundScoresZero() throws Exception {
    start();
    service.listenMore(room.roomId(), guest, 1, 2, NOW.plusSeconds(1));
    barrier();
    registry
        .actor(room.roomId())
        .orElseThrow()
        .execute(
            () -> {
              room.findPlayer(guest).orElseThrow().setConnected(false);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      room.roomId(), guest, RoomMembershipChanged.Change.DISCONNECTED));
            })
        .get();
    service.resync(room.roomId(), guest);
    barrier();
    assertThat(last(ResyncStateMessage.class).currentRound().currentLevel()).isEqualTo(5);
    assertThat(last(ResyncStateMessage.class).lastRoundAnswer()).isNull();
    fireTimer();
    barrier();
    assertThat(last(RoundEndEvent.class).results().get(1).roundScore()).isZero();
    fireTimer();
    awaitRound(2);
    registry
        .actor(room.roomId())
        .orElseThrow()
        .execute(
            () -> {
              room.findPlayer(guest).orElseThrow().setConnected(true);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      room.roomId(), guest, RoomMembershipChanged.Change.CONNECTED));
            })
        .get();
    assertThat(last(ResyncStateMessage.class).currentRound().roundNo()).isEqualTo(2);
    assertThat(last(ResyncStateMessage.class).currentRound().currentLevel()).isEqualTo(2);
  }

  @Test
  void replacingFailedAudioRollsBackCurrentPointsAndKeepsOtherRoundsAndCombo() throws Exception {
    start();
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    when(audio.isRoundPlayable(any())).thenReturn(false);
    when(catalog.selectRandomTracks(any(), eq(1), anySet())).thenReturn(List.of(tracks.get(5)));
    service.resync(room.roomId(), guest);
    awaitType(RoundTrackReplacedEvent.class);
    when(audio.isRoundPlayable(any())).thenReturn(true);
    barrier();
    assertThat(last(ScoreboardUpdateEvent.class).scores())
        .allMatch(p -> p.totalScore() == 0 && p.comboStreak() == 0);
    assertThat(sent).noneMatch(RoundEndEvent.class::isInstance);
    service.prepared(room.roomId(), host, 1);
    service.prepared(room.roomId(), guest, 1);
    barrier();
    service.guess(room.roomId(), host, new GuessCommand(1, tracks.get(5).id()), NOW.plusSeconds(1));
    barrier();
    assertThat(last(GuessResultMessage.class).scoreDetail().comboPercent()).isZero();
    verify(audio, atLeastOnce()).releaseRoundAudio(any());
    verify(catalog)
        .selectRandomTracks(isNull(), eq(1), argThat(ids -> ids.contains(tracks.getFirst().id())));
  }

  @Test
  void prepareTimeoutStartsWithoutAcknowledgementAndAudioIoDoesNotBlockActor() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    when(audio.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenAnswer(
            i -> {
              assertThat(Thread.currentThread().getName()).doesNotStartWith("meloo-room-");
              release.await(5, TimeUnit.SECONDS);
              return AudioPreparationResult.success();
            });
    service.start(room.roomId(), host);
    barrier();
    registry.actor(room.roomId()).orElseThrow().submit(() -> 1).get(1, TimeUnit.SECONDS);
    release.countDown();
    awaitType(RoundPrepareEvent.class);
    fireTimer();
    barrier();
    assertThat(last(RoundStartEvent.class).roundNo()).isEqualTo(1);
  }

  @Test
  void rejectsNonHostNotReadyInvalidLevelRoundAndWaitingPlayer() throws Exception {
    service.start(room.roomId(), guest);
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("NOT_HOST");
    room.findPlayer(guest).orElseThrow().setReady(false);
    service.start(room.roomId(), host);
    barrier();
    assertThat(last(ErrorMessage.class).startFailureReason()).isEqualTo("NOT_ALL_READY");
    room.findPlayer(guest).orElseThrow().setReady(true);
    start();
    service.giveUp(room.roomId(), host, 1, NOW.plusSeconds(1));
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("INVALID_LEVEL");
    service.listenMore(room.roomId(), host, 2, 2, NOW.plusSeconds(1));
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("INVALID_ROUND_NO");
    add(third, false);
    service.guess(
        room.roomId(), third, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("INVALID_STATE");
  }

  @Test
  void usesPlaylistApiAndPublishesFullPayloadAfterFiveRounds() throws Exception {
    UUID playlist = UUID.randomUUID();
    room.setPlaylistId(playlist);
    start();
    for (int n = 1; n <= 5; n++) {
      if (n > 1) {
        fireTimer();
        awaitRound(n);
        service.prepared(room.roomId(), host, n);
        service.prepared(room.roomId(), guest, n);
        barrier();
      }
      service.guess(
          room.roomId(), host, new GuessCommand(n, tracks.get(n - 1).id()), NOW.plusSeconds(1));
      service.guess(
          room.roomId(), guest, new GuessCommand(n, tracks.get(n - 1).id()), NOW.plusSeconds(2));
      barrier();
    }
    MatchFinished event =
        events.stream()
            .filter(MatchFinished.class::isInstance)
            .map(MatchFinished.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(event.rounds()).hasSize(5);
    assertThat(event.players()).allMatch(p -> p.results().size() == 5);
    assertThat(event.players().getFirst().correctCount()).isEqualTo(5);
    assertThat(event.playlistId()).isEqualTo(playlist);
    verify(catalog).selectRandomTracksByPlaylist(eq(playlist), eq(5), anySet());
    verify(messages)
        .publish(
            eq(room.roomId()),
            eq(RoomEventPublisher.RoomTopic.SCOREBOARD),
            any(MatchFinishedEvent.class));
    fireTimer();
    barrier();
    assertThat(last(ReturnToLobbyEvent.class)).isNotNull();
  }

  @Test
  void laterDepartureRanksAboveEarlierDepartureRegardlessOfPoints() throws Exception {
    add(third, false);
    service.start(room.roomId(), host);
    awaitType(RoundPrepareEvent.class);
    service.prepared(room.roomId(), host, 1);
    service.prepared(room.roomId(), guest, 1);
    service.prepared(room.roomId(), third, 1);
    barrier();
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    for (UUID user : List.of(host, guest)) {
      registry
          .actor(room.roomId())
          .orElseThrow()
          .execute(
              () -> {
                room.removePlayer(user);
                service.membershipChanged(
                    new RoomMembershipChanged(
                        room.roomId(), user, RoomMembershipChanged.Change.LEFT));
              })
          .get();
    }
    assertThat(last(MatchFinishedEvent.class).rankings())
        .extracting(PlayerRankingPayload::userId)
        .containsExactly(third, guest, host);
  }

  @Test
  void quickMatchUsesRankedEventAndRechecksConnectedCount() throws Exception {
    registry.remove(room.roomId());
    room =
        new RoomSession(
            UUID.randomUUID(),
            null,
            RoomTypeDto.QUICK_MATCH,
            null,
            RoomStatusDto.LOBBY,
            5,
            5,
            null,
            null,
            NOW);
    add(host, false);
    add(guest, false);
    registry.register(new RoomDescriptor(room.roomId(), room.allMemberIds(), false));
    when(lobby.getRoom(room.roomId())).thenReturn(Optional.of(room));
    service.start(room.roomId(), host);
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("NOT_HOST");
    room.findPlayer(guest).orElseThrow().setConnected(false);
    service.startQuickMatch(room.roomId());
    barrier();
    assertThat(sent).noneMatch(RoundPrepareEvent.class::isInstance);
    room.findPlayer(guest).orElseThrow().setConnected(true);
    service.startQuickMatch(room.roomId());
    awaitType(RoundPrepareEvent.class);
    assertThat(events.stream().filter(MatchStarted.class::isInstance).map(MatchStarted.class::cast))
        .allMatch(MatchStarted::ranked);
    service.prepared(room.roomId(), host, 1);
    service.prepared(room.roomId(), guest, 1);
    barrier();
    registry
        .actor(room.roomId())
        .orElseThrow()
        .execute(
            () -> {
              room.removePlayer(guest);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      room.roomId(), guest, RoomMembershipChanged.Change.LEFT));
            })
        .get();
    MatchFinished event =
        events.stream()
            .filter(MatchFinished.class::isInstance)
            .map(MatchFinished.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(event.ranked()).isTrue();
    assertThat(event.type().name()).isEqualTo("QUICK_MATCH");
    assertThat(event.mode()).isEqualTo("RANKED");
  }

  @Test
  void duplicateListenMoreAndFinishedGuessCannotOpenMoreLevelsOrChangeScore() throws Exception {
    start();
    service.listenMore(room.roomId(), host, 1, 2, NOW.plusSeconds(1));
    service.listenMore(room.roomId(), host, 1, 2, NOW.plusSeconds(1));
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("INVALID_LEVEL");
    assertThat(sent.stream().filter(ListenMoreResponseMessage.class::isInstance)).hasSize(1);
    assertThat(last(ListenMoreResponseMessage.class).level()).isEqualTo(5);
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(2));
    barrier();
    int score = last(PlayerGuessedEvent.class).roundScore();
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(45));
    barrier();
    assertThat(last(ScoreboardUpdateEvent.class).scores().getFirst().totalScore()).isEqualTo(score);
    assertThat(sent.stream().filter(PlayerGuessedEvent.class::isInstance)).hasSize(1);
  }

  @Test
  void exhaustedReplacementFinishesWithOnlyCommittedRoundsAndNoPenalty() throws Exception {
    start();
    service.guess(
        room.roomId(), host, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    service.guess(
        room.roomId(), guest, new GuessCommand(1, tracks.getFirst().id()), NOW.plusSeconds(1));
    barrier();
    fireTimer();
    awaitRound(2);
    service.prepared(room.roomId(), host, 2);
    service.prepared(room.roomId(), guest, 2);
    barrier();
    service.guess(room.roomId(), host, new GuessCommand(2, tracks.get(1).id()), NOW.plusSeconds(1));
    barrier();
    when(audio.isRoundPlayable(any())).thenReturn(false);
    when(catalog.selectRandomTracks(any(), eq(1), anySet())).thenReturn(List.of());
    service.resync(room.roomId(), guest);
    awaitType(MatchFinishedEvent.class);
    barrier();
    MatchFinished event =
        events.stream()
            .filter(MatchFinished.class::isInstance)
            .map(MatchFinished.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(event.rounds()).hasSize(1);
    assertThat(event.players())
        .allMatch(p -> p.results().size() == 1 && p.totalScore() == 1195 && p.correctCount() == 1);
    assertThat(last(ErrorMessage.class).code()).isEqualTo("NOT_ENOUGH_TRACKS");
    assertThat(last(ScoreboardUpdateEvent.class).scores()).allMatch(p -> p.comboStreak() == 1);
    fireTimer();
    barrier();
    assertThat(room.status()).isEqualTo(RoomStatusDto.LOBBY);
  }

  @Test
  void missingInitialAudioReturnsToLobbyWithoutStartingMatch() throws Exception {
    when(audio.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("unavailable"));
    when(catalog.selectRandomTracks(any(), eq(1), anySet())).thenReturn(List.of());
    service.start(room.roomId(), host);
    awaitType(ReturnToLobbyEvent.class);
    barrier();
    assertThat(last(ErrorMessage.class).code()).isEqualTo("NOT_ENOUGH_TRACKS");
    assertThat(events).isEmpty();
    assertThat(room.status()).isEqualTo(RoomStatusDto.LOBBY);
  }

  @Test
  void correctArtistHintUsesContractResultAndSecondsWithoutRevealingAnswer() throws Exception {
    TrackInfo old = tracks.get(6);
    tracks.set(
        6,
        new TrackInfo(
            old.id(),
            old.title(),
            tracks.getFirst().artistName(),
            old.provider(),
            old.providerTrackId(),
            null,
            null,
            null,
            null));
    start();
    service.guess(room.roomId(), host, new GuessCommand(1, tracks.get(6).id()), NOW.plusSeconds(1));
    barrier();
    GuessResultMessage result = last(GuessResultMessage.class);
    assertThat(result.result()).isEqualTo("WRONG_ARTIST");
    assertThat(result.isCorrectArtist()).isTrue();
    assertThat(result.nextLevel()).isEqualTo(5);
    assertThat(result.nextAudioClip().level()).isEqualTo(5);
    assertThat(result.toString())
        .doesNotContain(tracks.getFirst().title(), tracks.getFirst().artistName());
  }

  void add(UUID user, boolean isHost) {
    room.addPlayer(new RoomPlayer(user, "Player " + user, null, isHost, true, false, true, 0, NOW));
  }

  void start() throws Exception {
    service.start(room.roomId(), host);
    awaitType(RoundPrepareEvent.class);
    service.prepared(room.roomId(), host, 1);
    service.prepared(room.roomId(), guest, 1);
    barrier();
    assertThat(last(RoundStartEvent.class)).isNotNull();
  }

  void barrier() throws Exception {
    registry.actor(room.roomId()).orElseThrow().submit(() -> null).get(5, TimeUnit.SECONDS);
  }

  void fireTimer() {
    Timer timer;
    do {
      timer = timers.remove();
    } while (timer.cancelled().get());
    timer.command().run();
  }

  <T> T last(Class<T> type) {
    return sent.reversed().stream()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow();
  }

  <T> void awaitType(Class<T> type) {
    await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(type::isInstance));
  }

  void awaitRound(int n) {
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                sent.stream().anyMatch(m -> m instanceof RoundPrepareEvent p && p.roundNo() == n));
  }

  List<String> types() {
    return sent.stream().map(RoomMessage::type).toList();
  }

  record Timer(Runnable command, AtomicBoolean cancelled) {}
}
