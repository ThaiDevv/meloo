package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.RoomRankPublisher.Change;
import com.meloo.room.api.message.PlayerRankChangePayload;
import com.meloo.room.api.message.RankUpdatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class QueuedRoomRankPublisherTest {
  @Test
  void enqueuesBehindTheCommandPublishingMatchFinished() throws Exception {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.initialize();
    try (var rooms = new RoomRegistry(scheduler)) {
      UUID room = UUID.randomUUID();
      var actor = rooms.register(new RoomDescriptor(room, Set.of(), false));
      var messages = mock(RoomEventPublisher.class);
      var publisher = new QueuedRoomRankPublisher(rooms, messages);
      var began = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var matchFinished =
          actor.execute(
              () -> {
                began.countDown();
                try {
                  assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                  throw new IllegalStateException(failure);
                }
                messages.publish(
                    room,
                    RoomEventPublisher.RoomTopic.SCOREBOARD,
                    new com.meloo.room.api.message.MatchFinishedEvent(
                        UUID.randomUUID(), List.of(), Instant.now()));
              });
      try {
        assertThat(began.await(5, TimeUnit.SECONDS)).isTrue();
        UUID user = UUID.randomUUID();
        var rank =
            new RankUpdatedEvent(
                UUID.randomUUID(),
                List.of(new PlayerRankChangePayload(user, -5, 0)),
                Instant.now());
        var sent =
            publisher.publish(
                room, rank.matchId(), List.of(new Change(user, -5, 0)), rank.serverTime());
        assertThat(sent).isNotDone();
        verifyNoInteractions(messages);
        release.countDown();
        matchFinished.get(5, TimeUnit.SECONDS);
        sent.get(5, TimeUnit.SECONDS);
        var order = inOrder(messages);
        order
            .verify(messages)
            .publish(
                eq(room),
                eq(RoomEventPublisher.RoomTopic.SCOREBOARD),
                isA(com.meloo.room.api.message.MatchFinishedEvent.class));
        order.verify(messages).publish(room, RoomEventPublisher.RoomTopic.SCOREBOARD, rank);
      } finally {
        release.countDown();
      }
      rooms.remove(room);
      publisher.publish(room, UUID.randomUUID(), List.of(), Instant.now()).join();
      verifyNoMoreInteractions(messages);
    } finally {
      scheduler.shutdown();
    }
  }

  @Test
  void propagatesBrokerFailureSoRegistryCanRetry() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.initialize();
    try (var rooms = new RoomRegistry(scheduler)) {
      UUID room = UUID.randomUUID();
      rooms.register(new RoomDescriptor(room, Set.of(), false));
      var messages = mock(RoomEventPublisher.class);
      doThrow(new IllegalStateException("Broker unavailable"))
          .when(messages)
          .publish(any(), any(), any());
      assertThatThrownBy(
              () ->
                  new QueuedRoomRankPublisher(rooms, messages)
                      .publish(room, UUID.randomUUID(), List.of(), Instant.now())
                      .join())
          .hasCauseInstanceOf(IllegalStateException.class);
    } finally {
      scheduler.shutdown();
    }
  }
}
