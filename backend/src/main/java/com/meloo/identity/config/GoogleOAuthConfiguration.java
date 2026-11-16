package com.meloo.identity.config;

import com.meloo.identity.oauth.OAuthCallbackFilter;
import com.meloo.identity.oauth.OAuthLoginSupport;
import com.meloo.identity.oauth.OAuthStateRepository;
import com.meloo.identity.service.OAuthAccountService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  GoogleOAuthProperties.class,
  FacebookOAuthProperties.class,
  AdminEmailsProperties.class
})
public class GoogleOAuthConfiguration {
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnExpression(
      "${meloo.identity.oauth.enabled:false} || ${meloo.identity.facebook.enabled:false}")
  static class Enabled {
    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(
        GoogleOAuthProperties p, FacebookOAuthProperties facebook) {
      var registrations = new java.util.ArrayList<ClientRegistration>();
      if (p.enabled())
        registrations.add(
            ClientRegistration.withRegistrationId("google")
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/api/v1/auth/oauth2/{registrationId}/callback")
                .scope("email", "profile")
                .authorizationUri(p.authorizationUri())
                .tokenUri(p.tokenUri())
                .userInfoUri(p.userInfoUri())
                .userNameAttributeName("sub")
                .clientName("Google")
                .clientSettings(
                    ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build());
      if (facebook.enabled()) registrations.add(facebook.registration());
      return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    OAuthStateRepository oauthStates(IdentityProperties p, ObjectMapper mapper, Clock clock) {
      return new OAuthStateRepository(p, mapper, clock);
    }

    @Bean
    OAuthLoginSupport oauthSupport(
        ClientRegistrationRepository clients,
        OAuthAccountService accounts,
        IdentityProperties p,
        GoogleOAuthProperties oauth,
        OAuthStateRepository states) {
      return new OAuthLoginSupport(clients, accounts, p, oauth, states);
    }

    @Bean
    RestClientAuthorizationCodeTokenResponseClient oauthTokenClient() {
      var client = new RestClientAuthorizationCodeTokenResponseClient();
      client.setRestClient(
          RestClient.builder()
              .requestFactory(requestFactory())
              .configureMessageConverters(
                  c -> {
                    c.addCustomConverter(
                        new org.springframework.http.converter.FormHttpMessageConverter());
                    c.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                  })
              .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
              .build());
      return client;
    }

    @Bean
    DefaultOAuth2UserService googleUserService() {
      var service = new DefaultOAuth2UserService();
      service.setRestOperations(new org.springframework.web.client.RestTemplate(requestFactory()));
      return service;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(5));
      factory.setReadTimeout(Duration.ofSeconds(5));
      return factory;
    }

    @Bean
    FilterRegistrationBean<OAuthCallbackFilter> oauthCallbackFilter(
        OAuthLoginSupport support, OAuthStateRepository states, ObjectMapper mapper) {
      var registration =
          new FilterRegistrationBean<>(new OAuthCallbackFilter(support, states, mapper));
      registration.addUrlPatterns("/api/v1/auth/oauth2/*");
      registration.setOrder(
          -101); // Wrap Spring Security (-100), including resolver validation errors.
      return registration;
    }
  }
}
