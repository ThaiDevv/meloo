package com.meloo.identity.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meloo.identity.oauth")
public record GoogleOAuthProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:5173") URI spaOrigin,
    String clientId,
    String clientSecret,
    @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") String authorizationUri,
    @DefaultValue("https://oauth2.googleapis.com/token") String tokenUri,
    @DefaultValue("https://www.googleapis.com/oauth2/v3/userinfo") String userInfoUri) {
  public GoogleOAuthProperties {
    if (spaOrigin != null
        && (!java.util.Set.of("http", "https").contains(spaOrigin.getScheme())
            || spaOrigin.getHost() == null
            || spaOrigin.getRawQuery() != null
            || spaOrigin.getRawFragment() != null
            || spaOrigin.getRawUserInfo() != null
            || !(spaOrigin.getPath().isEmpty() || spaOrigin.getPath().equals("/")))) {
      throw new IllegalArgumentException("OAuth SPA origin must be an HTTP(S) origin");
    }
    if (enabled
        && (clientId == null
            || clientId.isBlank()
            || clientSecret == null
            || clientSecret.isBlank())) {
      throw new IllegalArgumentException("Enabled Google OAuth requires client credentials");
    }
  }
}
