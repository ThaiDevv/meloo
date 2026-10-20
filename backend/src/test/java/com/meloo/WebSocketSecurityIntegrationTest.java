package com.meloo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.support.AbstractMessageChannel;
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

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSocketMessageBroker
  static class TestMessageBrokerConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
      registry.addEndpoint("/test-stomp");
    }
  }
}
