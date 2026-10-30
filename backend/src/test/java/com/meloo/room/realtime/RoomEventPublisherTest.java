package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.meloo.room.api.message.RoundStartEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RoomEventPublisherTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final RoomEventPublisher publisher = new RoomEventPublisher(messagingTemplate);

  @Test
  void publishesTypedRoomMessagesOnAsyncApiTopicWithServerTime() {
    UUID roomId = UUID.randomUUID();
    RoundStartEvent event = new RoundStartEvent(2, 45_000, Instant.parse("2026-10-30T00:00:00Z"));

    publisher.publish(roomId, RoomEventPublisher.RoomTopic.ROUND, event);

    verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/round", event);
  }

  @Test
  void refusesAnEventWithoutServerTime() {
    RoundStartEvent event = new RoundStartEvent(2, 45_000, null);

    assertThatThrownBy(
            () -> publisher.publish(UUID.randomUUID(), RoomEventPublisher.RoomTopic.ROUND, event))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("serverTime");
  }
}
