package com.meloo.chat.service;

import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.api.ChatErrorMessage;
import com.meloo.chat.api.ChatMessageEvent;
import com.meloo.identity.events.GuestCreated;
import com.meloo.room.api.ActiveRoundQuery;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Core chat orchestration: - BR-16: Length 1-200, 1 msg/sec rate limit, answer leak blocking during
 * active rounds. - BR-17: Banned words filtering with cache. - Rejects HTML. - Broadcasts valid
 * messages to /topic/rooms/{roomId}/chat. - Sends private warnings to /user/{userId}/queue/errors.
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private final Optional<ActiveRoundQuery> activeRoundQuery;
  private final ChatLeakDetector leakDetector;
  private final BannedWordService bannedWordService;
  private final ChatSanitizer sanitizer;
  private final ChatRateLimiter rateLimiter;
  private final ChatHistoryManager historyManager;
  private final SimpMessagingTemplate messagingTemplate;
  private final @Nullable NamedParameterJdbcTemplate jdbcTemplate;
  private final ConcurrentHashMap<UUID, String> nicknameCache = new ConcurrentHashMap<>();

  public ChatService(
      Optional<ActiveRoundQuery> activeRoundQuery,
      ChatLeakDetector leakDetector,
      BannedWordService bannedWordService,
      ChatSanitizer sanitizer,
      ChatRateLimiter rateLimiter,
      ChatHistoryManager historyManager,
      SimpMessagingTemplate messagingTemplate,
      @Autowired(required = false) @Nullable NamedParameterJdbcTemplate jdbcTemplate) {
    this.activeRoundQuery = Objects.requireNonNull(activeRoundQuery, "activeRoundQuery");
    this.leakDetector = Objects.requireNonNull(leakDetector, "leakDetector");
    this.bannedWordService = Objects.requireNonNull(bannedWordService, "bannedWordService");
    this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    this.historyManager = Objects.requireNonNull(historyManager, "historyManager");
    this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate");
    this.jdbcTemplate = jdbcTemplate;
  }

  @ApplicationModuleListener
  void on(GuestCreated event) {
    if (event != null && event.userId() != null && event.nickname() != null) {
      nicknameCache.put(event.userId(), event.nickname());
    }
  }

  /** Processes an incoming chat message for a room. */
  public void processMessage(UUID roomId, UUID senderId, @Nullable String rawContent) {
    if (roomId == null || senderId == null) {
      return;
    }

    // 1. Validate length and reject HTML
    ChatSanitizer.ValidationResult sanitization = sanitizer.validate(rawContent);
    if (!sanitization.valid()) {
      sendPrivateError(senderId, sanitization.errorCode(), sanitization.errorMessage());
      return;
    }
    String content = sanitization.sanitizedContent();

    // 2. Check rate limit (1 msg/sec/user)
    if (!rateLimiter.tryAcquire(senderId)) {
      sendPrivateError(senderId, "CHAT_RATE_LIMITED", "Bạn chỉ có thể gửi 1 tin mỗi giây (BR-16)");
      return;
    }

    // 3. Check answer leak during active round (BR-16)
    if (activeRoundQuery.isPresent()) {
      Optional<String> activeAnswer = activeRoundQuery.get().findActiveAnswerTitle(roomId);
      if (activeAnswer.isPresent() && leakDetector.isLeakingAnswer(content, activeAnswer.get())) {
        sendPrivateError(
            senderId, "CHAT_ANSWER_LEAK", "Tin nhắn bị chặn do vi phạm quy tắc phòng (BR-16)");
        return;
      }
    }

    // 4. Check banned words (BR-17)
    if (bannedWordService.containsBannedWord(content)) {
      sendPrivateError(
          senderId, "CHAT_BANNED_WORD", "Tin nhắn bị chặn do chứa từ ngữ không phù hợp (BR-17)");
      return;
    }

    // 5. Message passed all filters -> broadcast
    String senderNickname = resolveNickname(senderId);
    UUID messageId = UUID.randomUUID();
    Instant now = Instant.now();

    historyManager.recordMessage(
        roomId, new ChatContextMessage(messageId, senderId, senderNickname, content, now));

    ChatMessageEvent event =
        new ChatMessageEvent(messageId, senderId, senderNickname, content, now, now);

    messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/chat", event);
  }

  public String resolveNickname(UUID userId) {
    String cached = nicknameCache.get(userId);
    if (cached != null) {
      return cached;
    }

    if (jdbcTemplate != null) {
      try {
        String nickname =
            jdbcTemplate.queryForObject(
                "SELECT nickname FROM app_user WHERE id = :id", Map.of("id", userId), String.class);
        if (nickname != null && !nickname.isBlank()) {
          nicknameCache.put(userId, nickname);
          return nickname;
        }
      } catch (Exception e) {
        log.debug("User {} not found in app_user, using fallback", userId);
      }
    }

    String fallback = "Player-" + userId.toString().substring(0, 8);
    nicknameCache.put(userId, fallback);
    return fallback;
  }

  void registerNickname(UUID userId, String nickname) {
    nicknameCache.put(userId, nickname);
  }

  private void sendPrivateError(UUID userId, String code, String message) {
    Instant now = Instant.now();
    ChatErrorMessage err = new ChatErrorMessage(code, message, null, now);
    messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/errors", err);
  }
}
