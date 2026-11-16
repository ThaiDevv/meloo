package com.meloo.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@ConfigurationProperties("meloo.identity.facebook")
public record FacebookOAuthProperties(
    @DefaultValue("false") boolean enabled,
    String clientId,
    String clientSecret,
    @DefaultValue("https://www.facebook.com/v24.0/dialog/oauth") String authorizationUri,
    @DefaultValue("https://graph.facebook.com/v24.0/oauth/access_token") String tokenUri,
    @DefaultValue("https://graph.facebook.com/v24.0/me?fields=id,name,email") String userInfoUri) {
  public FacebookOAuthProperties {
    if (enabled
        && (clientId == null
            || clientId.isBlank()
            || clientSecret == null
            || clientSecret.isBlank())) {
      throw new IllegalArgumentException("Enabled Facebook OAuth requires client credentials");
    }
  }

  public ClientRegistration registration() {
    return ClientRegistration.withRegistrationId("facebook")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/v1/auth/oauth2/{registrationId}/callback")
        .scope("public_profile", "email")
        .authorizationUri(authorizationUri)
        .tokenUri(tokenUri)
        .userInfoUri(userInfoUri)
        .userNameAttributeName("id")
        .clientName("Facebook")
        .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
        .build();
  }
}
