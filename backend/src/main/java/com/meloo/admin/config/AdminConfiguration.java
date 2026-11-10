package com.meloo.admin.config;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
@EnableScheduling
public class AdminConfiguration implements ModuleSecurityRules {
  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.hasAuthority(null, "ROLE_ADMIN", "/api/v1/admin/**"));
  }

  @Override
  public boolean oauth2ResourceServerEnabled() {
    return true;
  }

  @Override
  public void configureOAuth2ResourceServer(OAuth2ResourceServerConfigurer<HttpSecurity> server) {
    var scopes = new JwtGrantedAuthoritiesConverter();
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var authorities = new ArrayList<GrantedAuthority>(scopes.convert(jwt));
          if ("ADMIN".equals(jwt.getClaimAsString("role")))
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
          return authorities;
        });
    server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter));
  }
}
