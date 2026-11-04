package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.PlaylistInfo;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.CreateRoomRequestDto;
import com.meloo.generated.model.CreateRoomRequestDto.RoomTypeEnum;
import com.meloo.generated.model.CreateRoomRequestDto.RoundCountEnum;
import com.meloo.generated.model.PublicRoomPageDto;
import com.meloo.generated.model.QuickMatchRequestDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.generated.model.RoomTypeDto;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.room.api.message.ErrorMessage;
import com.meloo.room.api.message.KickCommand;
import com.meloo.room.api.message.PlayerJoinedEvent;
import com.meloo.room.api.message.PlayerKickedEvent;
import com.meloo.room.api.message.PlayerLeftEvent;
import com.meloo.room.api.message.ReadyCommand;
import com.meloo.room.api.message.RoomSettingsChangedEvent;
import com.meloo.room.api.message.SettingsCommand;
import com.meloo.room.api.message.WaitingListChangedEvent;
import com.meloo.room.realtime.RoomEventPublisher;
import com.meloo.room.realtime.RoomEventPublisher.PrivateQueue;
import com.meloo.room.realtime.RoomEventPublisher.RoomTopic;
import com.meloo.room.realtime.RoomRegistry;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RoomLobbyServiceTest {

  private RoomRegistry roomRegistry;
  private RoomEventPublisher eventPublisher;
  private CatalogService catalogService;
  private RoomCodeGenerator codeGenerator;
  private RoomLobbyProperties properties;
  private Clock clock;
  private ThreadPoolTaskScheduler scheduler;

  private RoomLobbyService service;

  private final Instant fixedNow = Instant.parse("2026-11-04T10:00:00Z");

  @BeforeEach
  void setUp() {
    scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.initialize();

    roomRegistry = new RoomRegistry(scheduler);
    eventPublisher = mock(RoomEventPublisher.class);
    catalogService = mock(CatalogService.class);
    codeGenerator = new RoomCodeGenerator();
    properties = new RoomLobbyProperties(2, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(30));
    clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    service =
        new RoomLobbyService(
            roomRegistry, eventPublisher, catalogService, codeGenerator, properties, clock);

    // Default catalog mock: always return enough tracks
    when(catalogService.selectRandomTracks(any(), anyInt(), any()))
        .thenAnswer(
            invocation -> {
              int count = invocation.getArgument(1);
              List<TrackInfo> tracks = new ArrayList<>();
              for (int i = 0; i < count; i++) {
                tracks.add(
                    new TrackInfo(
                        UUID.randomUUID(),
                        "Track " + i,
                        "Artist " + i,
                        "DEEZER",
                        "ext-" + i,
                        "cover.jpg",
                        null,
                        "VN",
                        (short) 2024));
              }
              return tracks;
            });
  }

  @AfterEach
  void tearDown() {
    roomRegistry.close();
    scheduler.shutdown();
  }

  @Test
  @DisplayName("Tạo phòng riêng thành công, mã 6 ký tự đúng chuẩn BR-11")
  void createPrivateRoomSuccess() {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto request = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    request.setRoundCount(RoundCountEnum.NUMBER_10);
    request.setMaxPlayers(4);

    RoomResponseDto response = service.createRoom(hostId, "HostPlayer", null, false, request);

    assertThat(response.getRoomId()).isNotNull();
    assertThat(response.getCode()).hasSize(6).matches("^[A-HJ-NP-Z2-9]{6}$");
    assertThat(response.getHostId()).isEqualTo(hostId);
    assertThat(response.getStatus()).isEqualTo(RoomStatusDto.LOBBY);
    assertThat(response.getRoundCount().getValue()).isEqualTo(10);
    assertThat(response.getMaxPlayers()).isEqualTo(4);
    assertThat(response.getPlayerCount()).isEqualTo(1);
    assertThat(response.getPlayers()).hasSize(1);
    assertThat(response.getPlayers().get(0).getIsHost()).isTrue();
    assertThat(response.getPlayers().get(0).getNickname()).isEqualTo("HostPlayer");

    assertThat(roomRegistry.find(response.getRoomId())).isPresent();
    assertThat(roomRegistry.find(response.getRoomId()).get().hasMember(hostId)).isTrue();
  }

  @Test
  @DisplayName("Tạo phòng từ chối khi cả playlistId và filter đều có")
  void createRoomRejectsBothPlaylistAndFilter() {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto request = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    request.setPlaylistId(UUID.randomUUID());
    request.setFilter(new SoloFilterDto());

    assertThatThrownBy(() -> service.createRoom(hostId, "HostPlayer", null, false, request))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(apiEx.code()).isEqualTo("VALIDATION_FAILED");
            });
  }

  @Test
  @DisplayName("Tạo phòng ném 422 khi kho nhạc không đủ bài cho số vòng (BR-09)")
  void createRoomThrows422WhenNotEnoughTracks() {
    UUID hostId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    when(catalogService.findPlaylistById(playlistId))
        .thenReturn(Optional.of(new PlaylistInfo(playlistId, "vpop", "V-Pop Hits", true, 3)));
    when(catalogService.selectRandomTracksByPlaylist(eq(playlistId), eq(10), any()))
        .thenReturn(
            List.of(
                new TrackInfo(
                    UUID.randomUUID(), "T1", "A1", "DEEZER", "e1", "c", null, "VN", (short) 2024),
                new TrackInfo(
                    UUID.randomUUID(), "T2", "A2", "DEEZER", "e2", "c", null, "VN", (short) 2024)));

    CreateRoomRequestDto request = new CreateRoomRequestDto(RoomTypeEnum.PUBLIC_ROOM);
    request.setPlaylistId(playlistId);
    request.setRoundCount(RoundCountEnum.NUMBER_10);

    assertThatThrownBy(() -> service.createRoom(hostId, "HostPlayer", null, false, request))
        .isInstanceOf(RoomNotEnoughTracksException.class)
        .satisfies(
            ex -> {
              RoomNotEnoughTracksException rne = (RoomNotEnoughTracksException) ex;
              assertThat(rne.missingTracks()).isEqualTo(8);
            });
  }

  @Test
  @DisplayName("Vào phòng bằng mã thành công và phát sự kiện PlayerJoinedEvent")
  void joinRoomByCodeSuccess() {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto request = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    RoomResponseDto created = service.createRoom(hostId, "Host", null, false, request);

    UUID player2Id = UUID.randomUUID();
    RoomResponseDto joined =
        service.joinRoomByCode(player2Id, "Player2", "avatar2.png", true, created.getCode());

    assertThat(joined.getPlayerCount()).isEqualTo(2);
    assertThat(joined.getPlayers()).extracting("userId").containsExactly(hostId, player2Id);

    ArgumentCaptor<PlayerJoinedEvent> eventCaptor =
        ArgumentCaptor.forClass(PlayerJoinedEvent.class);
    verify(eventPublisher)
        .publish(eq(created.getRoomId()), eq(RoomTopic.LOBBY), eventCaptor.capture());
    PlayerJoinedEvent event = eventCaptor.getValue();
    assertThat(event.user().userId()).isEqualTo(player2Id);
    assertThat(event.user().nickname()).isEqualTo("Player2");
    assertThat(event.user().isHost()).isFalse();
    assertThat(event.user().isGuest()).isTrue();
    assertThat(event.serverTime()).isEqualTo(fixedNow);
  }

  @Test
  @DisplayName("Vào phòng bằng mã từ chối 409 khi phòng đã đầy (BR-11, BR-15)")
  void joinRoomRejectsWhenFull() {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto request = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    request.setMaxPlayers(2);
    RoomResponseDto created = service.createRoom(hostId, "Host", null, false, request);

    UUID player2Id = UUID.randomUUID();
    service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    UUID player3Id = UUID.randomUUID();
    assertThatThrownBy(
            () -> service.joinRoomByCode(player3Id, "Player3", null, false, created.getCode()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(apiEx.code()).isEqualTo("ROOM_FULL");
            });
  }

  @Test
  @DisplayName("Phòng QUICK_MATCH không cho vào bằng mã hoặc link (BR-13)")
  void quickMatchRoomCannotBeJoinedByCode() {
    RoomResponseDto qm = service.createQuickMatchRoom(null, null, 10, 5);
    assertThat(qm.getCode()).isNull();
    assertThat(qm.getHostId()).isNull();

    assertThatThrownBy(
            () -> service.joinRoomByCode(UUID.randomUUID(), "P", null, false, "NOTEXIST"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("Người bị chủ phòng mời ra không được vào lại phòng đó (BR-12)")
  void kickedPlayerCannotRejoin() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));

    UUID player2Id = UUID.randomUUID();
    service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    // Host kicks player2
    service.handleKick(created.getRoomId(), hostId, new KickCommand(player2Id));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(eventPublisher)
                    .publish(
                        eq(created.getRoomId()),
                        eq(RoomTopic.LOBBY),
                        any(PlayerKickedEvent.class)));

    // Player2 tries to rejoin
    assertThatThrownBy(
            () -> service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(apiEx.code()).isEqualTo("ROOM_KICKED");
            });
  }

  @Test
  @DisplayName("Vào khi trận đang diễn ra thì được xếp vào hàng chờ sảnh (BR-15)")
  void joinWhilePlayingIsQueuedInWaitingList() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));

    RoomSession session = service.getRoom(created.getRoomId()).orElseThrow();
    session.setStatus(RoomStatusDto.PLAYING);

    UUID player2Id = UUID.randomUUID();
    RoomResponseDto response =
        service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    assertThat(response.getPlayerCount()).isEqualTo(2);
    assertThat(session.waitingPlayers()).extracting("userId").contains(player2Id);

    verify(eventPublisher)
        .publish(eq(created.getRoomId()), eq(RoomTopic.LOBBY), any(WaitingListChangedEvent.class));
  }

  @Test
  @DisplayName("Lệnh sẵn sàng, chỉnh cài đặt, mời ra gửi vào phòng QUICK_MATCH bị từ chối rõ ràng")
  void quickMatchRejectsReadySettingsAndKick() {
    RoomResponseDto qm = service.createQuickMatchRoom(null, null, 10, 5);
    UUID userId = UUID.randomUUID();

    service.handleReady(qm.getRoomId(), userId, new ReadyCommand(true));
    service.handleSettings(qm.getRoomId(), userId, new SettingsCommand(5, 4, null, null));
    service.handleKick(qm.getRoomId(), userId, new KickCommand(UUID.randomUUID()));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(eventPublisher, times(3))
                    .sendPrivate(eq(userId), eq(PrivateQueue.ERRORS), any(ErrorMessage.class)));
  }

  @Test
  @DisplayName("Chủ phòng rời thì quyền chủ phòng chuyển giao cho người vào sớm nhất (BR-12)")
  void hostLeavesTransfersToEarliestJoinedPlayer() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));

    UUID player2Id = UUID.randomUUID();
    UUID player3Id = UUID.randomUUID();
    service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());
    service.joinRoomByCode(player3Id, "Player3", null, false, created.getCode());

    service.handleLeave(created.getRoomId(), hostId);

    ArgumentCaptor<PlayerLeftEvent> captor = ArgumentCaptor.forClass(PlayerLeftEvent.class);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(eventPublisher)
                    .publish(eq(created.getRoomId()), eq(RoomTopic.LOBBY), captor.capture()));
    PlayerLeftEvent event = captor.getValue();

    assertThat(event.userId()).isEqualTo(hostId);
    assertThat(event.newHostId()).isEqualTo(player2Id);
    assertThat(event.reason()).isEqualTo("VOLUNTARY");

    RoomSession session = service.getRoom(created.getRoomId()).orElseThrow();
    assertThat(session.hostId()).isEqualTo(player2Id);
    assertThat(session.findPlayer(player2Id).get().isHost()).isTrue();
  }

  @Test
  @DisplayName("Chủ phòng chỉnh cài đặt thành công và phát RoomSettingsChangedEvent")
  void hostUpdatesSettingsSuccess() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));

    service.handleSettings(created.getRoomId(), hostId, new SettingsCommand(5, 3, null, null));

    ArgumentCaptor<RoomSettingsChangedEvent> captor =
        ArgumentCaptor.forClass(RoomSettingsChangedEvent.class);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(eventPublisher)
                    .publish(eq(created.getRoomId()), eq(RoomTopic.LOBBY), captor.capture()));
    RoomSettingsChangedEvent event = captor.getValue();
    assertThat(event.roundCount()).isEqualTo(5);
    assertThat(event.maxPlayers()).isEqualTo(3);
  }

  @Test
  @DisplayName("Người chơi không phải chủ phòng chỉnh cài đặt bị từ chối qua hàng đợi riêng")
  void nonHostCannotUpdateSettings() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));
    UUID player2Id = UUID.randomUUID();
    service.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    service.handleSettings(created.getRoomId(), player2Id, new SettingsCommand(5, 3, null, null));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(eventPublisher)
                    .sendPrivate(eq(player2Id), eq(PrivateQueue.ERRORS), any(ErrorMessage.class)));
  }

  @Test
  @DisplayName("Danh sách phòng công khai phân trang trả về đúng các phòng công khai")
  void listPublicRoomsPaginatesCorrectly() {
    UUID h1 = UUID.randomUUID();
    UUID h2 = UUID.randomUUID();
    UUID h3 = UUID.randomUUID();

    service.createRoom(h1, "H1", null, false, new CreateRoomRequestDto(RoomTypeEnum.PUBLIC_ROOM));
    service.createRoom(h2, "H2", null, false, new CreateRoomRequestDto(RoomTypeEnum.PUBLIC_ROOM));
    service.createRoom(
        h3, "H3", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM)); // private

    PublicRoomPageDto page = service.listPublicRooms(0, 10);
    assertThat(page.getTotalItems()).isEqualTo(2);
    assertThat(page.getItems()).hasSize(2);
  }

  @Test
  @DisplayName(
      "Khi host rời lúc players rỗng nhưng waitingPlayers có người thì host chuyển cho người vào sớm nhất trong waitingPlayers (M3)")
  void hostLeavesWithEmptyPlayersTransfersToEarliestWaitingPlayer() {
    UUID hostId = UUID.randomUUID();
    RoomResponseDto created =
        service.createRoom(
            hostId, "Host", null, false, new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM));

    RoomSession session = service.getRoom(created.getRoomId()).orElseThrow();
    session.setStatus(RoomStatusDto.PLAYING);

    UUID w1 = UUID.randomUUID();
    UUID w2 = UUID.randomUUID();
    RoomPlayer waiter1 =
        new RoomPlayer(
            w1, "Waiter1", null, false, false, false, false, 0, clock.instant().plusSeconds(10));
    RoomPlayer waiter2 =
        new RoomPlayer(
            w2, "Waiter2", null, false, false, false, false, 0, clock.instant().plusSeconds(20));
    session.addPlayer(waiter1);
    session.addPlayer(waiter2);

    service.handleLeave(created.getRoomId(), hostId);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              RoomSession s = service.getRoom(created.getRoomId()).orElseThrow();
              assertThat(s.hostId()).isEqualTo(w1);
              assertThat(s.findPlayer(w1).get().isHost()).isTrue();
            });
  }

  @Test
  @DisplayName(
      "Quick match tạo phòng mới có caller là thành viên với isReady=true và isHost=false (M1)")
  void quickMatchCreatesNewRoomWithCallerAsFirstPlayer() {
    UUID userId = UUID.randomUUID();
    RoomResponseDto res = service.quickMatch(userId, "QMPlayer", null, false, null);

    assertThat(res.getRoomType()).isEqualTo(RoomTypeDto.QUICK_MATCH);
    assertThat(res.getHostId()).isNull();
    assertThat(res.getPlayerCount()).isEqualTo(1);
    assertThat(res.getPlayers()).hasSize(1);
    assertThat(res.getPlayers().get(0).getUserId()).isEqualTo(userId);
    assertThat(res.getPlayers().get(0).getIsHost()).isFalse();
    assertThat(res.getPlayers().get(0).getIsReady()).isTrue();

    RoomSession s = service.getRoom(res.getRoomId()).orElseThrow();
    assertThat(s.hasMember(userId)).isTrue();
  }

  @Test
  @DisplayName("2 người cùng gọi quick match với cùng filter thì ghép vào chung một phòng (M1)")
  void twoCallersWithSameFilterJoinSameQuickMatchRoom() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();

    QuickMatchRequestDto req = new QuickMatchRequestDto();
    SoloFilterDto filter = new SoloFilterDto();
    filter.setGenreCodes(List.of("POP"));
    req.setFilter(filter);

    RoomResponseDto r1 = service.quickMatch(u1, "P1", null, false, req);
    RoomResponseDto r2 = service.quickMatch(u2, "P2", null, false, req);

    assertThat(r2.getRoomId()).isEqualTo(r1.getRoomId());
    assertThat(r2.getPlayerCount()).isEqualTo(2);

    RoomSession s = service.getRoom(r1.getRoomId()).orElseThrow();
    assertThat(s.playerCount()).isEqualTo(2);
    assertThat(s.hasMember(u1)).isTrue();
    assertThat(s.hasMember(u2)).isTrue();
  }

  @Test
  @DisplayName("Quick match gửi cả playlistId và filter bị từ chối 400 VALIDATION_FAILED (M1)")
  void quickMatchWithBothPlaylistAndFilterThrows400() {
    UUID userId = UUID.randomUUID();
    QuickMatchRequestDto req = new QuickMatchRequestDto();
    req.setPlaylistId(UUID.randomUUID());
    SoloFilterDto filter = new SoloFilterDto();
    filter.setGenreCodes(List.of("POP"));
    req.setFilter(filter);

    assertThatThrownBy(() -> service.quickMatch(userId, "P1", null, false, req))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
              assertThat(apiEx.code()).isEqualTo("VALIDATION_FAILED");
            });
  }

  @Test
  @DisplayName(
      "Nhiều thread cùng gọi quick match với cùng filter đồng thời đều ghép chung một phòng (M1)")
  void concurrentQuickMatchWithSameFilterJoinsSameRoom() throws Exception {
    int threadCount = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<CompletableFuture<RoomResponseDto>> futures = new ArrayList<>();

    QuickMatchRequestDto req = new QuickMatchRequestDto();
    SoloFilterDto filter = new SoloFilterDto();
    filter.setGenreCodes(List.of("ROCK"));
    req.setFilter(filter);

    for (int i = 0; i < threadCount; i++) {
      UUID u = UUID.randomUUID();
      String name = "P" + i;
      futures.add(
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return service.quickMatch(u, name, null, false, req);
              },
              executor));
    }

    startLatch.countDown();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    executor.shutdown();

    UUID expectedRoomId = futures.get(0).join().getRoomId();
    for (CompletableFuture<RoomResponseDto> f : futures) {
      assertThat(f.join().getRoomId()).isEqualTo(expectedRoomId);
    }

    RoomSession s = service.getRoom(expectedRoomId).orElseThrow();
    assertThat(s.playerCount()).isEqualTo(threadCount);
  }

  @Test
  @DisplayName(
      "Quick match cập nhật descriptor trong RoomRegistry hoàn tất trước khi trả response (M3)")
  void quickMatchUpdatesDescriptorInRegistryBeforeReturning() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();

    QuickMatchRequestDto req = new QuickMatchRequestDto();
    SoloFilterDto filter = new SoloFilterDto();
    filter.setGenreCodes(List.of("JAZZ"));
    req.setFilter(filter);

    RoomResponseDto r1 = service.quickMatch(u1, "P1", null, false, req);
    assertThat(roomRegistry.find(r1.getRoomId())).isPresent();
    assertThat(roomRegistry.find(r1.getRoomId()).get().hasMember(u1)).isTrue();

    RoomResponseDto r2 = service.quickMatch(u2, "P2", null, false, req);
    assertThat(r2.getRoomId()).isEqualTo(r1.getRoomId());
    assertThat(roomRegistry.find(r1.getRoomId()).get().hasMember(u2)).isTrue();
  }

  @Test
  @DisplayName(
      "Kick người chơi chỉ gửi UNSUBSCRIBE cho room bị kick, không gửi DISCONNECT session (M2)")
  void kickPlayerOnlyUnsubscribesRoomTopicAndDoesNotDisconnectSession() {
    SimpUserRegistry mockRegistry = mock(SimpUserRegistry.class);
    MessageChannel mockChannel = mock(MessageChannel.class);
    RoomLobbyService testService =
        new RoomLobbyService(
            roomRegistry,
            eventPublisher,
            catalogService,
            codeGenerator,
            properties,
            clock,
            mockRegistry,
            mockChannel);

    UUID hostId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    CreateRoomRequestDto req = new CreateRoomRequestDto(RoomTypeEnum.PUBLIC_ROOM);
    req.setRoundCount(RoundCountEnum.NUMBER_10);
    req.setMaxPlayers(5);
    RoomResponseDto created = testService.createRoom(hostId, "Host", null, false, req);
    UUID roomId = created.getRoomId();

    testService.joinRoomByCode(targetUserId, "Target", null, false, created.getCode());

    SimpUser mockUser = mock(SimpUser.class);
    SimpSession mockSession = mock(SimpSession.class);
    SimpSubscription roomSub = mock(SimpSubscription.class);
    SimpSubscription otherRoomSub = mock(SimpSubscription.class);

    when(mockRegistry.getUser(targetUserId.toString())).thenReturn(mockUser);
    when(mockUser.getSessions()).thenReturn(Set.of(mockSession));
    when(mockSession.getId()).thenReturn("sess-42");
    when(roomSub.getId()).thenReturn("sub-target-room");
    when(roomSub.getDestination()).thenReturn("/topic/rooms/" + roomId + "/lobby");
    when(otherRoomSub.getId()).thenReturn("sub-other-room");
    when(otherRoomSub.getDestination()).thenReturn("/topic/rooms/" + UUID.randomUUID() + "/lobby");
    when(mockSession.getSubscriptions()).thenReturn(Set.of(roomSub, otherRoomSub));

    testService.handleKick(roomId, hostId, new KickCommand(targetUserId));

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ArgumentCaptor<org.springframework.messaging.Message<?>> captor =
                  ArgumentCaptor.forClass(org.springframework.messaging.Message.class);
              verify(mockChannel, times(1)).send(captor.capture());

              StompHeaderAccessor accessor = StompHeaderAccessor.wrap(captor.getValue());
              assertThat(accessor.getCommand()).isEqualTo(StompCommand.UNSUBSCRIBE);
              assertThat(accessor.getSessionId()).isEqualTo("sess-42");
              assertThat(accessor.getSubscriptionId()).isEqualTo("sub-target-room");
              assertThat(accessor.getDestination()).isEqualTo("/topic/rooms/" + roomId + "/lobby");
            });
  }
}
