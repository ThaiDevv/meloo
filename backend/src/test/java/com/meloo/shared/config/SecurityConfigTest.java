package com.meloo.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.StompDestinationType;
import com.meloo.shared.api.ModuleSecurityRules.StompRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

  @Test
  void corsUsesConfiguredAllowedOrigins() {
    SecurityConfig securityConfig = new SecurityConfig();
    CorsConfigurationSource source =
        securityConfig.corsConfigurationSource(
            new CorsProperties(List.of("https://app.meloo.example")));

    CorsConfiguration configuration =
        source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/me"));

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.meloo.example");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }

  @Test
  void stompRulesAreAppliedAndUnmatchedMessagesAreDenied() {
    ModuleSecurityRules publicTopicRules =
        new ModuleSecurityRules() {
          @Override
          public List<StompRule> stompRules() {
            return List.of(StompRule.permitAll(StompDestinationType.SUBSCRIBE, "/topic/public"));
          }
        };
    AuthorizationManager<Message<?>> manager =
        new SecurityConfig().messageAuthorizationManager(List.of(publicTopicRules));

    assertThat(
            manager.authorize(() -> null, stompMessage(SimpMessageType.SUBSCRIBE, "/topic/public")))
        .isNotNull()
        .extracting(result -> result.isGranted())
        .isEqualTo(true);
    assertThat(manager.authorize(() -> null, stompMessage(SimpMessageType.MESSAGE, "/app/private")))
        .isNotNull()
        .extracting(result -> result.isGranted())
        .isEqualTo(false);
  }

  private static Message<byte[]> stompMessage(SimpMessageType type, String destination) {
    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
    headers.setDestination(destination);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }
}
