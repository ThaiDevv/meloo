package com.meloo.identity.oauth;

import com.meloo.generated.model.OAuthLinkRequestDto;
import com.meloo.identity.service.GuestAuthService;
import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "meloo.identity.oauth.enabled", havingValue = "true")
public class OAuthLinkService {
  private final OAuthStateRepository states;
  private final OAuthLoginSupport support;
  private final ClientRegistrationRepository clients;
  private final RestClientAuthorizationCodeTokenResponseClient tokenClient;
  private final DefaultOAuth2UserService userService;

  public OAuthLinkService(
      OAuthStateRepository states,
      OAuthLoginSupport support,
      ClientRegistrationRepository clients,
      RestClientAuthorizationCodeTokenResponseClient tokenClient,
      DefaultOAuth2UserService userService) {
    this.states = states;
    this.support = support;
    this.clients = clients;
    this.tokenClient = tokenClient;
    this.userService = userService;
  }

  public GuestAuthService.AuthResult link(
      OAuthLinkRequestDto body,
      UUID caller,
      HttpServletRequest request,
      HttpServletResponse response) {
    request.setAttribute("submittedOAuthState", body.getState());
    var authorization = states.removeAuthorizationRequest(request, response);
    if (authorization == null
        || !"link".equals(authorization.getAttribute("mode"))
        || !caller.toString().equals(authorization.getAttribute("userId"))
        || (body.getRedirectUri() != null
            && !body.getRedirectUri().toString().equals(authorization.getRedirectUri()))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "Invalid OAuth state");
    }
    try {
      var client = clients.findByRegistrationId("google");
      var callback =
          OAuth2AuthorizationResponse.success(body.getCode())
              .state(body.getState())
              .redirectUri(authorization.getRedirectUri())
              .build();
      var token =
          tokenClient.getTokenResponse(
              new OAuth2AuthorizationCodeGrantRequest(
                  client, new OAuth2AuthorizationExchange(authorization, callback)));
      var user =
          userService.loadUser(
              new OAuth2UserRequest(
                  client, token.getAccessToken(), token.getAdditionalParameters()));
      var result = support.finish(user, authorization);
      support.refreshCookie(response, result);
      return result;
    } catch (OAuth2AuthenticationException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "OAuth exchange failed");
    }
  }
}
