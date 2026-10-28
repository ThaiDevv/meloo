package com.meloo.shared.config;

import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import com.meloo.shared.api.ModuleSecurityRules.StompFrameRule;
import com.meloo.shared.api.ModuleSecurityRules.StompRule;
import com.meloo.shared.web.ProblemAccessDeniedHandler;
import com.meloo.shared.web.ProblemAuthenticationEntryPoint;
import com.meloo.shared.web.ProblemCorsProcessor;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Order(Ordered.LOWEST_PRECEDENCE)
public class SecurityConfig implements WebSocketMessageBrokerConfigurer {

  private final List<ModuleSecurityRules> moduleRules;
  private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
  private final ProblemAccessDeniedHandler accessDeniedHandler;

  @Autowired
  public SecurityConfig(
      List<ModuleSecurityRules> moduleRules,
      ProblemAuthenticationEntryPoint authenticationEntryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler) {
    this.moduleRules = List.copyOf(moduleRules);
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, List<ModuleSecurityRules> moduleRules)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            requests -> {
              requests.requestMatchers("/actuator/health/**").permitAll();
              requests.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll();
              moduleRules.stream()
                  .flatMap(rules -> rules.httpRules().stream())
                  .forEach(rule -> applyHttpRule(requests, rule));
              requests.anyRequest().denyAll();
            });

    if (moduleRules.stream().anyMatch(ModuleSecurityRules::oauth2LoginEnabled)) {
      http.oauth2Login(
          oauth2 ->
              moduleRules.stream()
                  .filter(ModuleSecurityRules::oauth2LoginEnabled)
                  .forEach(rules -> rules.configureOAuth2Login(oauth2)));
    }
    if (moduleRules.stream().anyMatch(ModuleSecurityRules::oauth2ResourceServerEnabled)) {
      http.oauth2ResourceServer(
          resourceServer -> {
            moduleRules.stream()
                .filter(ModuleSecurityRules::oauth2ResourceServerEnabled)
                .forEach(rules -> rules.configureOAuth2ResourceServer(resourceServer));
            resourceServer.authenticationEntryPoint(authenticationEntryPoint);
            resourceServer.accessDeniedHandler(accessDeniedHandler);
          });
    }
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  CorsFilter corsFilter(
      CorsConfigurationSource corsConfigurationSource, ObjectMapper objectMapper) {
    CorsFilter filter = new CorsFilter(corsConfigurationSource);
    filter.setCorsProcessor(new ProblemCorsProcessor(objectMapper));
    return filter;
  }

  AuthorizationManager<Message<?>> messageAuthorizationManager(
      List<ModuleSecurityRules> moduleRules) {
    MessageMatcherDelegatingAuthorizationManager.Builder messages =
        MessageMatcherDelegatingAuthorizationManager.builder();
    moduleRules.stream()
        .flatMap(rules -> rules.stompFrameRules().stream())
        .forEach(rule -> applyStompFrameRule(messages, rule));
    moduleRules.stream()
        .flatMap(rules -> rules.stompRules().stream())
        .forEach(rule -> applyStompRule(messages, rule));
    // CONNECT is admitted so module interceptors can authenticate its JWT before destination use.
    messages.simpTypeMatchers(SimpMessageType.CONNECT).permitAll();
    messages
        .simpTypeMatchers(
            SimpMessageType.DISCONNECT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.HEARTBEAT)
        .permitAll();
    messages.anyMessage().denyAll();
    return messages.build();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    moduleRules.forEach(rules -> rules.configureStompInboundChannel(registration));
    registration.interceptors(
        new SecurityContextChannelInterceptor(),
        new AuthorizationChannelInterceptor(messageAuthorizationManager(moduleRules)));
  }

  private static void applyHttpRule(
      org.springframework.security.config.annotation.web.configurers
                      .AuthorizeHttpRequestsConfigurer<
                  HttpSecurity>
              .AuthorizationManagerRequestMatcherRegistry
          requests,
      HttpRule rule) {
    String[] patterns = rule.patterns().toArray(String[]::new);
    HttpMethod method = rule.method();
    switch (rule.access()) {
      case PERMIT_ALL -> {
        if (method == null) requests.requestMatchers(patterns).permitAll();
        else requests.requestMatchers(method, patterns).permitAll();
      }
      case AUTHENTICATED -> {
        if (method == null) requests.requestMatchers(patterns).authenticated();
        else requests.requestMatchers(method, patterns).authenticated();
      }
      case AUTHORITY -> {
        if (method == null) requests.requestMatchers(patterns).hasAuthority(rule.authority());
        else requests.requestMatchers(method, patterns).hasAuthority(rule.authority());
      }
    }
  }

  private static void applyStompRule(
      MessageMatcherDelegatingAuthorizationManager.Builder messages, StompRule rule) {
    String[] patterns = rule.patterns().toArray(String[]::new);
    switch (rule.destinationType()) {
      case SEND -> applyStompSendRule(messages, rule, patterns);
      case SUBSCRIBE -> applyStompSubscribeRule(messages, rule, patterns);
    }
  }

  private static void applyStompFrameRule(
      MessageMatcherDelegatingAuthorizationManager.Builder messages, StompFrameRule rule) {
    SimpMessageType messageType = SimpMessageType.valueOf(rule.frameType().name());
    switch (rule.access()) {
      case PERMIT_ALL -> messages.simpTypeMatchers(messageType).permitAll();
      case AUTHENTICATED -> messages.simpTypeMatchers(messageType).authenticated();
      case AUTHORITY -> messages.simpTypeMatchers(messageType).hasAuthority(rule.authority());
    }
  }

  private static void applyStompSendRule(
      MessageMatcherDelegatingAuthorizationManager.Builder messages,
      StompRule rule,
      String[] patterns) {
    switch (rule.access()) {
      case PERMIT_ALL -> messages.simpMessageDestMatchers(patterns).permitAll();
      case AUTHENTICATED -> messages.simpMessageDestMatchers(patterns).authenticated();
      case AUTHORITY -> messages.simpMessageDestMatchers(patterns).hasAuthority(rule.authority());
    }
  }

  private static void applyStompSubscribeRule(
      MessageMatcherDelegatingAuthorizationManager.Builder messages,
      StompRule rule,
      String[] patterns) {
    switch (rule.access()) {
      case PERMIT_ALL -> messages.simpSubscribeDestMatchers(patterns).permitAll();
      case AUTHENTICATED -> messages.simpSubscribeDestMatchers(patterns).authenticated();
      case AUTHORITY -> messages.simpSubscribeDestMatchers(patterns).hasAuthority(rule.authority());
    }
  }
}
