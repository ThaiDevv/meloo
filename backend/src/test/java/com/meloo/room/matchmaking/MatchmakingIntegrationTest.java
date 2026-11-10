package com.meloo.room.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.audio.api.*;
import com.meloo.catalog.api.*;
import com.meloo.game.events.*;
import com.meloo.generated.model.*;
import com.meloo.room.lobby.RoomLobbyService;
import com.meloo.room.realtime.RoomRegistry;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MatchmakingIntegrationTest.EventsConfiguration.class)
@TestPropertySource(
    properties = {
      "meloo.room.lobby.default-round-count=5",
      "meloo.room.matchmaking.countdown=3s",
      "meloo.room.matchmaking.solo-suggestion-delay=3s",
      "meloo.room.match.prepare-timeout=5s",
      "meloo.room.match.reveal-duration=30ms",
      "meloo.room.match.podium-duration=100ms",
      "meloo.cors.allowed-origins=http://localhost:5173"
    })
class MatchmakingIntegrationTest extends IntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired RoomLobbyService lobby;
  @Autowired RoomRegistry registry;
  @Autowired SimpUserRegistry users;
  @Autowired Events events;
  @LocalServerPort int port;
  @MockitoBean CatalogService catalog;
  @MockitoBean AudioService audio;
  final Set<UUID> rooms = ConcurrentHashMap.newKeySet();
  final List<StompSession> sessions = new ArrayList<>();
  WebSocketStompClient client;
  List<TrackInfo> tracks;

  @BeforeEach
  void setUp() {
    tracks =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                n ->
                    new TrackInfo(
                        UUID.randomUUID(),
                        "SecretTitle" + n,
                        "SecretArtist" + n,
                        "FAKE",
                        "fake-" + n,
                        null,
                        null,
                        null,
                        null))
            .toList();
    when(catalog.selectRandomTracks(any(), anyInt(), any())).thenReturn(tracks);
    when(catalog.getTrackById(any()))
        .thenAnswer(i -> tracks.stream().filter(t -> t.id().equals(i.getArgument(0))).findFirst());
    when(audio.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());
    when(audio.isRoundPlayable(any())).thenReturn(true);
    when(audio.createAudioClip(any(), any(), anyInt()))
        .thenAnswer(
            i ->
                new AudioClipDto()
                    .url("/api/v1/audio/opaque-token")
                    .expiresAt(OffsetDateTime.now().plusMinutes(2)));
    client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new JacksonJsonMessageConverter());
    client.start();
  }

  @AfterEach
  void close() {
    sessions.forEach(
        s -> {
          if (s.isConnected()) s.disconnect();
        });
    client.stop();
    rooms.forEach(registry::remove);
  }

  @Test
  void anonymousGets401AndGuestGetsContract403() throws Exception {
    mvc.perform(post("/api/v1/rooms/quick-match")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/rooms/quick-match")
                .with(
                    jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("kind", "GUEST"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("QUICK_MATCH_REQUIRES_ACCOUNT"));
  }

  @Test
  void publicListOnlyContainsSelfCreatedNonfullWaitingRooms() throws Exception {
    UUID waiting = create(CreateRoomRequestDto.RoomTypeEnum.PUBLIC_ROOM, 5);
    UUID full = create(CreateRoomRequestDto.RoomTypeEnum.PUBLIC_ROOM, 2);
    lobby.joinRoomByCode(
        UUID.randomUUID(), "Other", null, false, lobby.getRoom(full).orElseThrow().code());
    UUID playing = create(CreateRoomRequestDto.RoomTypeEnum.PUBLIC_ROOM, 5);
    registry
        .actor(playing)
        .orElseThrow()
        .execute(() -> lobby.getRoom(playing).orElseThrow().setStatus(RoomStatusDto.PLAYING))
        .get();
    create(CreateRoomRequestDto.RoomTypeEnum.PRIVATE_ROOM, 5);
    quick(UUID.randomUUID());
    mvc.perform(get("/api/v1/rooms/public").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].roomId").value(waiting.toString()));
  }

  @Test
  void tenConcurrentHttpRequestsUseExactlyTwoRoomsOfFive() throws Exception {
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<RoomResponseDto>> calls = new ArrayList<>();
      for (int i = 0; i < 10; i++)
        calls.add(
            executor.submit(
                () -> {
                  gate.await();
                  return quick(UUID.randomUUID());
                }));
      gate.countDown();
      Set<UUID> ids = new HashSet<>();
      for (var call : calls) ids.add(call.get(15, TimeUnit.SECONDS).getRoomId());
      assertThat(ids).hasSize(2);
      for (UUID id : ids) {
        var snapshot =
            registry
                .actor(id)
                .orElseThrow()
                .submit(() -> lobby.getRoom(id).orElseThrow().toResponseDto())
                .get();
        assertThat(snapshot.getPlayerCount()).isEqualTo(5);
        assertThat(snapshot.getHostId()).isNull();
        assertThat(snapshot.getCode()).isNull();
        assertThat(snapshot.getRoomType()).isEqualTo(RoomTypeDto.QUICK_MATCH);
        assertThat(registry.find(id).orElseThrow().memberIds()).hasSize(5);
      }
    }
  }

  @Test
  void stompSuggestionKeepsSoloPlayerInQueue() throws Exception {
    UUID user = UUID.randomUUID();
    UUID room = quick(user).getRoomId();
    Peer peer = connect(user, room);
    peer.waitFor("MATCH_SOLO_SUGGESTION");
    assertThat(
            peer.messages.stream()
                .filter(n -> "MATCH_SOLO_SUGGESTION".equals(n.path("type").asText()))
                .findFirst()
                .orElseThrow()
                .hasNonNull("serverTime"))
        .isTrue();
    assertThat(registry.find(room).orElseThrow().hasMember(user)).isTrue();
    assertThat(
            registry
                .actor(room)
                .orElseThrow()
                .submit(() -> lobby.getRoom(room).orElseThrow().status())
                .get())
        .isEqualTo(RoomStatusDto.LOBBY);
  }

  @Test
  void actualRankedMatchStartsAfterCountdownAndCountsDownAgainAfterPodium() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID room = quick(first).getRoomId();
    Peer a = connect(first, room);
    assertThat(quick(second).getRoomId()).isEqualTo(room);
    Peer b = connect(second, room);
    a.waitFor("MATCH_COUNTDOWN");
    a.round("ROUND_PREPARE", 1);
    assertThat(
            a.messages.stream()
                .filter(n -> "MATCH_COUNTDOWN".equals(n.path("type").asText()))
                .map(n -> n.path("remainingSeconds").asInt())
                .toList())
        .contains(3)
        .anyMatch(seconds -> seconds > 0 && seconds < 3);
    assertThat(a.messages.toString())
        .doesNotContain("SecretTitle", "SecretArtist", tracks.getFirst().id().toString());
    assertThat(events.started.stream().filter(e -> e.playerIds().contains(first)).toList())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.ranked()).isTrue();
              assertThat(e.type().name()).isEqualTo("QUICK_MATCH");
            });
    for (int n = 1; n <= 5; n++) {
      a.round("ROUND_PREPARE", n);
      send(a, room, "prepared", Map.of("roundNo", n));
      send(b, room, "prepared", Map.of("roundNo", n));
      a.round("ROUND_START", n);
      send(a, room, "guess", Map.of("roundNo", n, "trackId", tracks.get(n - 1).id()));
      send(b, room, "guess", Map.of("roundNo", n, "trackId", tracks.get(n - 1).id()));
      a.round("ROUND_END", n);
    }
    a.waitFor("MATCH_FINISHED");
    a.waitFor("RETURN_TO_LOBBY");
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () -> a.types().lastIndexOf("MATCH_COUNTDOWN") > a.types().indexOf("RETURN_TO_LOBBY"));
    assertThat(events.finished.stream().filter(e -> room.equals(e.roomId())).toList())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.ranked()).isTrue();
              assertThat(e.type().name()).isEqualTo("QUICK_MATCH");
            });
  }

  RoomResponseDto quick(UUID user) throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/rooms/quick-match")
                    .with(
                        jwt()
                            .jwt(
                                j ->
                                    j.subject(user.toString())
                                        .claim("kind", "REGISTERED")
                                        .claim("nickname", "Player"))))
            .andExpect(status().isOk())
            .andReturn();
    RoomResponseDto room =
        mapper.readValue(result.getResponse().getContentAsString(), RoomResponseDto.class);
    rooms.add(room.getRoomId());
    return room;
  }

  UUID create(CreateRoomRequestDto.RoomTypeEnum type, int max) {
    var room =
        lobby.createRoom(
            UUID.randomUUID(),
            "Host",
            null,
            false,
            new CreateRoomRequestDto(type)
                .maxPlayers(max)
                .roundCount(CreateRoomRequestDto.RoundCountEnum.NUMBER_5));
    rooms.add(room.getRoomId());
    return room.getRoomId();
  }

  Peer connect(UUID id, UUID room) throws Exception {
    var token =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .subject(id.toString())
                .claim("kind", "REGISTERED")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(120)))
                .build());
    token.sign(
        new MACSigner(
            "meloo-test-super-secret-key-for-jwt-signing-minimum-256-bits-length-meloo"
                .getBytes(StandardCharsets.UTF_8)));
    var headers = new StompHeaders();
    headers.add("Authorization", "Bearer " + token.serialize());
    var websocket = new WebSocketHttpHeaders();
    websocket.setOrigin("http://localhost:5173");
    var session =
        client
            .connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                websocket,
                headers,
                new StompSessionHandlerAdapter() {})
            .get(10, TimeUnit.SECONDS);
    sessions.add(session);
    var peer = new Peer(session);
    for (String destination :
        List.of(
            "/topic/rooms/" + room + "/lobby",
            "/topic/rooms/" + room + "/round",
            "/topic/rooms/" + room + "/scoreboard")) {
      session.subscribe(destination, peer);
      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  users
                          .findSubscriptions(
                              s ->
                                  destination.equals(s.getDestination())
                                      && id.toString().equals(s.getSession().getUser().getName()))
                          .size()
                      == 1);
    }
    return peer;
  }

  void send(Peer peer, UUID room, String path, Object payload) {
    var headers = new StompHeaders();
    headers.setDestination("/app/rooms/" + room + "/" + path);
    headers.setContentType(MediaType.APPLICATION_JSON);
    peer.session.send(headers, payload);
  }

  class Peer implements StompFrameHandler {
    final StompSession session;
    final List<JsonNode> messages = new CopyOnWriteArrayList<>();

    Peer(StompSession session) {
      this.session = session;
    }

    public Type getPayloadType(StompHeaders headers) {
      return JsonNode.class;
    }

    public void handleFrame(StompHeaders headers, Object payload) {
      messages.add((JsonNode) payload);
    }

    List<String> types() {
      return messages.stream().map(n -> n.path("type").asText()).toList();
    }

    void waitFor(String type) {
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(types()).contains(type));
    }

    void round(String type, int round) {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  messages.stream()
                      .anyMatch(
                          n ->
                              type.equals(n.path("type").asText())
                                  && n.path("roundNo").asInt() == round));
    }
  }

  @TestConfiguration
  static class EventsConfiguration {
    @Bean
    Events events() {
      return new Events();
    }
  }

  static class Events {
    final List<MatchStarted> started = new CopyOnWriteArrayList<>();
    final List<MatchFinished> finished = new CopyOnWriteArrayList<>();

    @EventListener
    public void started(MatchStarted event) {
      started.add(event);
    }

    @EventListener
    public void finished(MatchFinished event) {
      finished.add(event);
    }
  }
}
