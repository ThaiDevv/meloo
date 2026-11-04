package com.meloo.chat.web;

import com.meloo.chat.api.ChatCommand;
import com.meloo.chat.service.ChatService;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ChatStompController {

  private final ChatService chatService;

  public ChatStompController(ChatService chatService) {
    this.chatService = Objects.requireNonNull(chatService, "chatService");
  }

  @MessageMapping("/rooms/{roomId}/chat")
  public void handleChat(
      @DestinationVariable UUID roomId, @Payload ChatCommand command, Principal principal) {
    if (principal == null || command == null) {
      return;
    }

    UUID userId;
    try {
      userId = UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      return;
    }

    chatService.processMessage(roomId, userId, command.content());
  }
}
