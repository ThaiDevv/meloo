package com.meloo.identity.config;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.stereotype.Component;

@Component
public class IdentitySecurityRules implements ModuleSecurityRules {

  @Override
  public List<HttpRule> httpRules() {
    return List.of(
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/guest"),
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/refresh"),
        HttpRule.permitAll(HttpMethod.POST, "/api/v1/auth/logout"),
        HttpRule.authenticated(HttpMethod.GET, "/api/v1/me"));
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
