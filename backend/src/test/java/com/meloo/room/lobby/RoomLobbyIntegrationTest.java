package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.CreateRoomRequestDto;
import com.meloo.generated.model.CreateRoomRequestDto.RoomTypeEnum;
import com.meloo.generated.model.CreateRoomRequestDto.RoundCountEnum;
import com.meloo.generated.model.JoinRoomRequestDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.message.KickCommand;
import com.meloo.room.api.message.LeaveCommand;
import com.meloo.room.api.message.ReadyCommand;
import com.meloo.room.api.message.SettingsCommand;
import com.meloo.room.realtime.RoomRegistry;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "meloo.room.realtime.heartbeat-interval=1s",
      "meloo.cors.allowed-origins=http://localhost:5173"
    })
@org.springframework.boot.test.context.SpringBootTest(
    webEnvironment =
        org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomLobbyIntegrationTest extends IntegrationTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";
  private static final String JWT_SECRET =
      "meloo-test-super-secret-key-for-jwt-signing-minimum-256-bits-length-meloo";

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private RoomLobbyService roomLobbyService;
  @Autowired private RoomRegistry roomRegistry;
  @Autowired private SimpUserRegistry simpUserRegistry;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private CatalogService catalogService;

  private WebSocketStompClient stompClient;
  private ThreadPoolTaskScheduler clientHeartbeatScheduler;

  @BeforeEach
  void setUp() {
    when(catalogService.selectRandomTracks(any(), anyInt(), any()))
        .thenAnswer(
            invocation -> {
              int count = invocation.getArgument(1);
              java.util.List<TrackInfo> list = new java.util.ArrayList<>();
              for (int i = 0; i < count; i++) {
                list.add(
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
              return list;
            });

    clientHeartbeatScheduler = new ThreadPoolTaskScheduler();
    clientHeartbeatScheduler.setPoolSize(2);
    clientHeartbeatScheduler.initialize();

    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(
        new CompositeMessageConverter(
            List.of(
                new ByteArrayMessageConverter(),
                new StringMessageConverter(),
                new JacksonJsonMessageConverter())));
    stompClient.setTaskScheduler(clientHeartbeatScheduler);
    stompClient.setDefaultHeartbeat(new long[] {500, 500});
    stompClient.start();
  }

  @AfterEach
  void tearDown() {
    if (stompClient != null) {
      stompClient.stop();
    }
    if (clientHeartbeatScheduler != null) {
      clientHeartbeatScheduler.shutdown();
    }
  }

  @Test
  @DisplayName("Vào phòng đầy từ chối với lỗi rõ ràng 409 ROOM_FULL (AC-2)")
  void joinFullRoomRejectsWith409() throws Exception {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(2);

    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);

    UUID player2Id = UUID.randomUUID();
    roomLobbyService.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    UUID player3Id = UUID.randomUUID();
    JoinRoomRequestDto joinReq = new JoinRoomRequestDto(created.getCode());

    mockMvc
        .perform(
            post("/api/v1/rooms/join")
                .with(jwt().jwt(j -> j.subject(player3Id.toString()).claim("nickname", "Player3")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinReq)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_FULL"));
  }

  @Test
  @DisplayName(
      "2 người cùng vào khi chỉ còn 1 chỗ: đúng 1 người vào được, 1 người nhận 409 (AC-2, Test bắt buộc)")
  void twoAuthenticatedClientsEnterRoomConcurrentSpot() throws Exception {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(2);

    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);

    UUID clientA = UUID.randomUUID();
    UUID clientB = UUID.randomUUID();

    CountDownLatch startGate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> callA =
          executor.submit(
              () -> {
                startGate.await();
                JoinRoomRequestDto joinReq = new JoinRoomRequestDto(created.getCode());
                MvcResult result =
                    mockMvc
                        .perform(
                            post("/api/v1/rooms/join")
                                .with(
                                    jwt()
                                        .jwt(
                                            j ->
                                                j.subject(clientA.toString())
                                                    .claim("nickname", "ClientA")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinReq)))
                        .andReturn();
                return result.getResponse().getStatus();
              });

      Future<Integer> callB =
          executor.submit(
              () -> {
                startGate.await();
                JoinRoomRequestDto joinReq = new JoinRoomRequestDto(created.getCode());
                MvcResult result =
                    mockMvc
                        .perform(
                            post("/api/v1/rooms/join")
                                .with(
                                    jwt()
                                        .jwt(
                                            j ->
                                                j.subject(clientB.toString())
                                                    .claim("nickname", "ClientB")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(joinReq)))
                        .andReturn();
                return result.getResponse().getStatus();
              });

      startGate.countDown();
      int statusA = callA.get(10, TimeUnit.SECONDS);
      int statusB = callB.get(10, TimeUnit.SECONDS);

      // Exactly one must succeed (200 OK) and one must be rejected (409 Conflict)
      assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(200, 409);
    }

    RoomSession session = roomLobbyService.getRoom(created.getRoomId()).orElseThrow();
    assertThat(session.playerCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Chủ phòng rời qua STOMP thì quyền chuyển cho người vào sớm nhất (BR-12, AC-4)")
  void hostLeavesTransfersHostToEarliestPlayer() throws Exception {
    UUID hostId = UUID.randomUUID();
    UUID player2Id = UUID.randomUUID();
    UUID player3Id = UUID.randomUUID();

    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(4);
    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);
    roomLobbyService.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());
    roomLobbyService.joinRoomByCode(player3Id, "Player3", null, false, created.getCode());

    StompSession hostSession = connect(hostId);
    StompSession player2Session = connect(player2Id);

    StompEventCollector lobbyEvents = subscribeLobby(player2Session, created.getRoomId());

    // Host sends leave command
    hostSession.send("/app/rooms/" + created.getRoomId() + "/leave", new LeaveCommand());

    // Verify STOMP broadcast includes PLAYER_LEFT with newHostId == player2Id
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String eventJson = lobbyEvents.poll();
              assertThat(eventJson).isNotNull().contains("PLAYER_LEFT");
              assertThat(eventJson).contains(player2Id.toString());
              assertThat(eventJson).contains("VOLUNTARY");
            });

    RoomSession session = roomLobbyService.getRoom(created.getRoomId()).orElseThrow();
    assertThat(session.hostId()).isEqualTo(player2Id);
    assertThat(session.findPlayer(player2Id).orElseThrow().isHost()).isTrue();
  }

  @Test
  @DisplayName("Vào khi trận đang chạy thì được xếp chờ ở sảnh (BR-15, AC-2)")
  void joinWhileMatchIsRunningQueuedAtLobbyWaitingList() throws Exception {
    UUID hostId = UUID.randomUUID();
    UUID player2Id = UUID.randomUUID();

    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(4);
    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);
    roomLobbyService.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    StompSession player2Session = connect(player2Id);
    StompEventCollector lobbyEvents = subscribeLobby(player2Session, created.getRoomId());

    // Transition room to PLAYING
    RoomSession session = roomLobbyService.getRoom(created.getRoomId()).orElseThrow();
    session.setStatus(RoomStatusDto.PLAYING);

    UUID player3Id = UUID.randomUUID();
    JoinRoomRequestDto joinReq = new JoinRoomRequestDto(created.getCode());

    mockMvc
        .perform(
            post("/api/v1/rooms/join")
                .with(jwt().jwt(j -> j.subject(player3Id.toString()).claim("nickname", "Player3")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.playerCount").value(3));

    // Verify WAITING_LIST_CHANGED is received via STOMP
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String eventJson = lobbyEvents.poll();
              assertThat(eventJson).isNotNull().contains("WAITING_LIST_CHANGED");
              assertThat(eventJson).contains(player3Id.toString());
            });

    assertThat(session.waitingPlayers()).extracting("userId").contains(player3Id);
  }

  @Test
  @DisplayName("Người bị mời ra vào lại thì bị từ chối 403 ROOM_KICKED (BR-12, AC-3)")
  void kickedPlayerCannotRejoinRoom() throws Exception {
    UUID hostId = UUID.randomUUID();
    UUID player2Id = UUID.randomUUID();

    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(4);
    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);
    roomLobbyService.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    StompSession hostSession = connect(hostId);
    StompSession player2Session = connect(player2Id);

    StompEventCollector lobbyEvents = subscribeLobby(player2Session, created.getRoomId());

    // Host sends KickCommand via STOMP
    hostSession.send("/app/rooms/" + created.getRoomId() + "/kick", new KickCommand(player2Id));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String eventJson = lobbyEvents.poll();
              assertThat(eventJson).isNotNull().contains("PLAYER_KICKED");
              assertThat(eventJson).contains(player2Id.toString());
            });

    // Player 2 tries to rejoin by code
    JoinRoomRequestDto joinReq = new JoinRoomRequestDto(created.getCode());
    mockMvc
        .perform(
            post("/api/v1/rooms/join")
                .with(jwt().jwt(j -> j.subject(player2Id.toString()).claim("nickname", "Player2")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinReq)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ROOM_KICKED"));
  }

  @Test
  @DisplayName(
      "Lệnh sẵn sàng hoặc mời ra gửi vào phòng QUICK_MATCH bị từ chối (BR-12, BR-13, AC-5)")
  void quickMatchRejectsReadySettingsAndKickCommands() throws Exception {
    RoomResponseDto qm = roomLobbyService.createQuickMatchRoom(null, null, 10, 5);
    UUID userId = UUID.randomUUID();

    RoomSession qmSession = roomLobbyService.getRoom(qm.getRoomId()).orElseThrow();
    qmSession.addPlayer(
        new RoomPlayer(userId, "QMUser", null, false, false, false, false, 0, Instant.now()));
    roomRegistry.update(new RoomDescriptor(qm.getRoomId(), Set.of(userId), false)).join();

    StompSession userSession = connect(userId);
    StompEventCollector errorEvents = subscribeErrors(userSession, userId);

    // Send ready command
    userSession.send("/app/rooms/" + qm.getRoomId() + "/ready", new ReadyCommand(true));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String err = errorEvents.poll();
              assertThat(err).isNotNull().contains("ERROR").contains("INVALID_COMMAND");
            });

    // Send kick command
    userSession.send("/app/rooms/" + qm.getRoomId() + "/kick", new KickCommand(UUID.randomUUID()));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String err = errorEvents.poll();
              assertThat(err).isNotNull().contains("ERROR").contains("INVALID_COMMAND");
            });

    // Send settings command
    userSession.send(
        "/app/rooms/" + qm.getRoomId() + "/settings", new SettingsCommand(5, 4, null, null));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String err = errorEvents.poll();
              assertThat(err).isNotNull().contains("ERROR").contains("INVALID_COMMAND");
            });
  }

  @Test
  @DisplayName("Sẵn sàng và chỉnh cài đặt phòng tự tạo hoạt động chuẩn STOMP (AC-3)")
  void readyAndSettingsCommandsWorkInPrivateRoom() throws Exception {
    UUID hostId = UUID.randomUUID();
    UUID player2Id = UUID.randomUUID();

    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PRIVATE_ROOM);
    createReq.setMaxPlayers(4);
    RoomResponseDto created =
        roomLobbyService.createRoom(hostId, "HostUser", null, false, createReq);
    roomLobbyService.joinRoomByCode(player2Id, "Player2", null, false, created.getCode());

    StompSession hostSession = connect(hostId);
    StompSession player2Session = connect(player2Id);

    StompEventCollector lobbyEvents = subscribeLobby(hostSession, created.getRoomId());

    // Player 2 sends ready
    player2Session.send("/app/rooms/" + created.getRoomId() + "/ready", new ReadyCommand(true));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String eventJson = lobbyEvents.poll();
              assertThat(eventJson).isNotNull().contains("PLAYER_READY_CHANGED");
              assertThat(eventJson).contains(player2Id.toString());
              assertThat(eventJson).contains("\"isReady\":true");
            });

    // Host updates settings
    hostSession.send(
        "/app/rooms/" + created.getRoomId() + "/settings", new SettingsCommand(5, 3, null, null));

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              String eventJson = lobbyEvents.poll();
              assertThat(eventJson).isNotNull().contains("ROOM_SETTINGS_CHANGED");
              assertThat(eventJson).contains("\"roundCount\":5");
              assertThat(eventJson).contains("\"maxPlayers\":3");
            });
  }

  @Test
  @DisplayName("REST API: Tạo phòng, vào bằng mã, liệt kê phòng công khai (OpenAPI)")
  void restApiRoomLifecycle() throws Exception {
    UUID hostId = UUID.randomUUID();
    CreateRoomRequestDto createReq = new CreateRoomRequestDto(RoomTypeEnum.PUBLIC_ROOM);
    createReq.setRoundCount(RoundCountEnum.NUMBER_10);
    createReq.setMaxPlayers(4);

    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/rooms")
                    .with(
                        jwt().jwt(j -> j.subject(hostId.toString()).claim("nickname", "HostUser")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").isNotEmpty())
            .andExpect(jsonPath("$.roomType").value("PUBLIC_ROOM"))
            .andExpect(jsonPath("$.playerCount").value(1))
            .andReturn();

    String roomCode =
        tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(createRes.getResponse().getContentAsString())
            .get("code")
            .asText();

    UUID player2Id = UUID.randomUUID();
    JoinRoomRequestDto joinReq = new JoinRoomRequestDto(roomCode);

    mockMvc
        .perform(
            post("/api/v1/rooms/join")
                .with(jwt().jwt(j -> j.subject(player2Id.toString()).claim("nickname", "Player2")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.playerCount").value(2));

    mockMvc
        .perform(
            get("/api/v1/rooms/public")
                .with(jwt().jwt(j -> j.subject(hostId.toString())))
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").isNumber())
        .andExpect(jsonPath("$.items").isArray());
  }

  private StompEventCollector subscribeLobby(StompSession session, UUID roomId) {
    String destination = "/topic/rooms/" + roomId + "/lobby";
    StompEventCollector collector = new StompEventCollector();
    session.subscribe(destination, collector);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        simpUserRegistry.findSubscriptions(
                            s -> destination.equals(s.getDestination())))
                    .isNotEmpty());
    return collector;
  }

  private StompEventCollector subscribeErrors(StompSession session, UUID userId) {
    StompEventCollector collector = new StompEventCollector();
    session.subscribe("/user/queue/errors", collector);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        simpUserRegistry.findSubscriptions(
                            s -> s.getDestination().contains("errors")))
                    .isNotEmpty());
    return collector;
  }

  private StompSession connect(UUID userId) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + signedJwt(userId));
    WebSocketHttpHeaders webSocketHeaders = new WebSocketHttpHeaders();
    webSocketHeaders.setOrigin(ALLOWED_ORIGIN);
    String url = "ws://localhost:" + port + "/ws";
    return stompClient
        .connectAsync(
            URI.create(url), webSocketHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
        .get(10, TimeUnit.SECONDS);
  }

  private String signedJwt(UUID userId) throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("nickname", "User-" + userId.toString().substring(0, 4))
            .claim("kind", "USER")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  static class StompEventCollector implements StompFrameHandler {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      if (payload instanceof byte[] bytes) {
        queue.offer(new String(bytes, StandardCharsets.UTF_8));
      } else if (payload != null) {
        queue.offer(payload.toString());
      }
    }

    public String poll() {
      try {
        return queue.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }
}
