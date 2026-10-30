package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.meloo.room.api.RoomDescriptor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RoomRegistryTest {

  private ThreadPoolTaskScheduler scheduler;
  private RoomRegistry registry;

  @BeforeEach
  void setUp() {
    scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.initialize();
    registry = new RoomRegistry(scheduler);
  }

  @AfterEach
  void tearDown() {
    registry.close();
    scheduler.shutdown();
  }

  @Test
  void disconnectIsQueuedAfterConnectionAndMultipleSessionsKeepPlayerConnected() {
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    List<String> changes = new CopyOnWriteArrayList<>();
    RoomDescriptor descriptor = new RoomDescriptor(roomId, Set.of(userId), false);

    registry.register(
        descriptor,
        new RoomConnectionHandler() {
          @Override
          public void connected(UUID connectedUserId) {
            changes.add("connected:" + connectedUserId);
          }

          @Override
          public void disconnected(UUID disconnectedUserId) {
            changes.add("disconnected:" + disconnectedUserId);
          }
        });
    registry.trackSession(roomId, userId, "session-a");
    registry.trackSession(roomId, userId, "session-b");
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(changes).containsExactly("connected:" + userId));
    registry.disconnectSession("session-a");

    assertThat(changes).containsExactly("connected:" + userId);
    registry.disconnectSession("session-b");
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(changes)
                    .containsExactly("connected:" + userId, "disconnected:" + userId));

    assertThat(registry.find(roomId)).contains(descriptor);
    assertThat(registry.isMember(roomId, userId)).isTrue();
    registry.remove(roomId);
    assertThat(registry.find(roomId)).isEmpty();
  }

  @Test
  void roomUpdatesAreAppliedInItsActorQueue() throws Exception {
    UUID roomId = UUID.randomUUID();
    UUID firstUser = UUID.randomUUID();
    UUID secondUser = UUID.randomUUID();
    RoomDescriptor initial = new RoomDescriptor(roomId, Set.of(firstUser), true);
    registry.register(initial);

    registry
        .update(new RoomDescriptor(roomId, Set.of(firstUser, secondUser), false))
        .get(5, TimeUnit.SECONDS);

    assertThat(registry.isMember(roomId, secondUser)).isTrue();
    assertThat(registry.find(roomId))
        .get()
        .extracting(RoomDescriptor::privateRoom)
        .isEqualTo(false);
  }
}
