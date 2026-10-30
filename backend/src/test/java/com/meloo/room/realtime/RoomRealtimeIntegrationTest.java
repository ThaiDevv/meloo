package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.meloo.IntegrationTest;
import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.message.RoundStartEvent;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Import(RoomRealtimeIntegrationTest.RealtimeTestConfiguration.class)
@TestPropertySource(
    properties = {
      "meloo.room.realtime.heartbeat-interval=1s",
      "meloo.cors.allowed-origins=http://localhost:5173"
    })
@org.springframework.boot.test.context.SpringBootTest(
    webEnvironment =
        org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomRealtimeIntegrationTest extends IntegrationTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";
  private static final String JWT_SECRET =
      "meloo-test-super-secret-key-for-jwt-signing-minimum-256-bits-length-meloo";

  @LocalServerPort private int port;
  @Autowired private RoomRegistry roomRegistry;
  @Autowired private RealtimeProbe probe;
  @Autowired private RoomEventPublisher eventPublisher;
  @Autowired private SimpUserRegistry simpUserRegistry;
  private WebSocketStompClient stompClient;
  private ThreadPoolTaskScheduler clientHeartbeatScheduler;
  private UUID roomId;
  private UUID otherRoomId;
  private UUID firstUserId;
  private UUID secondUserId;
  private UUID otherRoomUserId;

  @BeforeEach
  void setUp() {
    clientHeartbeatScheduler = new ThreadPoolTaskScheduler();
    clientHeartbeatScheduler.setPoolSize(1);
    clientHeartbeatScheduler.initialize();
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(
        new CompositeMessageConverter(
            List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));
    stompClient.setTaskScheduler(clientHeartbeatScheduler);
    stompClient.setDefaultHeartbeat(new long[] {250, 250});
    stompClient.start();
    roomId = UUID.randomUUID();
    otherRoomId = UUID.randomUUID();
    firstUserId = UUID.randomUUID();
    secondUserId = UUID.randomUUID();
    otherRoomUserId = UUID.randomUUID();
    roomRegistry.register(new RoomDescriptor(roomId, Set.of(firstUserId, secondUserId), false));
    roomRegistry.register(new RoomDescriptor(otherRoomId, Set.of(otherRoomUserId), false));
    probe.reset();
  }

  @AfterEach
  void tearDown() {
    roomRegistry.remove(roomId);
    roomRegistry.remove(otherRoomId);
    stompClient.stop();
    clientHeartbeatScheduler.shutdown();
  }

  @Test
  void twoAuthenticatedStompClientsCanSubmitOneHundredCommandsToTheirRoom() throws Exception {
    StompSession firstSession = connect(firstUserId);
    StompSession secondSession = connect(secondUserId);
    CountDownLatch start = new CountDownLatch(1);

    try (var senders = Executors.newFixedThreadPool(2)) {
      var firstSender = senders.submit(() -> sendCommands(firstSession, start));
      var secondSender = senders.submit(() -> sendCommands(secondSession, start));
      start.countDown();
      firstSender.get(10, TimeUnit.SECONDS);
      secondSender.get(10, TimeUnit.SECONDS);
    }

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(probe.commandCount()).hasValue(100));
  }

  @Test
  void disconnectIsDetectedAndPassedThroughTheRoomActor() throws Exception {
    roomRegistry.remove(roomId);
    CountDownLatch connected = new CountDownLatch(1);
    CountDownLatch disconnected = new CountDownLatch(1);
    roomRegistry.register(
        new RoomDescriptor(roomId, Set.of(firstUserId), false),
        new RoomConnectionHandler() {
          @Override
          public void connected(UUID userId) {
            connected.countDown();
          }

          @Override
          public void disconnected(UUID userId) {
            disconnected.countDown();
          }
        });
    StompSession session = connect(firstUserId);
    session.send("/app/rooms/" + roomId + "/test", "increment");

    assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
    session.disconnect();

    assertThat(disconnected.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void websocketHandshakeAllowsConfiguredOrigin() throws Exception {
    StompSession session = connect(firstUserId);

    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void websocketHandshakeRejectsOriginOutsideCorsAllowList() {
    assertThatThrownBy(
            () ->
                connect(
                    firstUserId, "https://attacker.example", new StompSessionHandlerAdapter() {}))
        .satisfies(failure -> assertThat(rootCause(failure)).hasMessageContaining("403"));
  }

  @Test
  void anotherRoomsMemberCannotSubscribeOrSendToThisRoomOverStomp() throws Exception {
    StompErrorHandler subscribeErrors = new StompErrorHandler();
    StompSession subscriber = connect(otherRoomUserId, ALLOWED_ORIGIN, subscribeErrors);
    subscriber.subscribe("/topic/rooms/" + roomId + "/lobby", new StringFrameHandler());

    assertThat(subscribeErrors.awaitError()).isTrue();

    StompErrorHandler sendErrors = new StompErrorHandler();
    StompSession sender = connect(otherRoomUserId, ALLOWED_ORIGIN, sendErrors);
    sender.send("/app/rooms/" + roomId + "/test", "increment");

    assertThat(sendErrors.awaitError()).isTrue();
    assertThat(probe.commandCount()).hasValue(0);
  }

  @Test
  void clientCannotSendDirectlyToRoomTopicOverStomp() throws Exception {
    StompErrorHandler errors = new StompErrorHandler();
    StompSession session = connect(firstUserId, ALLOWED_ORIGIN, errors);

    session.send("/topic/rooms/" + roomId + "/lobby", "forged");

    assertThat(errors.awaitError()).isTrue();
    assertThat(probe.commandCount()).hasValue(0);
  }

  @Test
  void subscribedClientReceivesPublishedRoomEventWithServerTime() throws Exception {
    StompSession session = connect(firstUserId);
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<RoundStartEvent> payload = new AtomicReference<>();
    String destination = "/topic/rooms/" + roomId + "/round";
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
            return RoundStartEvent.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object body) {
            payload.set((RoundStartEvent) body);
            received.countDown();
          }
        });

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        simpUserRegistry.findSubscriptions(
                            subscription -> destination.equals(subscription.getDestination())))
                    .hasSize(1));
    Instant serverTime = Instant.parse("2026-10-30T00:00:00Z");
    eventPublisher.publish(
        roomId, RoomEventPublisher.RoomTopic.ROUND, new RoundStartEvent(1, 45_000, serverTime));

    assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(payload.get().type()).isEqualTo("ROUND_START");
    assertThat(payload.get().serverTime()).isEqualTo(serverTime);
  }

  @Test
  void missedClientHeartbeatIsDetectedAndPassedThroughTheRoomActor() throws Exception {
    roomRegistry.remove(roomId);
    CountDownLatch connected = new CountDownLatch(1);
    CountDownLatch disconnected = new CountDownLatch(1);
    roomRegistry.register(
        new RoomDescriptor(roomId, Set.of(firstUserId), false),
        new RoomConnectionHandler() {
          @Override
          public void connected(UUID userId) {
            connected.countDown();
          }

          @Override
          public void disconnected(UUID userId) {
            disconnected.countDown();
          }
        });
    StompSession session = connect(firstUserId);
    session.send("/app/rooms/" + roomId + "/test", "increment");
    assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

    clientHeartbeatScheduler.shutdown();

    assertThat(disconnected.await(8, TimeUnit.SECONDS)).isTrue();
  }

  private StompSession connect(UUID userId) throws Exception {
    return connect(userId, ALLOWED_ORIGIN, new StompSessionHandlerAdapter() {});
  }

  private StompSession connect(
      UUID userId, String origin, StompSessionHandlerAdapter sessionHandler) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + signedJwt(userId));
    WebSocketHttpHeaders webSocketHeaders = new WebSocketHttpHeaders();
    webSocketHeaders.setOrigin(origin);
    String url = "ws://localhost:" + port + "/ws";
    return stompClient
        .connectAsync(URI.create(url), webSocketHeaders, connectHeaders, sessionHandler)
        .get(10, TimeUnit.SECONDS);
  }

  private void sendCommands(StompSession session, CountDownLatch start) {
    try {
      start.await();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while starting concurrent STOMP sends", failure);
    }
    for (int index = 0; index < 50; index++) {
      session.send("/app/rooms/" + roomId + "/test", "increment");
    }
  }

  private String signedJwt(UUID userId) throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RealtimeTestConfiguration {

    @Bean
    RealtimeProbe realtimeProbe() {
      return new RealtimeProbe();
    }

    @Bean
    RealtimeTestController realtimeTestController(RoomRegistry roomRegistry, RealtimeProbe probe) {
      return new RealtimeTestController(roomRegistry, probe);
    }
  }

  @Controller
  static class RealtimeTestController {

    private final RoomRegistry roomRegistry;
    private final RealtimeProbe probe;

    RealtimeTestController(RoomRegistry roomRegistry, RealtimeProbe probe) {
      this.roomRegistry = roomRegistry;
      this.probe = probe;
    }

    @MessageMapping("/rooms/{roomId}/test")
    void increment(@DestinationVariable UUID roomId) {
      roomRegistry.actor(roomId).orElseThrow().execute(probe::increment);
    }
  }

  static class RealtimeProbe {

    private final AtomicInteger commandCount = new AtomicInteger();

    AtomicInteger commandCount() {
      return commandCount;
    }

    void increment() {
      commandCount.incrementAndGet();
    }

    void reset() {
      commandCount.set(0);
    }
  }

  private static class StringFrameHandler implements StompFrameHandler {

    @Override
    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {}
  }

  private static class StompErrorHandler extends StompSessionHandlerAdapter {

    private final CountDownLatch errorReceived = new CountDownLatch(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    @Override
    public void handleException(
        StompSession session,
        StompCommand command,
        StompHeaders headers,
        byte[] payload,
        Throwable exception) {
      failure.compareAndSet(null, exception);
      errorReceived.countDown();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      failure.compareAndSet(null, exception);
      errorReceived.countDown();
    }

    boolean awaitError() throws InterruptedException {
      return errorReceived.await(5, TimeUnit.SECONDS) && failure.get() != null;
    }
  }
}
