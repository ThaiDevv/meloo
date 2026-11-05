package com.meloo.room.match;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Stamps receipt on the transport thread before the inbound executor or room queue waits. */
@Configuration(proxyBeanMethods = false)
public class RoomMatchIngress implements WebSocketMessageBrokerConfigurer, ChannelInterceptor {
  static final String RECEIVED_AT = "melooRoomReceivedAt";
  private final Clock clock;

  public RoomMatchIngress(@Qualifier("clock") Clock clock) {
    this.clock = clock;
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(this);
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor headers =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (headers == null) return message;
    String destination = headers.getDestination();
    if (headers.getCommand() == StompCommand.SEND
        && destination != null
        && destination.startsWith("/app/rooms/")) {
      if (headers.isMutable()) {
        headers.setHeader(RECEIVED_AT, clock.instant());
        return message;
      }
      StompHeaderAccessor copy = StompHeaderAccessor.wrap(message);
      copy.setHeader(RECEIVED_AT, clock.instant());
      copy.setLeaveMutable(true);
      return MessageBuilder.createMessage(message.getPayload(), copy.getMessageHeaders());
    }
    return message;
  }
}
