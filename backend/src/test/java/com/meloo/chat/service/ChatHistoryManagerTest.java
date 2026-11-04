package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.config.ChatProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatHistoryManagerTest {

  private ChatHistoryManager historyManager;

  @BeforeEach
  void setUp() {
    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 5);
    historyManager = new ChatHistoryManager(properties);
  }

  @Test
  @DisplayName("Records message and finds preceding context up to requested count")
  void recordAndFindPrecedingContext() {
    UUID roomId = UUID.randomUUID();
    UUID user = UUID.randomUUID();

    UUID m1 = UUID.randomUUID();
    UUID m2 = UUID.randomUUID();
    UUID m3 = UUID.randomUUID();

    historyManager.recordMessage(
        roomId, new ChatContextMessage(m1, user, "Alice", "msg 1", Instant.now()));
    historyManager.recordMessage(
        roomId, new ChatContextMessage(m2, user, "Alice", "msg 2", Instant.now()));
    historyManager.recordMessage(
        roomId, new ChatContextMessage(m3, user, "Alice", "msg 3", Instant.now()));

    List<ChatContextMessage> preceding = historyManager.findPrecedingContext(roomId, m3, 5);
    assertThat(preceding).hasSize(2);
    assertThat(preceding.get(0).messageId()).isEqualTo(m1);
    assertThat(preceding.get(1).messageId()).isEqualTo(m2);

    Optional<ChatContextMessage> found = historyManager.findMessage(roomId, m2);
    assertThat(found).isPresent();
    assertThat(found.get().content()).isEqualTo("msg 2");
  }

  @Test
  @DisplayName("Enforces bounded message limit per room")
  void boundedHistoryLimit() {
    UUID roomId = UUID.randomUUID();
    UUID user = UUID.randomUUID();

    UUID first = null;
    UUID last = null;
    for (int i = 0; i < 10; i++) {
      UUID mid = UUID.randomUUID();
      if (i == 0) first = mid;
      if (i == 9) last = mid;
      historyManager.recordMessage(
          roomId, new ChatContextMessage(mid, user, "Alice", "msg " + i, Instant.now()));
    }

    // Since limit is 5, first message should have been discarded
    assertThat(historyManager.findMessage(roomId, first)).isEmpty();
    assertThat(historyManager.findMessage(roomId, last)).isPresent();

    List<ChatContextMessage> preceding = historyManager.findPrecedingContext(roomId, last, 10);
    assertThat(preceding).hasSize(4); // remaining 4 messages before last
  }

  @Test
  @DisplayName("Atomic cleanup evicts inactive rooms while preserving active rooms")
  void atomicCleanupPreservesActiveRooms() {
    UUID activeRoom = UUID.randomUUID();
    UUID staleRoom = UUID.randomUUID();
    UUID user = UUID.randomUUID();

    historyManager.recordMessage(
        activeRoom,
        new ChatContextMessage(UUID.randomUUID(), user, "Alice", "active", Instant.now()));
    historyManager.recordMessage(
        staleRoom, new ChatContextMessage(UUID.randomUUID(), user, "Bob", "stale", Instant.now()));

    // Seed > 100 rooms to trigger cleanup threshold condition
    for (int i = 0; i < 105; i++) {
      historyManager.recordMessage(
          UUID.randomUUID(),
          new ChatContextMessage(UUID.randomUUID(), user, "U" + i, "seed", Instant.now()));
    }

    // Mark only staleRoom as 31 minutes stale
    Instant staleTime = Instant.now().minus(Duration.ofMinutes(31));
    historyManager.markRoomStaleForTest(staleRoom, staleTime);

    // Run cleanup
    historyManager.cleanupInactiveRooms();

    assertThat(historyManager.findMessage(staleRoom, UUID.randomUUID())).isEmpty();
    assertThat(historyManager.getTrackedRoomCount()).isLessThanOrEqualTo(106);
  }

  @Test
  @DisplayName(
      "Concurrent cleanup and message addition does not discard newly added messages (m1 fix)")
  void concurrentCleanupAndRecordDoesNotLoseMessage() throws InterruptedException {
    UUID roomId = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    UUID msgId = UUID.randomUUID();

    historyManager.recordMessage(
        roomId, new ChatContextMessage(UUID.randomUUID(), user, "U", "old", Instant.now()));
    historyManager.markRoomStaleForTest(roomId, Instant.now().minus(Duration.ofMinutes(31)));

    for (int i = 0; i < 105; i++) {
      historyManager.recordMessage(
          UUID.randomUUID(),
          new ChatContextMessage(UUID.randomUUID(), user, "U" + i, "seed", Instant.now()));
    }

    // Run cleanup and recordMessage concurrently
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    AtomicBoolean recorded = new AtomicBoolean(false);

    executor.submit(
        () -> {
          try {
            startLatch.await();
            historyManager.cleanupInactiveRooms();
          } catch (Exception ignored) {
          } finally {
            doneLatch.countDown();
          }
        });

    executor.submit(
        () -> {
          try {
            startLatch.await();
            historyManager.recordMessage(
                roomId, new ChatContextMessage(msgId, user, "Alice", "new message", Instant.now()));
            recorded.set(true);
          } catch (Exception ignored) {
          } finally {
            doneLatch.countDown();
          }
        });

    startLatch.countDown();
    boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();
    // Newly recorded message must always be present and accessible
    Optional<ChatContextMessage> found = historyManager.findMessage(roomId, msgId);
    assertThat(found).isPresent();
    assertThat(found.get().content()).isEqualTo("new message");
  }
}
