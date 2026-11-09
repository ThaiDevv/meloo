package com.meloo.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.identity.config.IdentityProperties;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.json.JsonMapper;

class OAuthStateRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
  private final IdentityProperties properties =
      new IdentityProperties(
          new IdentityProperties.JwtProperties(
              "test-state-secret-with-at-least-32-bytes", null, null, null),
          null,
          null,
          null);

  @Test
  void encryptedStateRoundTripsVerifierAndUserBindingAndRemovesCookie() {
    var states = states(NOW);
    var start = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    states.saveAuthorizationRequest(authorization(), start, response);
    var cookie = response.getCookie(OAuthStateRepository.COOKIE);
    assertThat(cookie.getValue()).doesNotContain("verifier", "userId");
    var callback = new MockHttpServletRequest();
    callback.setCookies(cookie);
    callback.setParameter("state", "state-value");
    var loaded = states.removeAuthorizationRequest(callback, response);
    assertThat(loaded).isNotNull();
    assertThat((String) loaded.getAttribute("code_verifier")).isEqualTo("verifier");
    assertThat((String) loaded.getAttribute("userId")).isEqualTo("bound-user");
    assertThat(callback.getAttribute(OAuthStateRepository.ATTRIBUTE)).isEqualTo(loaded);
    assertThat(response.getHeaders("Set-Cookie").getLast()).contains("Max-Age=0");
  }

  @Test
  void expiredMissingAndModifiedStateAreRejected() {
    var response = new MockHttpServletResponse();
    states(NOW).saveAuthorizationRequest(authorization(), new MockHttpServletRequest(), response);
    Cookie cookie = response.getCookie(OAuthStateRepository.COOKIE);
    var callback = new MockHttpServletRequest();
    callback.setCookies(cookie);
    callback.setParameter("state", "state-value");
    assertThat(states(NOW.plusSeconds(300)).loadAuthorizationRequest(callback)).isNull();
    callback.setParameter("state", "wrong");
    assertThat(states(NOW).loadAuthorizationRequest(callback)).isNull();
    callback.setParameter("state", "state-value");
    callback.setCookies(new Cookie(OAuthStateRepository.COOKIE, "a" + cookie.getValue()));
    assertThat(states(NOW).loadAuthorizationRequest(callback)).isNull();
    assertThat(states(NOW).loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
  }

  private OAuthStateRepository states(Instant now) {
    return new OAuthStateRepository(
        properties, JsonMapper.builder().build(), Clock.fixed(now, ZoneOffset.UTC));
  }

  private OAuth2AuthorizationRequest authorization() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://accounts.example/authorize")
        .clientId("client")
        .redirectUri("https://meloo.example/api/v1/auth/oauth2/google/callback")
        .state("state-value")
        .attributes(
            a -> {
              a.put("code_verifier", "verifier");
              a.put("userId", "bound-user");
            })
        .additionalParameters(Map.of("code_challenge", "challenge"))
        .build();
  }
}
