package com.meloo.shared.api;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

/**
 * Security declarations contributed by one application module. Overlapping HTTP or STOMP patterns
 * are unsupported; the first matching declaration wins. Annotate contributing beans with
 * {@code @Order} when declarations from multiple modules need a stable order.
 */
public interface ModuleSecurityRules {

  default List<HttpRule> httpRules() {
    return List.of();
  }

  default List<StompRule> stompRules() {
    return List.of();
  }

  default List<StompFrameRule> stompFrameRules() {
    return List.of();
  }

  default boolean oauth2LoginEnabled() {
    return false;
  }

  default void configureOAuth2Login(OAuth2LoginConfigurer<HttpSecurity> oauth2Login) {}

  default boolean oauth2ResourceServerEnabled() {
    return false;
  }

  default void configureOAuth2ResourceServer(
      OAuth2ResourceServerConfigurer<HttpSecurity> resourceServer) {}

  /**
   * Add module authentication interceptors before shared STOMP authorization interceptors.
   *
   * <p>This hook is the only supported place to register STOMP authentication interceptors: an
   * interceptor added from a separate {@code WebSocketMessageBrokerConfigurer} may be ordered after
   * the shared authorization interceptor and would never see an authenticated context in time.
   */
  default void configureStompInboundChannel(ChannelRegistration registration) {}

  enum Access {
    PERMIT_ALL,
    AUTHENTICATED,
    AUTHORITY
  }

  enum StompDestinationType {
    SEND,
    SUBSCRIBE
  }

  enum StompFrameType {
    CONNECT,
    DISCONNECT,
    UNSUBSCRIBE,
    HEARTBEAT
  }

  record StompFrameRule(StompFrameType frameType, Access access, @Nullable String authority) {

    public StompFrameRule {
      requireAuthorityWhenNeeded(access, authority);
    }

    public static StompFrameRule permitAll(StompFrameType frameType) {
      return new StompFrameRule(frameType, Access.PERMIT_ALL, null);
    }

    public static StompFrameRule authenticated(StompFrameType frameType) {
      return new StompFrameRule(frameType, Access.AUTHENTICATED, null);
    }

    public static StompFrameRule hasAuthority(StompFrameType frameType, String authority) {
      return new StompFrameRule(frameType, Access.AUTHORITY, authority);
    }
  }

  record HttpRule(
      @Nullable HttpMethod method,
      List<String> patterns,
      Access access,
      @Nullable String authority) {

    public HttpRule {
      patterns = List.copyOf(patterns);
      if (patterns.isEmpty()) {
        throw new IllegalArgumentException("At least one HTTP pattern is required");
      }
      requireAuthorityWhenNeeded(access, authority);
    }

    public static HttpRule permitAll(@Nullable HttpMethod method, String... patterns) {
      return new HttpRule(method, List.of(patterns), Access.PERMIT_ALL, null);
    }

    public static HttpRule authenticated(@Nullable HttpMethod method, String... patterns) {
      return new HttpRule(method, List.of(patterns), Access.AUTHENTICATED, null);
    }

    public static HttpRule hasAuthority(
        @Nullable HttpMethod method, String authority, String... patterns) {
      return new HttpRule(method, List.of(patterns), Access.AUTHORITY, authority);
    }
  }

  record StompRule(
      StompDestinationType destinationType,
      List<String> patterns,
      Access access,
      @Nullable String authority) {

    public StompRule {
      patterns = List.copyOf(patterns);
      if (patterns.isEmpty()) {
        throw new IllegalArgumentException("At least one STOMP pattern is required");
      }
      requireAuthorityWhenNeeded(access, authority);
    }

    public static StompRule permitAll(StompDestinationType type, String... patterns) {
      return new StompRule(type, List.of(patterns), Access.PERMIT_ALL, null);
    }

    public static StompRule authenticated(StompDestinationType type, String... patterns) {
      return new StompRule(type, List.of(patterns), Access.AUTHENTICATED, null);
    }

    public static StompRule hasAuthority(
        StompDestinationType type, String authority, String... patterns) {
      return new StompRule(type, List.of(patterns), Access.AUTHORITY, authority);
    }
  }

  private static void requireAuthorityWhenNeeded(Access access, @Nullable String authority) {
    if (access == Access.AUTHORITY && (authority == null || authority.isBlank())) {
      throw new IllegalArgumentException("Authority is required for AUTHORITY access");
    }
  }
}
