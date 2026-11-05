package com.meloo.room.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;

class RoomMatchIngressTest {
  @Test
  void stampsTrustedReceiptBeforeDispatchAndOverridesClientHeader() {
    Instant now = Instant.parse("2026-11-05T00:00:00Z");
    RoomMatchIngressTracker tracker = new RoomMatchIngressTracker();
    RoomMatchIngress ingress = new RoomMatchIngress(Clock.fixed(now, ZoneOffset.UTC), tracker);
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/rooms/00000000-0000-0000-0000-000000000000/guess");
    headers.setHeader(RoomMatchIngress.RECEIVED_AT, now.minusSeconds(60));
    headers.setNativeHeader(RoomMatchIngress.RECEIVED_AT, now.minusSeconds(60).toString());
    Message<?> result =
        ingress.preSend(
            MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
            mock(MessageChannel.class));
    assertThat(result.getHeaders().get(RoomMatchIngress.RECEIVED_AT)).isEqualTo(now);
    assertThat(result.getHeaders().get(RoomMatchIngress.TRACKER_TOKEN)).isInstanceOf(UUID.class);
    assertThat(
            org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(
                result, StompHeaderAccessor.class))
        .isNotNull();
  }

  @Test
  void tracksOnlyDeadlineRelevantGameplayFramesAndReleasesAfterHandler() {
    Instant now = Instant.parse("2026-11-05T00:00:00Z");
    UUID roomId = UUID.randomUUID();
    RoomMatchIngressTracker tracker = new RoomMatchIngressTracker();
    RoomMatchIngress ingress = new RoomMatchIngress(Clock.fixed(now, ZoneOffset.UTC), tracker);
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/rooms/" + roomId + "/guess");
    headers.setLeaveMutable(true);
    Message<?> result =
        ingress.preSend(
            MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
            mock(MessageChannel.class));

    assertThat(tracker.hasPendingBefore(roomId, now.plusMillis(1))).isTrue();
    ingress.afterMessageHandled(result, mock(MessageChannel.class), message -> {}, null);
    assertThat(tracker.hasPendingBefore(roomId, now.plusMillis(1))).isFalse();
  }
}
