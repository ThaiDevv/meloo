package com.meloo.chat.service;

import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.config.ChatProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory bounded message history per room. Used to extract preceding messages for chat reports.
 * Thread-safe with per-room concurrency and automatic eviction of inactive rooms.
 */
@Component
public class ChatHistoryManager {

  private final int maxHistoryPerRoom;
  private final ConcurrentHashMap<UUID, RoomHistoryHolder> roomHistories =
      new ConcurrentHashMap<>();

  public ChatHistoryManager(ChatProperties properties) {
    Objects.requireNonNull(properties, "properties");
    this.maxHistoryPerRoom = properties.maxHistoryPerRoom();
  }

  public void recordMessage(UUID roomId, ChatContextMessage message) {
    if (roomId == null || message == null) {
      return;
    }
    if (roomHistories.size() > 100) {
      Instant threshold = Instant.now().minus(Duration.ofMinutes(30));
      roomHistories.entrySet().removeIf(e -> e.getValue().lastAccessed().isBefore(threshold));
    }
    roomHistories
        .computeIfAbsent(roomId, k -> new RoomHistoryHolder(maxHistoryPerRoom))
        .add(message);
  }

  public Optional<ChatContextMessage> findMessage(UUID roomId, UUID messageId) {
    if (roomId == null || messageId == null) {
      return Optional.empty();
    }
    RoomHistoryHolder holder = roomHistories.get(roomId);
    return holder != null ? holder.find(messageId) : Optional.empty();
  }

  /**
   * Returns up to {@code count} messages sent immediately before the specified message in the room.
   */
  public List<ChatContextMessage> findPrecedingContext(UUID roomId, UUID messageId, int count) {
    if (roomId == null || messageId == null || count <= 0) {
      return List.of();
    }
    RoomHistoryHolder holder = roomHistories.get(roomId);
    return holder != null ? holder.findPreceding(messageId, count) : List.of();
  }

  public void clearRoom(UUID roomId) {
    if (roomId != null) {
      roomHistories.remove(roomId);
    }
  }

  public void clearAll() {
    roomHistories.clear();
  }

  private static class RoomHistoryHolder {
    private final int limit;
    private final List<ChatContextMessage> messages = new ArrayList<>();
    private volatile Instant lastAccessed = Instant.now();

    RoomHistoryHolder(int limit) {
      this.limit = limit;
    }

    synchronized void add(ChatContextMessage msg) {
      lastAccessed = Instant.now();
      messages.add(msg);
      if (messages.size() > limit) {
        messages.remove(0);
      }
    }

    synchronized Optional<ChatContextMessage> find(UUID messageId) {
      lastAccessed = Instant.now();
      return messages.stream().filter(m -> m.messageId().equals(messageId)).findFirst();
    }

    synchronized List<ChatContextMessage> findPreceding(UUID messageId, int count) {
      lastAccessed = Instant.now();
      int targetIndex = -1;
      for (int i = 0; i < messages.size(); i++) {
        if (messages.get(i).messageId().equals(messageId)) {
          targetIndex = i;
          break;
        }
      }

      if (targetIndex <= 0) {
        return List.of();
      }

      int startIndex = Math.max(0, targetIndex - count);
      return new ArrayList<>(messages.subList(startIndex, targetIndex));
    }

    Instant lastAccessed() {
      return lastAccessed;
    }
  }
}
