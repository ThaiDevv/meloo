package com.meloo.room.realtime;

import com.meloo.room.api.RoomDirectory;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class RoomStompChannelInterceptor implements ChannelInterceptor {

  private final JwtDecoder jwtDecoder;
  private final RoomDirectory roomDirectory;
  private final RoomRegistry roomRegistry;

  public RoomStompChannelInterceptor(
      JwtDecoder jwtDecoder, RoomDirectory roomDirectory, RoomRegistry roomRegistry) {
    this.jwtDecoder = jwtDecoder;
    this.roomDirectory = roomDirectory;
    this.roomRegistry = roomRegistry;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    if (accessor.getCommand() == StompCommand.CONNECT) {
      authenticate(accessor);
      return message;
    }

    if (accessor.getCommand() == StompCommand.SEND
        && isRoomDestination(accessor.getDestination(), "/app/rooms/")) {
      authorizeRoomAccess(accessor, "/app/rooms/");
    } else if (accessor.getCommand() == StompCommand.SUBSCRIBE
        && isRoomDestination(accessor.getDestination(), "/topic/rooms/")) {
      authorizeRoomAccess(accessor, "/topic/rooms/");
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    String authorization = accessor.getFirstNativeHeader("Authorization");
    if (authorization == null) {
      authorization = accessor.getFirstNativeHeader("authorization");
    }
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AccessDeniedException("A bearer JWT is required to connect");
    }

    try {
      Jwt jwt = jwtDecoder.decode(authorization.substring(7).trim());
      UUID userId = UUID.fromString(jwt.getSubject());
      accessor.setUser(
          UsernamePasswordAuthenticationToken.authenticated(
              userId.toString(), null, java.util.List.of()));
    } catch (RuntimeException failure) {
      throw new AccessDeniedException("The STOMP bearer JWT is invalid", failure);
    }
  }

  private void authorizeRoomAccess(StompHeaderAccessor accessor, String prefix) {
    String destination = accessor.getDestination();
    String[] segments = destination.substring(prefix.length()).split("/");
    if (segments.length < 2 || segments[0].isBlank()) {
      throw new AccessDeniedException("A room command or topic destination is required");
    }

    UUID roomId;
    UUID userId;
    try {
      roomId = UUID.fromString(segments[0]);
      userId = UUID.fromString(accessor.getUser().getName());
    } catch (RuntimeException failure) {
      throw new AccessDeniedException("The STOMP destination or principal is invalid", failure);
    }

    if (!roomDirectory.isMember(roomId, userId)) {
      throw new AccessDeniedException("Only room members can use room destinations");
    }
    String sessionId = accessor.getSessionId();
    if (sessionId != null) {
      roomRegistry.trackSession(roomId, userId, sessionId);
    }
  }

  private static boolean isRoomDestination(String destination, String prefix) {
    return destination != null && destination.startsWith(prefix);
  }
}
