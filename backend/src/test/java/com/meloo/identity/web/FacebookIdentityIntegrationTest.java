package com.meloo.identity.web;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.meloo.IntegrationTest;
import com.meloo.identity.oauth.OAuthStateRepository;
import com.meloo.identity.persistence.*;
import com.meloo.identity.service.JwtTokenService;
import com.meloo.identity.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
class FacebookIdentityIntegrationTest extends IntegrationTest {
  private static final String BASE = "/api/v1/auth/oauth2/facebook";
  private static final String SECRET = "facebook-mock-secret";
  private static final WireMockServer META = new WireMockServer(0);

  static {
    META.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("MELOO_JWT_SECRET", () -> "test-meloo-facebook-key-at-least-32-bytes");
    r.add("meloo.identity.facebook.enabled", () -> true);
    r.add("meloo.identity.facebook.client-id", () -> "facebook-test-client");
    r.add("meloo.identity.facebook.client-secret", () -> SECRET);
    r.add("meloo.identity.facebook.authorization-uri", () -> META.baseUrl() + "/authorize");
    r.add("meloo.identity.facebook.token-uri", () -> META.baseUrl() + "/token");
    r.add("meloo.identity.facebook.user-info-uri", () -> META.baseUrl() + "/userinfo");
    r.add("meloo.admin.emails", () -> "facebook-bootstrap@example.com");
  }

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired AuthIdentityRepository identities;
  @Autowired RefreshTokenService refresh;
  @Autowired JwtTokenService jwt;

  @BeforeEach
  void reset() {
    META.resetAll();
  }

  @Test
  void facebookOnlyLoginUsesStatePkceAndScopesWithoutTrustingEmailOrGrantingAdmin()
      throws Exception {
    String subject = profile("facebook-bootstrap@example.com");
    Flow flow = start(null, "login");
    var auth = UriComponentsBuilder.fromUriString(flow.location()).build().getQueryParams();
    assertThat(auth.getFirst("scope")).contains("email", "public_profile");
    assertThat(auth.getFirst("code_challenge_method")).isEqualTo("S256");
    assertThat(auth.getFirst("code_challenge")).isNotBlank();
    assertThat(flow.cookie().isHttpOnly()).isTrue();
    assertThat(flow.cookie().getSecure()).isTrue();
    MvcResult result =
        callback(flow, "facebook")
            .andExpect(redirectedUrl("http://localhost:5173/auth/callback"))
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    var identity =
        identities.findByProviderAndProviderSubject(AuthProvider.FACEBOOK, subject).orElseThrow();
    AppUser user = users.findById(identity.getUserId()).orElseThrow();
    assertThat(user.getEmail()).isNull();
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.isProfileCompleted()).isFalse();
    mvc.perform(
            post("/api/v1/auth/refresh").cookie(result.getResponse().getCookie("meloo_refresh")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(user.getId().toString()));
    callback(start(null, "login"), "facebook").andExpect(cookie().exists("meloo_refresh"));
    assertThat(identities.findAllByUserId(user.getId())).hasSize(1);
    assertThat(META.findAll(postRequestedFor(urlEqualTo("/token"))).getFirst().getBodyAsString())
        .contains("code_verifier=", "client_id=facebook-test-client");
  }

  @Test
  void matchingUnverifiedEmailCannotTakeOverAnExistingAccount() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    AppUser existing = account(email, UserKind.REGISTERED);
    String subject = profile(email);
    callback(start(null, "login"), "facebook")
        .andExpect(
            redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_EMAIL_UNVERIFIED"))
        .andExpect(cookie().doesNotExist("meloo_refresh"));
    assertThat(identities.findByProviderAndProviderSubject(AuthProvider.FACEBOOK, subject))
        .isEmpty();
    assertThat(users.findById(existing.getId()).orElseThrow().getEmail()).isEqualTo(email);
  }

  @Test
  void registeredCookieAndMatchingBearerCanExplicitlyLinkFacebook() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    AppUser existing = account(email, UserKind.REGISTERED);
    String subject = profile(email);
    Flow flow =
        start(
            new Cookie("meloo_refresh", refresh.createRefreshToken(existing.getId()).rawToken()),
            "link");
    callback(flow, "facebook").andExpect(status().isFound());
    META.verify(0, postRequestedFor(urlEqualTo("/token")));
    mvc.perform(
            post(BASE + "/link")
                .cookie(flow.cookie())
                .header("Authorization", "Bearer " + jwt.generateAccessToken(existing).token())
                .contentType("application/json")
                .content("{\"code\":\"mock-code\",\"state\":\"" + flow.state() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(existing.getId().toString()));
    assertThat(
            identities
                .findByProviderAndProviderSubject(AuthProvider.FACEBOOK, subject)
                .orElseThrow()
                .getUserId())
        .isEqualTo(existing.getId());
  }

