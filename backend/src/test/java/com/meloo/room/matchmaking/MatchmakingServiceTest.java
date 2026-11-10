package com.meloo.room.matchmaking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.*;
import com.meloo.room.api.message.*;
import com.meloo.room.lobby.*;
import com.meloo.room.match.RoomMatchService;
import com.meloo.room.match.RoomMembershipChanged;
import com.meloo.room.realtime.*;
import com.meloo.shared.api.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

class MatchmakingServiceTest {
  final TaskScheduler scheduler = mock(TaskScheduler.class);
  final RoomRegistry registry = new RoomRegistry(scheduler);
  final RoomEventPublisher messages = mock(RoomEventPublisher.class);
  final RoomMatchService matches = mock(RoomMatchService.class);
  final CatalogService catalog = mock(CatalogService.class);
  final List<Timer> timers = new CopyOnWriteArrayList<>();
  final Instant now = Instant.parse("2026-11-10T12:00:00Z");
  final AtomicReference<Instant> time = new AtomicReference<>(now);
  final Clock clock = mock(Clock.class);
  RoomLobbyService lobby;
  MatchmakingService service;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenAnswer(i -> time.get());
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            i -> {
              ScheduledFuture<?> future = mock(ScheduledFuture.class);
              timers.add(new Timer(i.getArgument(0), i.getArgument(1), future));
              return future;
            });
    when(catalog.selectRandomTracks(any(), anyInt(), any()))
        .thenAnswer(
            i ->
                java.util.stream.IntStream.range(0, i.getArgument(1))
                    .mapToObj(
                        n ->
                            new TrackInfo(
                                UUID.randomUUID(),
                                "Secret song",
                                "Secret artist",
                                "DEEZER",
                                "id-" + n,
                                null,
                                null,
                                null,
                                null))
                    .toList());
    lobby =
        new RoomLobbyService(
            registry,
            messages,
            catalog,
            new RoomCodeGenerator(),
            new RoomLobbyProperties(2, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(30)),
            null,
            null);
    service =
        new MatchmakingService(
            lobby,
            registry,
            matches,
            messages,
            new MatchmakingProperties(Duration.ofSeconds(30), Duration.ofSeconds(60)),
            clock);
    ReflectionTestUtils.setField(
        lobby,
        "matchLifecyclePublisher",
        (ApplicationEventPublisher)
            event -> {
              if (event instanceof QuickMatchLobbyChanged changed) service.lobbyChanged(changed);
              if (event instanceof RoomMembershipChanged changed)
                service.membershipChanged(changed);
            });
  }

  @AfterEach
  void close() {
    registry.close();
  }

  @Test
  void guestIsRejectedBeforeCatalogOrRoomCreation() {
    assertThatThrownBy(() -> service.quickMatch(UUID.randomUUID(), "Guest", null, true, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(403);
              assertThat(e.code()).isEqualTo("QUICK_MATCH_REQUIRES_ACCOUNT");
            });
    verifyNoInteractions(catalog);
  }

  @Test
  void thirdPlayerDoesNotResetThirtySecondCountdown() throws Exception {
    var first = join(null);
    barrier(first.getRoomId());
    Timer solo = timers.getLast();
    join(null);
    barrier(first.getRoomId());
    Timer countdown = timers.getLast();
    assertDelay(countdown, 30);
    verify(solo.future()).cancel(false);
    join(null);
    barrier(first.getRoomId());
    assertThat(timers.getLast()).isSameAs(countdown);
    verify(messages)
        .publish(
            eq(first.getRoomId()),
            eq(RoomEventPublisher.RoomTopic.LOBBY),
            eq(new MatchCountdownEvent(30, now)));
    verifyNoInteractions(matches);
    fire(first.getRoomId(), countdown);
    verify(matches).startQuickMatch(eq(first.getRoomId()), any());
  }

  @Test
  void disconnectStopsCountdownAndStaleCallbackCannotStartAfterReconnect() throws Exception {
    var first = join(null);
    var second = join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    Timer old = timers.getLast();
    UUID other = second.getPlayers().getLast().getUserId();
    connection(roomId, other, false);
    verify(old.future()).cancel(false);
    verify(messages)
        .publish(
            eq(roomId),
            eq(RoomEventPublisher.RoomTopic.LOBBY),
            eq(new MatchCountdownStoppedEvent("NOT_ENOUGH_PLAYERS", now)));
    fire(roomId, old);
    verifyNoInteractions(matches);
    connection(roomId, other, true);
    Timer fresh = timers.getLast();
    assertThat(fresh).isNotSameAs(old);
    assertDelay(fresh, 30);
    fire(roomId, old);
    verifyNoInteractions(matches);
    fire(roomId, fresh);
    verify(matches).startQuickMatch(eq(roomId), any());
  }

  @Test
  void leavingSecondPlayerNeverStartsSoloAndStartsFreshCountdownForReplacement() throws Exception {
    var first = join(null);
    var second = join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    Timer old = timers.getLast();
    lobby.handleLeave(roomId, second.getPlayers().getLast().getUserId());
    barrier(roomId);
    fire(roomId, old);
    verifyNoInteractions(matches);
    assertThat(join(null).getRoomId()).isEqualTo(roomId);
    barrier(roomId);
    Timer fresh = timers.getLast();
    fire(roomId, old);
    verifyNoInteractions(matches);
    fire(roomId, fresh);
    verify(matches).startQuickMatch(eq(roomId), any());
  }

  @Test
  void sixtySecondsAloneSuggestsUnrankedAlternativesAndKeepsMembershipAndFilter() throws Exception {
    var request = new QuickMatchRequestDto();
    request.setFilter(new SoloFilterDto().genreCodes(List.of("POP")));
    var first = join(request);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    Timer suggestion = timers.getLast();
    assertDelay(suggestion, 60);
    fire(roomId, suggestion);
    var event = org.mockito.ArgumentCaptor.forClass(RoomMessage.class);
    verify(messages, atLeastOnce())
        .publish(eq(roomId), eq(RoomEventPublisher.RoomTopic.LOBBY), event.capture());
    assertThat(event.getAllValues())
        .filteredOn(MatchSoloSuggestionEvent.class::isInstance)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(((MatchSoloSuggestionEvent) e).message())
                  .contains(
                      "solo", "private room", "same filters", "do not affect rank", "keep waiting");
              assertThat(e.serverTime()).isEqualTo(now);
            });
    var snapshot =
        registry
            .actor(roomId)
            .orElseThrow()
            .submit(() -> lobby.getRoom(roomId).orElseThrow().toResponseDto())
            .get();
    assertThat(snapshot.getPlayerCount()).isEqualTo(1);
    assertThat(snapshot.getFilter().getGenreCodes()).containsExactly("POP");
    verifyNoInteractions(matches);
  }

  @Test
  void cancelledSoloSuggestionCannotFireAfterAnotherPlayerJoins() throws Exception {
    var first = join(null);
    barrier(first.getRoomId());
    Timer old = timers.getLast();
    join(null);
    barrier(first.getRoomId());
    fire(first.getRoomId(), old);
    verify(messages, never()).publish(any(), any(), isA(MatchSoloSuggestionEvent.class));
  }

  @Test
  void fullRoomStartsImmediatelyAndReturnsToFreshCountdown() throws Exception {
    var first = join(null);
    for (int i = 1; i < 5; i++) join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    verify(matches).startQuickMatch(eq(roomId), any());
    Timer old = timers.getLast();
    registry
        .actor(roomId)
        .orElseThrow()
        .execute(() -> service.lobbyChanged(new QuickMatchLobbyChanged(roomId, true)))
        .get();
    barrier(roomId);
    Timer fresh = timers.getLast();
    assertThat(fresh).isNotSameAs(old);
    assertDelay(fresh, 30);
    fire(roomId, old);
    verify(matches).startQuickMatch(eq(roomId), any());
    fire(roomId, fresh);
    verify(matches, times(2)).startQuickMatch(eq(roomId), any());
  }

  @Test
  void tenConcurrentRequestsCreateOnlyTwoHostlessCodelessRooms() throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<RoomResponseDto>> calls = new ArrayList<>();
      for (int i = 0; i < 10; i++)
        calls.add(
            executor.submit(
                () -> {
                  gate.await();
                  return join(null);
                }));
      gate.countDown();
      List<RoomResponseDto> results = new ArrayList<>();
      for (var call : calls) results.add(call.get(10, TimeUnit.SECONDS));
      var ids = results.stream().map(RoomResponseDto::getRoomId).distinct().toList();
      assertThat(ids).hasSize(2);
      for (UUID id : ids) {
        var snapshot =
            registry
                .actor(id)
                .orElseThrow()
                .submit(() -> lobby.getRoom(id).orElseThrow().toResponseDto())
                .get();
        assertThat(snapshot.getPlayerCount()).isEqualTo(5);
        assertThat(snapshot.getCode()).isNull();
        assertThat(snapshot.getHostId()).isNull();
        assertThat(snapshot.getRoomType()).isEqualTo(RoomTypeDto.QUICK_MATCH);
        assertThat(registry.find(id).orElseThrow().memberIds()).hasSize(5);
      }
    }
  }

  @Test
  void matchesEquivalentFiltersButNeverSelfCreatedPublicRooms() {
    var publicRoom =
        lobby.createRoom(
            UUID.randomUUID(),
            "Host",
            null,
            false,
            new CreateRoomRequestDto(CreateRoomRequestDto.RoomTypeEnum.PUBLIC_ROOM));
    var a =
        new QuickMatchRequestDto().filter(new SoloFilterDto().genreCodes(List.of("POP", "ROCK")));
    var b =
        new QuickMatchRequestDto()
            .filter(new SoloFilterDto().genreCodes(List.of("ROCK", "POP", "POP")));
    var c = new QuickMatchRequestDto().filter(new SoloFilterDto().genreCodes(List.of("JAZZ")));
    var first = join(a);
    assertThat(first.getRoomId()).isNotEqualTo(publicRoom.getRoomId());
    assertThat(join(b).getRoomId()).isEqualTo(first.getRoomId());
    assertThat(join(c).getRoomId()).isNotEqualTo(first.getRoomId());
  }

  @Test
  void immediateDisconnectReconnectInSameCommandStillRestartsCountdown() throws Exception {
    var first = join(null);
    var second = join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    Timer old = timers.getLast();
    UUID other = second.getPlayers().getLast().getUserId();
    registry
        .actor(roomId)
        .orElseThrow()
        .execute(
            () -> {
              var player = lobby.getRoom(roomId).orElseThrow().findPlayer(other).orElseThrow();
              player.setConnected(false);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      roomId, other, RoomMembershipChanged.Change.DISCONNECTED));
              player.setConnected(true);
              service.membershipChanged(
                  new RoomMembershipChanged(roomId, other, RoomMembershipChanged.Change.CONNECTED));
            })
        .get(5, TimeUnit.SECONDS);
    Timer fresh = timers.getLast();
    assertThat(fresh).isNotSameAs(old);
    fire(roomId, old);
    verifyNoInteractions(matches);
    fire(roomId, fresh);
    verify(matches).startQuickMatch(eq(roomId), any());
  }

  @Test
  void invalidTimerConfigurationFailsInsteadOfStartingUnboundedOrImmediateTimers() {
    for (Duration invalid :
        new Duration[] {
          null,
          Duration.ZERO,
          Duration.ofSeconds(-1),
          Duration.ofNanos(1),
          Duration.ofMillis((long) Integer.MAX_VALUE + 1)
        }) {
      assertThatThrownBy(() -> new MatchmakingProperties(invalid, Duration.ofSeconds(60)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new MatchmakingProperties(Duration.ofSeconds(30), invalid))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void idleRoomClosingBetweenLookupAndSubmissionDoesNotFailMatching() {
    RoomRegistry closingRegistry = spy(registry);
    var closingLobby =
        new RoomLobbyService(
            closingRegistry,
            messages,
            catalog,
            new RoomCodeGenerator(),
            new RoomLobbyProperties(2, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(30)),
            null,
            null);
    UUID old = closingLobby.createQuickMatchRoom(null, null, 10, 5).getRoomId();
    RoomActor staleActor = closingRegistry.actor(old).orElseThrow();
    registry.remove(old);
    doReturn(Optional.of(staleActor), Optional.empty()).when(closingRegistry).actor(old);
    var closingService =
        new MatchmakingService(
            closingLobby,
            closingRegistry,
            matches,
            messages,
            new MatchmakingProperties(Duration.ofSeconds(30), Duration.ofSeconds(60)),
            Clock.fixed(now, ZoneOffset.UTC));
    var result = closingService.quickMatch(UUID.randomUUID(), "Player", null, false, null);
    assertThat(result.getRoomId()).isNotEqualTo(old);
    assertThat(result.getPlayerCount()).isEqualTo(1);
  }

  @Test
  void alreadyQueuedStartIsInvalidatedByDepartureAndRejoin() throws Exception {
    var first = join(null);
    for (int i = 1; i < 5; i++) join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    var guard = org.mockito.ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);
    verify(matches).startQuickMatch(eq(roomId), guard.capture());
    var members = registry.find(roomId).orElseThrow().memberIds().stream().toList();
    for (UUID user : members.subList(0, 4)) lobby.handleLeave(roomId, user);
    barrier(roomId);
    join(null);
    barrier(roomId);
    assertThat(
            registry
                .actor(roomId)
                .orElseThrow()
                .submit(() -> guard.getValue().getAsBoolean())
                .get())
        .isFalse();
    assertDelay(timers.getLast(), 30);
  }

  @Test
  void serverTicksUseOriginalDeadlineAndIgnoreCancelledTicksAfterReconnect() throws Exception {
    var first = join(null);
    var second = join(null);
    UUID roomId = first.getRoomId();
    barrier(roomId);
    Timer tick = timers.get(timers.size() - 2);
    assertDelay(tick, 1);
    time.set(now.plusSeconds(1));
    fire(roomId, tick);
    verify(messages)
        .publish(
            eq(roomId),
            eq(RoomEventPublisher.RoomTopic.LOBBY),
            eq(new MatchCountdownEvent(29, time.get())));
    Timer next = timers.getLast();
    time.set(now.plusSeconds(5));
    fire(roomId, next);
    verify(messages)
        .publish(
            eq(roomId),
            eq(RoomEventPublisher.RoomTopic.LOBBY),
            eq(new MatchCountdownEvent(25, time.get())));
    Timer stale = timers.getLast();
    UUID other = second.getPlayers().getLast().getUserId();
    connection(roomId, other, false);
    connection(roomId, other, true);
    clearInvocations(messages);
    fire(roomId, stale);
    verify(messages, never()).publish(any(), any(), isA(MatchCountdownEvent.class));
  }

  RoomResponseDto join(QuickMatchRequestDto request) {
    return service.quickMatch(UUID.randomUUID(), "Player", null, false, request);
  }

  void connection(UUID roomId, UUID userId, boolean connected) throws Exception {
    registry
        .actor(roomId)
        .orElseThrow()
        .execute(
            () -> {
              lobby
                  .getRoom(roomId)
                  .orElseThrow()
                  .findPlayer(userId)
                  .orElseThrow()
                  .setConnected(connected);
              service.membershipChanged(
                  new RoomMembershipChanged(
                      roomId,
                      userId,
                      connected
                          ? RoomMembershipChanged.Change.CONNECTED
                          : RoomMembershipChanged.Change.DISCONNECTED));
            })
        .get(5, TimeUnit.SECONDS);
    barrier(roomId);
  }

  void barrier(UUID id) throws Exception {
    for (int i = 0; i < 2; i++)
      registry.actor(id).orElseThrow().execute(() -> {}).get(5, TimeUnit.SECONDS);
  }

  void fire(UUID roomId, Timer timer) throws Exception {
    timer.command().run();
    barrier(roomId);
  }

  void assertDelay(Timer timer, int seconds) {
    assertThat(Duration.between(Instant.now(), timer.at()).toMillis())
        .isBetween((seconds - 2L) * 1000, (seconds + 1L) * 1000);
  }

  record Timer(Runnable command, Instant at, ScheduledFuture<?> future) {}
}
