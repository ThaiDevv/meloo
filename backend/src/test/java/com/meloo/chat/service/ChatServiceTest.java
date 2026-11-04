package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meloo.chat.api.ChatErrorMessage;
import com.meloo.chat.api.ChatMessageEvent;
import com.meloo.chat.config.ChatProperties;
import com.meloo.room.api.ActiveRoundQuery;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ChatServiceTest {

  private ActiveRoundQuery activeRoundQuery;
  private ChatLeakDetector leakDetector;
  private BannedWordService bannedWordService;
  private ChatSanitizer sanitizer;
  private ChatRateLimiter rateLimiter;
  private ChatHistoryManager historyManager;
  private SimpMessagingTemplate messagingTemplate;
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    activeRoundQuery = mock(ActiveRoundQuery.class);
    bannedWordService = mock(BannedWordService.class);
    messagingTemplate = mock(SimpMessagingTemplate.class);

    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 50);
    leakDetector = new ChatLeakDetector();
    sanitizer = new ChatSanitizer(properties);
    rateLimiter = new ChatRateLimiter(properties);
    historyManager = new ChatHistoryManager(properties);

    chatService =
        new ChatService(
            Optional.of(activeRoundQuery),
            leakDetector,
            bannedWordService,
            sanitizer,
            rateLimiter,
            historyManager,
            messagingTemplate,
            null);
  }

  @Test
  @DisplayName("Broadcasts valid message to room topic and records in history")
  void broadcastsValidMessage() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    when(activeRoundQuery.findActiveAnswerTitle(roomId)).thenReturn(Optional.empty());
    when(bannedWordService.containsBannedWord(any())).thenReturn(false);

    chatService.registerNickname(senderId, "Alice");
    chatService.processMessage(roomId, senderId, "Chào cả phòng!");

    ArgumentCaptor<ChatMessageEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageEvent.class);
    verify(messagingTemplate)
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), eventCaptor.capture());

    ChatMessageEvent event = eventCaptor.getValue();
    assertThat(event.content()).isEqualTo("Chào cả phòng!");
    assertThat(event.senderId()).isEqualTo(senderId);
    assertThat(event.senderNickname()).isEqualTo("Alice");

    // Verified in history
    assertThat(historyManager.findMessage(roomId, event.messageId())).isPresent();
  }

  @Test
  @DisplayName("Blocks message exceeding 200 chars and sends private error (BR-16)")
  void blocksTooLongMessage() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();

    chatService.processMessage(roomId, senderId, "a".repeat(201));

    verify(messagingTemplate, never())
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));

    ArgumentCaptor<ChatErrorMessage> errorCaptor = ArgumentCaptor.forClass(ChatErrorMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(senderId.toString()), eq("/queue/errors"), errorCaptor.capture());

    assertThat(errorCaptor.getValue().code()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
  }

  @Test
  @DisplayName("Rejects HTML tags and sends private error (BR-16)")
  void rejectsHtmlTags() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();

    chatService.processMessage(roomId, senderId, "<script>alert('hack')</script>");

    verify(messagingTemplate, never())
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));

    ArgumentCaptor<ChatErrorMessage> errorCaptor = ArgumentCaptor.forClass(ChatErrorMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(senderId.toString()), eq("/queue/errors"), errorCaptor.capture());

    assertThat(errorCaptor.getValue().code()).isEqualTo("CHAT_HTML_NOT_ALLOWED");
  }

  @Test
  @DisplayName("Enforces 1 message per second rate limit and sends private error (BR-16)")
  void enforcesRateLimit() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    when(activeRoundQuery.findActiveAnswerTitle(roomId)).thenReturn(Optional.empty());
    when(bannedWordService.containsBannedWord(any())).thenReturn(false);

    // Message 1 -> passes
    chatService.processMessage(roomId, senderId, "Tin nhắn 1");
    verify(messagingTemplate)
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));

    // Message 2 immediately -> rate limited
    chatService.processMessage(roomId, senderId, "Tin nhắn 2");

    ArgumentCaptor<ChatErrorMessage> errorCaptor = ArgumentCaptor.forClass(ChatErrorMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(senderId.toString()), eq("/queue/errors"), errorCaptor.capture());

    assertThat(errorCaptor.getValue().code()).isEqualTo("CHAT_RATE_LIMITED");
  }

  @Test
  @DisplayName("Blocks answer leak during active round and sends private warning (BR-16)")
  void blocksAnswerLeakInActiveRound() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    when(activeRoundQuery.findActiveAnswerTitle(roomId)).thenReturn(Optional.of("Lạc Trôi"));

    chatService.processMessage(roomId, senderId, "Bài này là l a c   t r o i");

    verify(messagingTemplate, never())
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));

    ArgumentCaptor<ChatErrorMessage> errorCaptor = ArgumentCaptor.forClass(ChatErrorMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(senderId.toString()), eq("/queue/errors"), errorCaptor.capture());

    assertThat(errorCaptor.getValue().code()).isEqualTo("CHAT_ANSWER_LEAK");
  }

  @Test
  @DisplayName("Allows mentioning song title when round has ended (BR-16)")
  void allowsAnswerAfterRoundEnds() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    // Round is ended -> empty active answer
    when(activeRoundQuery.findActiveAnswerTitle(roomId)).thenReturn(Optional.empty());
    when(bannedWordService.containsBannedWord(any())).thenReturn(false);

    chatService.processMessage(roomId, senderId, "Vừa rồi là bài Lạc Trôi nè!");

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));
  }

  @Test
  @DisplayName("Blocks message with banned words and sends private warning (BR-17)")
  void blocksBannedWords() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    when(activeRoundQuery.findActiveAnswerTitle(roomId)).thenReturn(Optional.empty());
    when(bannedWordService.containsBannedWord("đồ khốn nạn")).thenReturn(true);

    chatService.processMessage(roomId, senderId, "đồ khốn nạn");

    verify(messagingTemplate, never())
        .convertAndSend(eq("/topic/rooms/" + roomId + "/chat"), any(ChatMessageEvent.class));

    ArgumentCaptor<ChatErrorMessage> errorCaptor = ArgumentCaptor.forClass(ChatErrorMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(senderId.toString()), eq("/queue/errors"), errorCaptor.capture());

    assertThat(errorCaptor.getValue().code()).isEqualTo("CHAT_BANNED_WORD");
  }
}
