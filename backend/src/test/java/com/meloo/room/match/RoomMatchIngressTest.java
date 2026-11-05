package com.meloo.room.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;

class RoomMatchIngressTest {
  @Test
  void stampsTrustedReceiptBeforeDispatchAndOverridesClientHeader() {
    Instant now = Instant.parse("2026-11-05T00:00:00Z");
    RoomMatchIngress ingress = new RoomMatchIngress(Clock.fixed(now, ZoneOffset.UTC));
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/rooms/00000000-0000-0000-0000-000000000000/guess");
    headers.setHeader(RoomMatchIngress.RECEIVED_AT, now.minusSeconds(60));
    headers.setNativeHeader(RoomMatchIngress.RECEIVED_AT, now.minusSeconds(60).toString());
    Message<?> result =
        ingress.preSend(
            MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
            mock(MessageChannel.class));
    assertThat(result.getHeaders().get(RoomMatchIngress.RECEIVED_AT)).isEqualTo(now);
    assertThat(
            org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(
                result, StompHeaderAccessor.class))
        .isNotNull();
  }
}
