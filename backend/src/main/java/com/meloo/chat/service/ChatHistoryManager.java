package com.meloo.chat.service;

import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.config.ChatProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory bounded message history per room. Used to extract preceding messages for chat reports.
 */
@Component
public class ChatHistoryManager {

  private final int maxHistoryPerRoom;
  private final ConcurrentHashMap<UUID, List<ChatContextMessage>> roomHistories =
      new ConcurrentHashMap<>();

  public ChatHistoryManager(ChatProperties properties) {
    Objects.requireNonNull(properties, "properties");
    this.maxHistoryPerRoom = properties.maxHistoryPerRoom();
  }

  public synchronized void recordMessage(UUID roomId, ChatContextMessage message) {
    if (roomId == null || message == null) {
      return;
    }
    List<ChatContextMessage> history =
        roomHistories.computeIfAbsent(roomId, k -> new ArrayList<>());
    history.add(message);
    if (history.size() > maxHistoryPerRoom) {
      history.remove(0);
    }
  }

  public synchronized Optional<ChatContextMessage> findMessage(UUID roomId, UUID messageId) {
    if (roomId == null || messageId == null) {
      return Optional.empty();
    }
    List<ChatContextMessage> history = roomHistories.get(roomId);
    if (history == null) {
      return Optional.empty();
    }
    return history.stream().filter(m -> m.messageId().equals(messageId)).findFirst();
  }

  /**
   * Returns up to {@code count} messages sent immediately before the specified message in the room.
   */
  public synchronized List<ChatContextMessage> findPrecedingContext(
      UUID roomId, UUID messageId, int count) {
    if (roomId == null || messageId == null || count <= 0) {
      return List.of();
    }
    List<ChatContextMessage> history = roomHistories.get(roomId);
    if (history == null || history.isEmpty()) {
      return List.of();
    }

    int targetIndex = -1;
    for (int i = 0; i < history.size(); i++) {
      if (history.get(i).messageId().equals(messageId)) {
        targetIndex = i;
        break;
      }
    }

    if (targetIndex <= 0) {
      return List.of();
    }

    int startIndex = Math.max(0, targetIndex - count);
    return new ArrayList<>(history.subList(startIndex, targetIndex));
  }

  public synchronized void clearRoom(UUID roomId) {
    if (roomId != null) {
      roomHistories.remove(roomId);
    }
  }

  public synchronized void clearAll() {
    roomHistories.clear();
  }
}
