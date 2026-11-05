package com.meloo.room.match;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Stamps receipt on the transport thread before the inbound executor or room queue waits. */
@Configuration(proxyBeanMethods = false)
public class RoomMatchIngress
    implements WebSocketMessageBrokerConfigurer, ChannelInterceptor, ExecutorChannelInterceptor {
  static final String RECEIVED_AT = "melooRoomReceivedAt";
  static final String TRACKER_TOKEN = "melooRoomIngressToken";
  private final Clock clock;
  private final RoomMatchIngressTracker tracker;

  public RoomMatchIngress(@Qualifier("clock") Clock clock, RoomMatchIngressTracker tracker) {
    this.clock = clock;
    this.tracker = tracker;
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
    if (headers.getCommand() == StompCommand.SEND && isGameplayCommand(destination)) {
      String[] segments = destination.split("/");
      UUID roomId;
      try {
        roomId = UUID.fromString(segments[3]);
      } catch (RuntimeException invalidRoomId) {
        return message;
      }
      RoomMatchIngressTracker.Receipt receipt = tracker.register(roomId, clock);
      Instant receivedAt = receipt.receivedAt();
      UUID token = receipt.token();
      if (headers.isMutable()) {
        headers.setHeader(RECEIVED_AT, receivedAt);
        headers.setHeader(TRACKER_TOKEN, token);
        return message;
      }
      StompHeaderAccessor copy = StompHeaderAccessor.wrap(message);
      copy.setHeader(RECEIVED_AT, receivedAt);
      copy.setHeader(TRACKER_TOKEN, token);
      copy.setLeaveMutable(true);
      return MessageBuilder.createMessage(message.getPayload(), copy.getMessageHeaders());
    }
    return message;
  }

  @Override
  public void afterSendCompletion(
      Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
    if (ex != null) release(message);
  }

  @Override
  public void afterMessageHandled(
      Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
    release(message);
  }

  private void release(Message<?> message) {
    Object token = message.getHeaders().get(TRACKER_TOKEN);
    if (token instanceof UUID ingressToken) tracker.dispatched(ingressToken);
  }

  private static boolean isGameplayCommand(String destination) {
    if (destination == null || !destination.startsWith("/app/rooms/")) return false;
    String[] segments = destination.split("/");
    return segments.length == 5
        && (segments[4].equals("guess")
            || segments[4].equals("listen-more")
            || segments[4].equals("give-up"));
  }
}
