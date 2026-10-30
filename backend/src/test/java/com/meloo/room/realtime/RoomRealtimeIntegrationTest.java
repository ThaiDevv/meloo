package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.meloo.IntegrationTest;
import com.meloo.room.api.RoomDescriptor;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Import(RoomRealtimeIntegrationTest.RealtimeTestConfiguration.class)
@TestPropertySource(properties = "meloo.room.realtime.heartbeat-interval=1s")
@org.springframework.boot.test.context.SpringBootTest(
    webEnvironment =
        org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomRealtimeIntegrationTest extends IntegrationTest {

  private static final String JWT_SECRET =
      "meloo-test-super-secret-key-for-jwt-signing-minimum-256-bits-length-meloo";

  @LocalServerPort private int port;
  @Autowired private RoomRegistry roomRegistry;
  @Autowired private RealtimeProbe probe;
  private WebSocketStompClient stompClient;
  private UUID roomId;
  private UUID firstUserId;
  private UUID secondUserId;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new StringMessageConverter());
    stompClient.start();
    roomId = UUID.randomUUID();
    firstUserId = UUID.randomUUID();
    secondUserId = UUID.randomUUID();
    roomRegistry.register(new RoomDescriptor(roomId, Set.of(firstUserId, secondUserId), false));
    probe.reset();
  }

  @AfterEach
  void tearDown() {
    roomRegistry.remove(roomId);
    stompClient.stop();
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

  private StompSession connect(UUID userId) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + signedJwt(userId));
    String url = "ws://localhost:" + port + "/ws";
    return stompClient
        .connectAsync(
            URI.create(url),
            new WebSocketHttpHeaders(),
            connectHeaders,
            new StompSessionHandlerAdapter() {})
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
}
