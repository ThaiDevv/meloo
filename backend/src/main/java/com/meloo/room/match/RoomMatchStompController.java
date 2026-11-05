package com.meloo.room.match;

import com.meloo.room.api.message.*;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class RoomMatchStompController {
  private final RoomMatchService service;

  public RoomMatchStompController(RoomMatchService service) {
    this.service = service;
  }

  @MessageMapping("/rooms/{roomId}/start")
  public void start(@DestinationVariable UUID roomId, Principal principal) {
    service.start(roomId, UUID.fromString(principal.getName()));
  }

  @MessageMapping("/rooms/{roomId}/prepared")
  public void prepared(
      @DestinationVariable UUID roomId, @Payload PreparedCommand command, Principal principal) {
    service.prepared(roomId, UUID.fromString(principal.getName()), command.roundNo());
  }

  @MessageMapping("/rooms/{roomId}/guess")
  public void guess(
      @DestinationVariable UUID roomId,
      @Payload GuessCommand command,
      Principal principal,
      @Header(name = RoomMatchIngress.RECEIVED_AT) Instant receivedAt) {
    service.guess(roomId, UUID.fromString(principal.getName()), command, receivedAt);
  }

  @MessageMapping("/rooms/{roomId}/listen-more")
  public void listenMore(
      @DestinationVariable UUID roomId,
      @Payload ListenMoreCommand command,
      Principal principal,
      @Header(name = RoomMatchIngress.RECEIVED_AT) Instant receivedAt) {
    service.listenMore(
        roomId,
        UUID.fromString(principal.getName()),
        command.roundNo(),
        command.currentLevel(),
        receivedAt);
  }

  @MessageMapping("/rooms/{roomId}/give-up")
  public void giveUp(
      @DestinationVariable UUID roomId,
      @Payload GiveUpCommand command,
      Principal principal,
      @Header(name = RoomMatchIngress.RECEIVED_AT) Instant receivedAt) {
    service.giveUp(roomId, UUID.fromString(principal.getName()), command.roundNo(), receivedAt);
  }

  @MessageMapping("/rooms/{roomId}/resync")
  public void resync(@DestinationVariable UUID roomId, Principal principal) {
    service.resync(roomId, UUID.fromString(principal.getName()));
  }
}