  @Test
  void providerSwapAndMissingStateNeverExchangeCode() throws Exception {
    profile(UUID.randomUUID() + "@example.com");
    callback(start(null, "login"), "google")
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"));
    mvc.perform(get(BASE + "/callback").param("code", "mock-code").param("state", "unknown"))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"));
    META.verify(0, postRequestedFor(urlEqualTo("/token")));
  }

  @Test
  void signedJsonAndFormCallbacksDeleteTheLinkedAccountAndTolerateRetry() throws Exception {
    AppUser existing = account(UUID.randomUUID() + "@example.com", UserKind.REGISTERED);
    String subject = UUID.randomUUID().toString();
    identities.saveAndFlush(
        AuthIdentity.builder()
            .id(UUID.randomUUID())
            .provider(AuthProvider.FACEBOOK)
            .providerSubject(subject)
            .userId(existing.getId())
            .createdAt(java.time.Instant.now())
            .build());
    String signed = signed(subject, "HMAC-SHA256");
    mvc.perform(
            post(BASE + "/data-deletion")
                .contentType("application/json")
                .content("{\"signed_request\":\"" + signed + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmation_code").isNotEmpty())
        .andExpect(
            jsonPath("$.url")
                .value(
                    org.hamcrest.Matchers.startsWith(
                        "http://localhost:5173/privacy?deletion_code=")));
    assertThat(users.findById(existing.getId()).orElseThrow().getEmail()).isNull();
    assertThat(identities.findAllByUserId(existing.getId())).isEmpty();
    mvc.perform(
            post(BASE + "/data-deletion")
                .contentType("application/x-www-form-urlencoded")
                .content("signed_request=" + signed))
        .andExpect(status().isOk());
  }

  @Test
  void badSignaturesAlgorithmsAndMalformedPayloadsCannotDelete() throws Exception {
    AppUser existing = account(UUID.randomUUID() + "@example.com", UserKind.REGISTERED);
    String subject = UUID.randomUUID().toString();
    identities.saveAndFlush(
        AuthIdentity.builder()
            .id(UUID.randomUUID())
            .provider(AuthProvider.FACEBOOK)
            .providerSubject(subject)
            .userId(existing.getId())
            .createdAt(java.time.Instant.now())
            .build());
    for (String signed :
        List.of(
            "invalid",
            "x." + signed(subject, "HMAC-SHA256").split("\\.")[1],
            signed(subject, "HMAC-SHA1"))) {
      mvc.perform(
              post(BASE + "/data-deletion")
                  .contentType("application/json")
                  .content("{\"signed_request\":\"" + signed + "\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    assertThat(users.findById(existing.getId()).orElseThrow().getEmail())
        .isEqualTo(existing.getEmail());
  }

  private AppUser account(String email, UserKind kind) {
    return users.saveAndFlush(
        AppUser.builder()
            .id(UUID.randomUUID())
            .kind(kind)
            .nickname("PlayerTest")
            .email(email)
            .avatarId("piano")
            .build());
  }

  private String profile(String email) {
    String subject = UUID.randomUUID().toString();
    META.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
            .willReturn(
                okJson(
                    "{\"access_token\":\"mock-access\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    // email_verified is deliberately malicious: Facebook must never be allowed to assert it.
    META.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/userinfo"))
            .willReturn(
                okJson(
                    "{\"id\":\""
                        + subject
                        + "\",\"name\":\"Player\",\"email\":\""
                        + email
                        + "\",\"email_verified\":true}")));
    return subject;
  }

  private Flow start(Cookie refreshCookie, String mode) throws Exception {
    var request = get(BASE).param("mode", mode);
    if (refreshCookie != null) request.cookie(refreshCookie);
    var response = mvc.perform(request).andExpect(status().isFound()).andReturn().getResponse();
    String location = response.getRedirectedUrl();
    String state =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    return new Flow(
        java.net.URLDecoder.decode(state, StandardCharsets.UTF_8),
        response.getCookie(OAuthStateRepository.COOKIE),
        location);
  }

  private ResultActions callback(Flow flow, String provider) throws Exception {
    return mvc.perform(
        get("/api/v1/auth/oauth2/" + provider + "/callback")
            .servletPath("/api/v1/auth/oauth2/" + provider + "/callback")
            .cookie(flow.cookie())
            .param("state", flow.state())
            .param("code", "mock-code"));
  }

  private static String signed(String subject, String algorithm) throws Exception {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"algorithm\":\"" + algorithm + "\",\"user_id\":\"" + subject + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)))
        + "."
        + payload;
  }

  private record Flow(String state, Cookie cookie, String location) {}
}
