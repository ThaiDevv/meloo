package com.meloo.chat.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meloo.chat.api.ChatCommand;
import com.meloo.chat.service.ChatService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatStompControllerTest {

  private ChatService chatService;
  private ChatStompController controller;

  @BeforeEach
  void setUp() {
    chatService = mock(ChatService.class);
    controller = new ChatStompController(chatService);
  }

  @Test
  @DisplayName("Delegates STOMP chat command to ChatService with roomId and senderId")
  void delegatesToChatService() {
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Principal principal = () -> userId.toString();
    ChatCommand command = new ChatCommand("Hello world");

    controller.handleChat(roomId, command, principal);

    verify(chatService).processMessage(roomId, userId, "Hello world");
  }

  @Test
  @DisplayName("Safely ignores null principal or null command")
  void ignoresNullInputs() {
    UUID roomId = UUID.randomUUID();

    controller.handleChat(roomId, null, () -> UUID.randomUUID().toString());
    controller.handleChat(roomId, new ChatCommand("test"), null);

    verify(chatService, never()).processMessage(any(), any(), any());
  }

  @Test
  @DisplayName("Safely ignores non-UUID principal name")
  void ignoresInvalidPrincipal() {
    UUID roomId = UUID.randomUUID();
    Principal principal = () -> "invalid-uuid";

    controller.handleChat(roomId, new ChatCommand("test"), principal);

    verify(chatService, never()).processMessage(any(), any(), any());
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
