package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meloo.room.api.RoomDescriptor;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class RoomStompChannelInterceptorTest {

  private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
  private final MessageChannel channel = mock(MessageChannel.class);
  private ThreadPoolTaskScheduler scheduler;
  private RoomRegistry roomRegistry;
  private RoomStompChannelInterceptor interceptor;

  @BeforeEach
  void setUp() {
    scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.initialize();
    roomRegistry = new RoomRegistry(scheduler);
    interceptor = new RoomStompChannelInterceptor(jwtDecoder, roomRegistry, roomRegistry);
  }

  @AfterEach
  void tearDown() {
    roomRegistry.close();
    scheduler.shutdown();
  }

  @Test
  void connectDecodesBearerJwtAndSetsRoomPrincipal() {
    UUID userId = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("valid-token")
            .header("alg", "HS256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.addNativeHeader("Authorization", "Bearer valid-token");

    Message<?> result = interceptor.preSend(message(accessor), channel);
    StompHeaderAccessor resultAccessor =
        MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

    assertThat(resultAccessor.getUser().getName()).isEqualTo(userId.toString());
  }

  @Test
  void rejectsRoomSendAndSubscriptionWhenUserIsNotAMember() {
    UUID roomId = UUID.randomUUID();
    UUID otherRoomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    roomRegistry.register(new RoomDescriptor(roomId, Set.of(userId), false));

    for (var destination :
        Set.of(
            new DestinationFrame(StompCommand.SEND, "/app/rooms/" + otherRoomId + "/guess"),
            new DestinationFrame(
                StompCommand.SUBSCRIBE, "/topic/rooms/" + otherRoomId + "/round"))) {
      StompHeaderAccessor accessor =
          roomAccessor(destination.command(), destination.destination(), userId);

      assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Only room members");
    }
  }

  @Test
  void allowsBothRoomSendAndSubscriptionForAMember() {
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    roomRegistry.register(new RoomDescriptor(roomId, Set.of(userId), false));

    assertThatNoException()
        .isThrownBy(
            () ->
                interceptor.preSend(
                    message(
                        roomAccessor(StompCommand.SEND, "/app/rooms/" + roomId + "/guess", userId)),
                    channel));
    assertThatNoException()
        .isThrownBy(
            () ->
                interceptor.preSend(
                    message(
                        roomAccessor(
                            StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId + "/round", userId)),
                    channel));
  }

  @Test
  void rejectsConnectWithoutBearerToken() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

    assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("bearer JWT");
  }

  private static StompHeaderAccessor roomAccessor(
      StompCommand command, String destination, UUID userId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setSessionId("test-session");
    accessor.setUser(
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(userId.toString(), null, java.util.List.of()));
    accessor.setLeaveMutable(true);
    return accessor;
  }

  private static Message<byte[]> message(StompHeaderAccessor accessor) {
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private record DestinationFrame(StompCommand command, String destination) {}
}
