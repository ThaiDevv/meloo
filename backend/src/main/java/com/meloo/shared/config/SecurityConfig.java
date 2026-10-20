package com.meloo.shared.config;

import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import com.meloo.shared.api.ModuleSecurityRules.StompRule;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, List<ModuleSecurityRules> moduleRules)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            requests -> {
              requests.requestMatchers("/actuator/health/**").permitAll();
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
  AuthorizationManager<Message<?>> messageAuthorizationManager(
      List<ModuleSecurityRules> moduleRules) {
    MessageMatcherDelegatingAuthorizationManager.Builder messages =
        MessageMatcherDelegatingAuthorizationManager.builder();
    moduleRules.stream()
        .flatMap(rules -> rules.stompRules().stream())
        .forEach(rule -> applyStompRule(messages, rule));
    messages.anyMessage().denyAll();
    return messages.build();
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

  private static void applyStompSendRule(
      MessageMatcherDelegatingAuthorizationManager.Builder messages,
      StompRule rule,
      String[] patterns) {
    switch (rule.access()) {
      case PERMIT_ALL -> messages.simpDestMatchers(patterns).permitAll();
      case AUTHENTICATED -> messages.simpDestMatchers(patterns).authenticated();
      case AUTHORITY -> messages.simpDestMatchers(patterns).hasAuthority(rule.authority());
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
