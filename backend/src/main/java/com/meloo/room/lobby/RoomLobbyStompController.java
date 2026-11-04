package com.meloo.room.lobby;

import com.meloo.room.api.message.KickCommand;
import com.meloo.room.api.message.LeaveCommand;
import com.meloo.room.api.message.ReadyCommand;
import com.meloo.room.api.message.SettingsCommand;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Controller STOMP tiếp nhận các lệnh sảnh từ client theo AsyncAPI: /app/rooms/{roomId}/ready,
 * /settings, /kick, /leave.
 */
@Controller
public class RoomLobbyStompController {

  private final RoomLobbyService roomLobbyService;

  public RoomLobbyStompController(RoomLobbyService roomLobbyService) {
    this.roomLobbyService =
        Objects.requireNonNull(roomLobbyService, "roomLobbyService must not be null");
  }

  @MessageMapping("/rooms/{roomId}/ready")
  public void ready(
      @DestinationVariable UUID roomId, @Payload ReadyCommand command, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    roomLobbyService.handleReady(roomId, userId, command);
  }

  @MessageMapping("/rooms/{roomId}/settings")
  public void settings(
      @DestinationVariable UUID roomId, @Payload SettingsCommand command, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    roomLobbyService.handleSettings(roomId, userId, command);
  }

  @MessageMapping("/rooms/{roomId}/kick")
  public void kick(
      @DestinationVariable UUID roomId, @Payload KickCommand command, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    roomLobbyService.handleKick(roomId, userId, command);
  }

  @MessageMapping("/rooms/{roomId}/leave")
  public void leave(
      @DestinationVariable UUID roomId,
      @Payload(required = false) LeaveCommand command,
      Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    roomLobbyService.handleLeave(roomId, userId);
  }
}
