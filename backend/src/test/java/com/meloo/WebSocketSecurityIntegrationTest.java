package com.meloo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Import(WebSocketSecurityIntegrationTest.TestMessageBrokerConfiguration.class)
class WebSocketSecurityIntegrationTest extends IntegrationTest {

  @Autowired private AbstractMessageChannel clientInboundChannel;

  @Autowired private SimpAnnotationMethodMessageHandler messageHandler;

  @Test
  void installsAuthorizationInterceptorOnClientInboundChannel() {
    assertThat(messageHandler).isNotNull();
    assertThat(clientInboundChannel.getInterceptors())
        .anyMatch(AuthorizationChannelInterceptor.class::isInstance);
  }

  @Test
  void acceptsConnectForModuleAuthenticationAndDeniesUnmatchedDestinationFrames() {
    var connectHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
    var connectMessage =
        MessageBuilder.createMessage(new byte[0], connectHeaders.getMessageHeaders());

    assertThatNoException().isThrownBy(() -> clientInboundChannel.send(connectMessage));

    var messageHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    messageHeaders.setDestination("/app/unconfigured");
    var unmatchedMessage =
        MessageBuilder.createMessage(new byte[0], messageHeaders.getMessageHeaders());
    assertThatThrownBy(() -> clientInboundChannel.send(unmatchedMessage))
        .isInstanceOf(MessageDeliveryException.class)
        .hasRootCauseInstanceOf(AccessDeniedException.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSocketMessageBroker
  static class TestMessageBrokerConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
      registry.addEndpoint("/test-stomp");
    }
  }
}
