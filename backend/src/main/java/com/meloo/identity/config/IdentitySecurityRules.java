package com.meloo.identity.config;

import com.meloo.identity.oauth.OAuthLoginSupport;
import com.meloo.identity.oauth.OAuthStateRepository;
import com.meloo.identity.oauth.TransientAuthorizedClients;
import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class IdentitySecurityRules implements ModuleSecurityRules {
  private final ObjectProvider<OAuthLoginSupport> oauth;
  private final ObjectProvider<OAuthStateRepository> states;
  private final ObjectProvider<RestClientAuthorizationCodeTokenResponseClient> tokenClient;
  private final ObjectProvider<DefaultOAuth2UserService> userService;

  public IdentitySecurityRules(
      ObjectProvider<OAuthLoginSupport> oauth,
      ObjectProvider<OAuthStateRepository> states,
      ObjectProvider<RestClientAuthorizationCodeTokenResponseClient> tokenClient,
      ObjectProvider<DefaultOAuth2UserService> userService) {
    this.oauth = oauth;
    this.states = states;
    this.tokenClient = tokenClient;
    this.userService = userService;
  }

  @Override
  public boolean oauth2LoginEnabled() {
    return oauth.getIfAvailable() != null;
  }

  @Override
  public void configureOAuth2Login(OAuth2LoginConfigurer<HttpSecurity> login) {
    var support = oauth.getObject();
    login
        .authorizationEndpoint(
            a ->
                a.baseUri(OAuthLoginSupport.BASE)
                    .authorizationRequestResolver(support)
                    .authorizationRequestRepository(states.getObject()))
        .redirectionEndpoint(r -> r.baseUri("/api/v1/auth/oauth2/*/callback"))
        .tokenEndpoint(t -> t.accessTokenResponseClient(tokenClient.getObject()))
        .userInfoEndpoint(u -> u.userService(userService.getObject()))
        // The shared SecurityContextConfigurer replaces the repository during configure;
        // set the OAuth filter repository after configuration to avoid a second login session.
        .withObjectPostProcessor(
            new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
              @Override
              public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                filter.setSecurityContextRepository(new NullSecurityContextRepository());
                return filter;
              }
            })
        .authorizedClientRepository(new TransientAuthorizedClients())
        .successHandler(support::success)
        .failureHandler(
            (request, response, error) -> support.failure(response, "OAUTH_STATE_INVALID"));
  }

  @Override
  public List<HttpRule> httpRules() {
    return List.of(
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/guest"),
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/refresh"),
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/logout"),
        HttpRule.authenticated(HttpMethod.GET, "/api/v1/me"),
        HttpRule.permitAll(
            HttpMethod.GET, "/api/v1/auth/oauth2/google", "/api/v1/auth/oauth2/google/callback"),
        HttpRule.authenticated(HttpMethod.POST, "/api/v1/auth/oauth2/google/link"));
  }

  @Override
  public boolean oauth2ResourceServerEnabled() {
    return true;
  }

  @Override
  public void configureOAuth2ResourceServer(
      OAuth2ResourceServerConfigurer<HttpSecurity> resourceServer) {
    resourceServer.jwt(Customizer.withDefaults());
  }
}
