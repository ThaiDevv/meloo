package com.meloo.room.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.meloo.IntegrationTest;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.events.MatchFinished;
import com.meloo.generated.model.*;
import com.meloo.room.api.message.ReadyCommand;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomMatchIntegrationTest.ListenerConfiguration.class)
@TestPropertySource(
    properties = {
      "meloo.room.match.prepare-timeout=10s",
      "meloo.room.match.round-duration=45s",
      "meloo.room.match.reveal-duration=150ms",
      "meloo.room.match.podium-duration=300ms",
      "meloo.room.lobby.disconnect-timeout=5s",
      "meloo.cors.allowed-origins=http://localhost:5173"
    })
class RoomMatchIntegrationTest extends IntegrationTest {
  @Autowired RoomLobbyService lobby;
  @Autowired RoomRegistry registry;
  @Autowired SimpUserRegistry users;
  @Autowired ObjectMapper mapper;
  @Autowired FinishedListener listener;
  @LocalServerPort int port;
  @MockitoBean CatalogService catalog;
  @MockitoBean AudioService audio;
  WebSocketStompClient client;
  UUID roomId;
  UUID host;
  UUID guest;
  String code;
  List<TrackInfo> tracks;
  List<StompSession> sessions = new ArrayList<>();

  @BeforeEach
  void setUp() {
    tracks =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                n ->
                    new TrackInfo(
                        UUID.randomUUID(),
                        "Hidden title " + n,
                        "Hidden artist " + n,
                        "FAKE",
                        "fake-" + n,
                        null,
                        null,
                        null,
                        null))
            .toList();
    when(catalog.selectRandomTracks(any(), anyInt(), anySet())).thenReturn(tracks);
    when(catalog.getTrackById(any()))
        .thenAnswer(i -> tracks.stream().filter(t -> t.id().equals(i.getArgument(0))).findFirst());
    when(audio.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());
    when(audio.isRoundPlayable(any())).thenReturn(true);
    when(audio.createAudioClip(any(), any(), anyInt()))
        .thenAnswer(
            i -> {
              AudioClipDto clip = new AudioClipDto();
              clip.setUrl("/api/v1/audio/opaque-" + UUID.randomUUID());
              clip.setExpiresAt(Instant.now().plusSeconds(120).atOffset(ZoneOffset.UTC));
              return clip;
            });
    host = UUID.randomUUID();
    guest = UUID.randomUUID();
    var request = new CreateRoomRequestDto(CreateRoomRequestDto.RoomTypeEnum.PRIVATE_ROOM);
    request.setRoundCount(CreateRoomRequestDto.RoundCountEnum.NUMBER_5);
    RoomResponseDto created = lobby.createRoom(host, "Host", null, false, request);
    roomId = created.getRoomId();
    code = created.getCode();
    lobby.joinRoomByCode(guest, "Guest", null, false, code);
    lobby.handleReady(roomId, host, new ReadyCommand(true));
    lobby.handleReady(roomId, guest, new ReadyCommand(true));
    client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(
        new CompositeMessageConverter(
            List.of(
                new ByteArrayMessageConverter(),
                new StringMessageConverter(),
                new JacksonJsonMessageConverter())));
    client.start();
  }

  @AfterEach
  void close() {
    sessions.forEach(
        s -> {
          if (s.isConnected()) s.disconnect();
        });
    client.stop();
    registry.remove(roomId);
  }

  @Test
  void realStompMatchPublishesTransactionalFullResultsAndPromotesWaiters() throws Exception {
    Peer first = connect(host);
    Peer second = connect(guest);
    send(first, "start", Map.of());
    round(first, "ROUND_PREPARE", 1);
    assertThat(first.messages.stream().map(n -> n.path("type").asText()).toList())
        .containsSubsequence("MATCH_STARTING", "ROUND_PREPARE");
    assertThat(first.messages.toString())
        .doesNotContain("Hidden title", "Hidden artist", tracks.getFirst().id().toString());
    UUID waiting = UUID.randomUUID();
    lobby.joinRoomByCode(waiting, "Waiting", null, false, code);
    for (int n = 1; n <= 5; n++) {
      round(first, "ROUND_PREPARE", n);
      send(first, "prepared", Map.of("roundNo", n));
      send(second, "prepared", Map.of("roundNo", n));
      round(first, "ROUND_START", n);
      send(first, "guess", Map.of("roundNo", n, "trackId", tracks.get(n - 1).id()));
      send(second, "guess", Map.of("roundNo", n, "trackId", tracks.get(n - 1).id()));
      round(first, "ROUND_END", n);
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> listener.events().stream().anyMatch(e -> roomId.equals(e.roomId())));
    MatchFinished event =
        listener.events().stream().filter(e -> roomId.equals(e.roomId())).findFirst().orElseThrow();
    assertThat(event.status().name()).isEqualTo("FINISHED");
    assertThat(event.roomId()).isEqualTo(roomId);
    assertThat(event.mode()).isEqualTo("PRACTICE");
    assertThat(event.rounds()).hasSize(5);
    assertThat(event.players())
        .hasSize(2)
        .allMatch(p -> !p.left() && p.results().size() == 5 && p.totalScore() > 0);
    assertThat(listener.publications()).contains(event.matchId());
    assertThat(listener.deliveries()).contains(event.matchId());
    await().atMost(Duration.ofSeconds(5)).until(() -> first.has("RETURN_TO_LOBBY"));
    var state =
        registry
            .actor(roomId)
            .orElseThrow()
            .submit(() -> lobby.getRoom(roomId).orElseThrow().toResponseDto())
            .get();
    assertThat(state.getStatus()).isEqualTo(RoomStatusDto.LOBBY);
    assertThat(state.getCode()).isEqualTo(code);
    assertThat(state.getPlayers()).hasSize(3);
    assertThat(
            registry
                .actor(roomId)
                .orElseThrow()
                .submit(() -> lobby.getRoom(roomId).orElseThrow().waitingPlayers().size())
                .get())
        .isZero();
    assertThat(
            state.getPlayers().stream()
                .filter(p -> host.equals(p.getUserId()))
                .findFirst()
                .orElseThrow()
                .getIsReady())
        .isTrue();
  }

  @Test
  void disconnectReconnectKeepsLevelAndActualHostLeaveFinishesEarly() throws Exception {
    Peer first = connect(host);
    Peer second = connect(guest);
    send(first, "start", Map.of());
    round(first, "ROUND_PREPARE", 1);
    send(first, "prepared", Map.of("roundNo", 1));
    send(second, "prepared", Map.of("roundNo", 1));
    round(first, "ROUND_START", 1);
    send(second, "listen-more", Map.of("roundNo", 1, "currentLevel", 2));
    await().atMost(Duration.ofSeconds(5)).until(() -> second.has("LISTEN_MORE_RESPONSE"));
    second.session.disconnect();
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                registry
                    .actor(roomId)
                    .orElseThrow()
                    .submit(
                        () ->
                            !lobby
                                .getRoom(roomId)
                                .orElseThrow()
                                .findPlayer(guest)
                                .orElseThrow()
                                .isConnected())
                    .join());
    Peer rejoined = connect(guest);
    send(rejoined, "resync", Map.of());
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                rejoined.messages.stream()
                    .anyMatch(n -> n.path("currentRound").path("currentLevel").asInt() == 5));
    JsonNode snapshot =
        rejoined.messages.stream()
            .filter(n -> n.path("currentRound").path("currentLevel").asInt() == 5)
            .findFirst()
            .orElseThrow();
    assertThat(
            snapshot.path("lastRoundAnswer").isNull()
                || snapshot.path("lastRoundAnswer").isMissingNode())
        .isTrue();
    send(first, "leave", Map.of());
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> listener.events().stream().anyMatch(e -> roomId.equals(e.roomId())));
    MatchFinished event =
        listener.events().stream().filter(e -> roomId.equals(e.roomId())).findFirst().orElseThrow();
    assertThat(event.players().getFirst().userId()).isEqualTo(guest);
    assertThat(event.players().getLast().userId()).isEqualTo(host);
    assertThat(event.players().getLast().left()).isTrue();
    assertThat(
            registry
                .actor(roomId)
                .orElseThrow()
                .submit(() -> lobby.getRoom(roomId).orElseThrow().hostId())
                .get())
        .isEqualTo(guest);
  }

  @Test
  void expiredDisconnectRemovesSeatAndFinishesWithDisconnectedPlayerLast() throws Exception {
    Peer first = connect(host);
    Peer second = connect(guest);
    send(first, "start", Map.of());
    round(first, "ROUND_PREPARE", 1);
    send(first, "prepared", Map.of("roundNo", 1));
    send(second, "prepared", Map.of("roundNo", 1));
    round(first, "ROUND_START", 1);
    second.session.disconnect();
    await()
        .atMost(Duration.ofSeconds(12))
        .until(() -> listener.events().stream().anyMatch(e -> roomId.equals(e.roomId())));
    MatchFinished event =
        listener.events().stream().filter(e -> roomId.equals(e.roomId())).findFirst().orElseThrow();
    assertThat(event.players().getFirst().userId()).isEqualTo(host);
    assertThat(event.players().getLast().userId()).isEqualTo(guest);
    assertThat(event.players().getLast().left()).isTrue();
    assertThat(registry.find(roomId).orElseThrow().hasMember(guest)).isFalse();
  }

  Peer connect(UUID id) throws Exception {
    Instant now = Instant.now();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .subject(id.toString())
                .claim("kind", "USER")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build());
    jwt.sign(
        new MACSigner(
            "meloo-test-super-secret-key-for-jwt-signing-minimum-256-bits-length-meloo"
                .getBytes(StandardCharsets.UTF_8)));
    var headers = new StompHeaders();
    headers.add("Authorization", "Bearer " + jwt.serialize());
    var websocket = new WebSocketHttpHeaders();
    websocket.setOrigin("http://localhost:5173");
    StompSession session =
        client
            .connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                websocket,
                headers,
                new StompSessionHandlerAdapter() {})
            .get(10, TimeUnit.SECONDS);
    sessions.add(session);
    Peer peer = new Peer(session);
    for (String destination :
        List.of(
            "/user/queue/private",
            "/user/queue/errors",
            "/topic/rooms/" + roomId + "/lobby",
            "/topic/rooms/" + roomId + "/round",
            "/topic/rooms/" + roomId + "/scoreboard")) {
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
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                registry
                    .actor(roomId)
                    .orElseThrow()
                    .submit(
                        () ->
                            lobby
                                .getRoom(roomId)
                                .orElseThrow()
                                .findPlayer(id)
                                .orElseThrow()
                                .isConnected())
                    .join());
    return peer;
  }

  void send(Peer peer, String path, Object payload) {
    peer.session.send("/app/rooms/" + roomId + "/" + path, payload);
  }

  void round(Peer peer, String type, int number) {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                peer.messages.stream()
                    .anyMatch(
                        n ->
                            type.equals(n.path("type").asText())
                                && n.path("roundNo").asInt() == number));
  }

  class Peer implements StompFrameHandler {
    final StompSession session;
    final List<JsonNode> messages = new CopyOnWriteArrayList<>();

    Peer(StompSession session) {
      this.session = session;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      messages.add(mapper.readTree(new String((byte[]) payload, StandardCharsets.UTF_8)));
    }

    boolean has(String type) {
      return messages.stream().anyMatch(n -> type.equals(n.path("type").asText()));
    }
  }

  @TestConfiguration
  static class ListenerConfiguration {
    @Bean
    FinishedListener finishedListener() {
      return new FinishedListener();
    }
  }

  static class FinishedListener {
    final List<MatchFinished> events = new CopyOnWriteArrayList<>();
    final Set<UUID> transactionalPublications = ConcurrentHashMap.newKeySet();
    final Set<UUID> transactionalDeliveries = ConcurrentHashMap.newKeySet();

    public List<MatchFinished> events() {
      return events;
    }

    public Set<UUID> publications() {
      return transactionalPublications;
    }

    public Set<UUID> deliveries() {
      return transactionalDeliveries;
    }

    @EventListener
    public void published(MatchFinished event) {
      if (TransactionSynchronizationManager.isActualTransactionActive())
        transactionalPublications.add(event.matchId());
    }

    @ApplicationModuleListener
    public void finished(MatchFinished event) {
      if (TransactionSynchronizationManager.isActualTransactionActive())
        transactionalDeliveries.add(event.matchId());
      events.add(event);
    }
  }
}
