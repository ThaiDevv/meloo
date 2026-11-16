package com.meloo.identity.oauth;

import com.meloo.identity.config.GoogleOAuthProperties;
import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.AuthProvider;
import com.meloo.identity.service.GuestAuthService;
import com.meloo.identity.service.OAuthAccountService;
import com.meloo.identity.service.RefreshTokenService;
import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.util.UriComponentsBuilder;

public final class OAuthLoginSupport implements OAuth2AuthorizationRequestResolver {
  public static final String BASE = "/api/v1/auth/oauth2";
  private final DefaultOAuth2AuthorizationRequestResolver delegate;
  private final OAuthAccountService accounts;
  private final IdentityProperties identity;
  private final GoogleOAuthProperties properties;
  private final OAuthStateRepository states;

  public OAuthLoginSupport(
      ClientRegistrationRepository clients,
      OAuthAccountService accounts,
      IdentityProperties identity,
      GoogleOAuthProperties properties,
      OAuthStateRepository states) {
    delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, BASE);
    this.accounts = accounts;
    this.identity = identity;
    this.properties = properties;
    this.states = states;
  }

  @Override
  @Nullable public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return bind(delegate.resolve(request), request);
  }

  @Override
  @Nullable public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
    return bind(delegate.resolve(request, registrationId), request);
  }

  @Nullable private OAuth2AuthorizationRequest bind(
      @Nullable OAuth2AuthorizationRequest authorization, HttpServletRequest request) {
    if (authorization == null) {
      return null;
    }
    String mode = request.getParameter("mode");
    if (mode != null && !mode.equals("login") && !mode.equals("link")) {
      throw invalid();
    }
    String returnTo = request.getParameter("returnTo");
    if (returnTo != null
        && (returnTo.length() > 200
            || !returnTo.startsWith("/")
            || returnTo.startsWith("//")
            || returnTo.contains("\\")
            || returnTo.chars().anyMatch(c -> c < 32)
            || returnTo.contains("%"))) {
      throw invalid();
    }
    String rawToken = OAuthStateRepository.cookie(request, identity.cookie().name());
    var userId = accounts.initiatingUser(rawToken, "link".equals(mode));
    if ("link".equals(mode) && userId.isEmpty()) {
      throw invalid();
    }
    return OAuth2AuthorizationRequest.from(authorization)
        .attributes(
            a -> {
              a.put("mode", "link".equals(mode) ? "link" : "login");
              if (returnTo != null) {
                a.put("returnTo", returnTo);
              }
              userId.ifPresent(
                  id -> {
                    a.put("userId", id.toString());
                    a.put("tokenHash", RefreshTokenService.hashToken(rawToken));
                  });
            })
        .build();
  }

  public void success(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    try {
      OAuth2AuthorizationRequest authorization =
          (OAuth2AuthorizationRequest) request.getAttribute(OAuthStateRepository.ATTRIBUTE);
      OAuth2User user = ((OAuth2AuthenticationToken) authentication).getPrincipal();
      var result = finish(user, authorization);
      refreshCookie(response, result);
      redirect(response, authorization, null);
    } catch (ApiException e) {
      failure(response, e.code());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  public GuestAuthService.AuthResult finish(
      OAuth2User user, OAuth2AuthorizationRequest authorization) {
    String bound = authorization.getAttribute("userId");
    boolean facebook = "facebook".equals(authorization.getAttribute("registration_id"));
    return accounts.login(
        facebook ? AuthProvider.FACEBOOK : AuthProvider.GOOGLE,
        user.getAttribute(facebook ? "id" : "sub"),
        user.getAttribute("email"),
        !facebook && Boolean.TRUE.equals(user.getAttribute("email_verified")),
        bound == null ? null : UUID.fromString(bound),
        authorization.getAttribute("tokenHash"),
        "link".equals(authorization.getAttribute("mode")));
  }

  public void refreshCookie(HttpServletResponse response, GuestAuthService.AuthResult result) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(identity.cookie().name(), result.rawRefreshToken())
            .httpOnly(true)
            .secure(identity.cookie().secure())
            .sameSite(identity.cookie().sameSite())
            .path(identity.cookie().path())
            .maxAge(identity.jwt().refreshTokenTtl())
            .build()
            .toString());
  }

  public void failure(HttpServletResponse response, String code) throws IOException {
    states.clear(response);
    // Provider error descriptions are untrusted and may include sensitive details.
    redirect(
        response,
        null,
        java.util.Set.of(
                    "ACCOUNT_LOCKED", "OAUTH_EMAIL_UNVERIFIED", "OAUTH_IDENTITY_ALREADY_LINKED")
                .contains(code)
            ? code
            : "OAUTH_STATE_INVALID");
  }

  public void redirect(
      HttpServletResponse response,
      @Nullable OAuth2AuthorizationRequest authorization,
      @Nullable String error)
      throws IOException {
    var uri = UriComponentsBuilder.fromUri(properties.spaOrigin()).replacePath("/auth/callback");
    if (error != null) {
      uri.queryParam("error", error);
    }
    if (authorization != null && authorization.getAttribute("returnTo") != null) {
      uri.queryParam("returnTo", (String) authorization.getAttribute("returnTo"));
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.sendRedirect(uri.build().encode().toUriString());
  }

  public String linkRedirect(String code, String state, OAuth2AuthorizationRequest authorization) {
    var uri =
        UriComponentsBuilder.fromUri(properties.spaOrigin())
            .replacePath("/auth/callback")
            .queryParam("mode", "link")
            .queryParam("provider", (String) authorization.getAttribute("registration_id"))
            .queryParam("code", code)
            .queryParam("state", state);
    if (authorization.getAttribute("returnTo") != null) {
      uri.queryParam("returnTo", (String) authorization.getAttribute("returnTo"));
    }
    return uri.build().encode().toUriString();
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_INVALID", "Invalid OAuth flow");
  }
}
