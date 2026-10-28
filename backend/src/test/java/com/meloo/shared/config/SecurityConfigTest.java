package com.meloo.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.StompDestinationType;
import com.meloo.shared.api.ModuleSecurityRules.StompFrameRule;
import com.meloo.shared.api.ModuleSecurityRules.StompFrameType;
import com.meloo.shared.api.ModuleSecurityRules.StompRule;
import com.meloo.shared.web.ProblemAccessDeniedHandler;
import com.meloo.shared.web.ProblemAuthenticationEntryPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

class SecurityConfigTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ProblemAuthenticationEntryPoint authenticationEntryPoint =
      new ProblemAuthenticationEntryPoint(objectMapper);
  private final ProblemAccessDeniedHandler accessDeniedHandler =
      new ProblemAccessDeniedHandler(objectMapper);

  private SecurityConfig createConfig(List<ModuleSecurityRules> rules) {
    return new SecurityConfig(rules, authenticationEntryPoint, accessDeniedHandler);
  }

  @Test
  void corsUsesConfiguredAllowedOrigins() {
    SecurityConfig securityConfig = createConfig(List.of());
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
            return List.of(
                StompRule.permitAll(StompDestinationType.SEND, "/app/public"),
                StompRule.hasAuthority(StompDestinationType.SUBSCRIBE, "ROOM_ADMIN", "/app/public"),
                StompRule.permitAll(StompDestinationType.SUBSCRIBE, "/topic/public"));
          }

          @Override
          public List<StompFrameRule> stompFrameRules() {
            return List.of(StompFrameRule.permitAll(StompFrameType.CONNECT));
          }
        };
    AuthorizationManager<Message<?>> manager =
        createConfig(List.of()).messageAuthorizationManager(List.of(publicTopicRules));

    assertThat(
            manager.authorize(() -> null, stompMessage(SimpMessageType.SUBSCRIBE, "/topic/public")))
        .isNotNull()
        .extracting(result -> result.isGranted())
        .isEqualTo(true);
    assertThat(
            manager.authorize(
                () -> new TestingAuthenticationToken("user", "n/a", "ROLE_USER"),
                stompMessage(SimpMessageType.SUBSCRIBE, "/app/public")))
        .isNotNull()
        .extracting(result -> result.isGranted())
        .isEqualTo(false);
    assertThat(manager.authorize(() -> null, stompMessage(SimpMessageType.CONNECT, null)))
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
    if (destination != null) headers.setDestination(destination);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }
}
