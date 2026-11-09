package com.meloo.identity.web;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.meloo.IntegrationTest;
import com.meloo.generated.model.GuestRequestDto;
import com.meloo.identity.api.UserAccounts;
import com.meloo.identity.events.GuestMerged;
import com.meloo.identity.events.UserRegistered;
import com.meloo.identity.oauth.OAuthStateRepository;
import com.meloo.identity.persistence.*;
import com.meloo.identity.service.GuestAuthService;
import com.meloo.identity.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
@RecordApplicationEvents
class GoogleOAuthIntegrationTest extends IntegrationTest {
  private static final String BASE = "/api/v1/auth/oauth2/google";
  private static final WireMockServer GOOGLE = new WireMockServer(0);

  static {
    GOOGLE.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("MELOO_JWT_SECRET", () -> "test-meloo-google-key-with-minimum-32-bytes-length");
    r.add("meloo.identity.oauth.enabled", () -> true);
    r.add("meloo.identity.oauth.client-id", () -> "google-test-client");
    r.add("meloo.identity.oauth.client-secret", () -> "mock-client-secret");
    r.add("meloo.identity.oauth.authorization-uri", () -> GOOGLE.baseUrl() + "/authorize");
    r.add("meloo.identity.oauth.token-uri", () -> GOOGLE.baseUrl() + "/token");
    r.add("meloo.identity.oauth.user-info-uri", () -> GOOGLE.baseUrl() + "/userinfo");
    r.add("meloo.admin.emails", () -> "bootstrap@example.com");
  }

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired AuthIdentityRepository identities;
  @Autowired RefreshTokenRepository tokens;
  @Autowired GuestAuthService guests;
  @Autowired UserAccounts accounts;
  @Autowired ApplicationEvents events;

  @BeforeEach
  void resetProvider() {
    GOOGLE.resetAll();
  }

  @Test
  void firstLoginUsesStatePkceAndSecureCookieThenRefreshesIntoSpa() throws Exception {
    String email = uniqueEmail();
    profile(email, true);
    Flow flow = start(null, "login");
    assertThat(flow.cookie().isHttpOnly()).isTrue();
    assertThat(flow.cookie().getSecure()).isTrue();
    assertThat(flow.cookie().getPath()).isEqualTo("/api/v1/auth");
    assertThat(flow.cookie().getMaxAge()).isEqualTo(300);
    assertThat(flow.cookie().getValue()).doesNotContain(email, "code_verifier");
    assertThat(flow.challenge()).isNotBlank();
    MvcResult result =
        callback(flow)
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("http://localhost:5173/auth/callback"))
            .andReturn();
    Cookie refresh = result.getResponse().getCookie("meloo_refresh");
    assertThat(refresh).isNotNull();
    assertThat(refresh.isHttpOnly()).isTrue();
    assertThat(refresh.getSecure()).isTrue();
    assertThat(refresh.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(result.getRequest().getSession(false)).isNull();
    assertThat(result.getResponse().getCookie(OAuthStateRepository.COOKIE).getMaxAge()).isZero();
    mvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(jsonPath("$.user.kind").value("REGISTERED"))
        .andExpect(jsonPath("$.user.role").value("USER"));
    AppUser user = users.findByEmail(email).orElseThrow();
    assertThat(
            events.stream(UserRegistered.class)
                .filter(e -> e.userId().equals(user.getId()))
                .count())
        .isEqualTo(1);
    var requests = GOOGLE.findAll(postRequestedFor(urlEqualTo("/token")));
    assertThat(requests).hasSize(1);
    String verifier = parseForm(requests.getFirst().getBodyAsString()).get("code_verifier");
    assertThat(verifier).isNotBlank();
    assertThat(
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII))))
        .isEqualTo(flow.challenge());
  }

  @Test
  void repeatedLoginReusesIdentityWithoutRegistrationEvent() throws Exception {
    String email = uniqueEmail();
    profile(email, true);
    callback(start(null, "login")).andExpect(status().isFound());
    UUID id = users.findByEmail(email).orElseThrow().getId();
    callback(start(null, "login")).andExpect(status().isFound());
    assertThat(users.findByEmail(email).orElseThrow().getId()).isEqualTo(id);
    assertThat(identities.findAllByUserId(id)).hasSize(1);
    assertThat(events.stream(UserRegistered.class).filter(e -> e.userId().equals(id)).count())
        .isEqualTo(1);
  }

  @Test
  void verifiedEmailLinksGoogleToExistingFacebookAccount() throws Exception {
    String email = uniqueEmail();
    AppUser existing = registered(email);
    identities.save(
        AuthIdentity.builder()
            .id(UUID.randomUUID())
            .provider(AuthProvider.FACEBOOK)
            .providerSubject(UUID.randomUUID().toString())
            .userId(existing.getId())
            .build());
    profile(email.toUpperCase(Locale.ROOT), true);
    callback(start(null, "login")).andExpect(redirectedUrl("http://localhost:5173/auth/callback"));
    assertThat(identities.findAllByUserId(existing.getId()))
        .extracting(AuthIdentity::getProvider)
        .containsExactlyInAnyOrder(AuthProvider.GOOGLE, AuthProvider.FACEBOOK);
    assertThat(accounts.search(email, 0, 10).items().getFirst().linkedProviders())
        .containsExactly("FACEBOOK", "GOOGLE");
    assertThat(accounts.get(existing.getId()).lastActiveAt()).isNotNull();
    assertThat(events.stream(UserRegistered.class).filter(e -> e.userId().equals(existing.getId())))
        .isEmpty();
  }

  @Test
  void guestMergesIntoExistingAccountAndAllGuestRefreshTokensAreRevoked() throws Exception {
    String email = uniqueEmail();
    AppUser existing = registered(email);
    var guest = guest();
    UUID guestId = guest.session().getUser().getId();
    profile(email, true);
    callback(start(refreshCookie(guest), "login"))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback"));
    assertThat(users.findById(guestId).orElseThrow().getMergedIntoId()).isEqualTo(existing.getId());
    assertThat(
            events.stream(GuestMerged.class)
                .filter(e -> e.guestId().equals(guestId) && e.userId().equals(existing.getId()))
                .count())
        .isEqualTo(1);
    assertThat(events.stream(UserRegistered.class).filter(e -> e.userId().equals(existing.getId())))
        .isEmpty();
    mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(guest)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/me").header("Authorization", "Bearer " + guest.session().getAccessToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void guestWithNewEmailPublishesRegistrationAndMerge() throws Exception {
    String email = uniqueEmail();
    var guest = guest();
    profile(email, true);
    callback(start(refreshCookie(guest), "login")).andExpect(status().isFound());
    UUID id = users.findByEmail(email).orElseThrow().getId();
    assertThat(events.stream(UserRegistered.class).filter(e -> e.userId().equals(id)).count())
        .isEqualTo(1);
    assertThat(events.stream(GuestMerged.class).filter(e -> e.userId().equals(id)).count())
        .isEqualTo(1);
  }

  @Test
  void unverifiedEmailCannotLinkOrCreateAccount() throws Exception {
    String email = uniqueEmail();
    profile(email, false);
    callback(start(null, "login"))
        .andExpect(
            redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_EMAIL_UNVERIFIED"));
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void lockedAccountCannotLoginAndLockRevokesOldRefreshEvenAfterUnlock() throws Exception {
    String email = uniqueEmail();
    profile(email, true);
    Cookie refresh =
        callback(start(null, "login")).andReturn().getResponse().getCookie("meloo_refresh");
    UUID id = users.findByEmail(email).orElseThrow().getId();
    accounts.setLocked(id, true, "Test moderation");
    assertThat(
            tokens
                .findByTokenHash(RefreshTokenService.hashToken(refresh.getValue()))
                .orElseThrow()
                .getRevokedAt())
        .isNotNull();
    callback(start(null, "login"))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=ACCOUNT_LOCKED"));
    accounts.setLocked(id, false, "");
    mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
  }

  @Test
  void allowlistedEmailReceivesAdminAndOtherEmailStaysUser() throws Exception {
    profile("bootstrap@example.com", true);
    callback(start(null, "login")).andExpect(redirectedUrl("http://localhost:5173/auth/callback"));
    assertThat(users.findByEmail("bootstrap@example.com").orElseThrow().getRole())
        .isEqualTo(UserRole.ADMIN);
    String email = uniqueEmail();
    profile(email, true);
    callback(start(null, "login")).andExpect(status().isFound());
    assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.USER);
  }

  @Test
  void stateMismatchOrTamperedCookieNeverCallsTokenEndpoint() throws Exception {
    Flow flow = start(null, "login");
    mvc.perform(
            get(BASE + "/callback")
                .servletPath(BASE + "/callback")
                .param("code", "code")
                .param("state", "wrong")
                .cookie(flow.cookie()))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"));
    Cookie tampered =
        new Cookie(OAuthStateRepository.COOKIE, "tampered" + flow.cookie().getValue());
    callback(new Flow(tampered, flow.state(), flow.challenge()))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"));
    GOOGLE.verify(0, postRequestedFor(urlEqualTo("/token")));
  }

  @Test
  void providerDenialClearsStateAndDoesNotLeakErrorDescription() throws Exception {
    Flow flow = start(null, "login");
    mvc.perform(
            get(BASE + "/callback")
                .servletPath(BASE + "/callback")
                .param("state", flow.state())
                .param("error", "access_denied")
                .param("error_description", "sensitive")
                .cookie(flow.cookie()))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"))
        .andExpect(cookie().maxAge(OAuthStateRepository.COOKIE, 0));
  }

  @Test
  void lockedOrLoggedOutGuestCannotCompletePreviouslyStartedFlow() throws Exception {
    var guest = guest();
    Flow flow = start(refreshCookie(guest), "login");
    profile(uniqueEmail(), true);
    accounts.setLocked(guest.session().getUser().getId(), true, "Moderation");
    callback(flow)
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=ACCOUNT_LOCKED"));
    var another = guest();
    Flow other = start(refreshCookie(another), "login");
    guests.logout(another.rawRefreshToken());
    callback(other)
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"));
  }

  @Test
  void linkCallbackLeavesCodeUnusedAndBearerPostCompletesGuestUpgrade() throws Exception {
    var guest = guest();
    String email = uniqueEmail();
    profile(email, true);
    Flow flow = start(refreshCookie(guest), "link");
    callback(flow)
        .andExpect(
            redirectedUrl(
                "http://localhost:5173/auth/callback?mode=link&provider=google&code=mock-code&state="
                    + java.net.URLEncoder.encode(flow.state(), StandardCharsets.UTF_8)));
    GOOGLE.verify(0, postRequestedFor(urlEqualTo("/token")));
    mvc.perform(
            post(BASE + "/link")
                .cookie(flow.cookie())
                .header("Authorization", "Bearer " + guest.session().getAccessToken())
                .contentType("application/json")
                .content("{\"code\":\"mock-code\",\"state\":\"" + flow.state() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(cookie().exists("meloo_refresh"));
  }

  @Test
  void linkRejectsWrongBearerBindingBeforeExchangingCode() throws Exception {
    var guest = guest();
    var other = guest();
    Flow flow = start(refreshCookie(guest), "link");
    mvc.perform(
            post(BASE + "/link")
                .cookie(flow.cookie())
                .header("Authorization", "Bearer " + other.session().getAccessToken())
                .contentType("application/json")
                .content("{\"code\":\"code\",\"state\":\"" + flow.state() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));
    GOOGLE.verify(0, postRequestedFor(urlEqualTo("/token")));
  }

  @Test
  void returnPathCannotRedirectOutsideSpa() throws Exception {
    mvc.perform(get(BASE).param("returnTo", "//evil.example"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Bad Request"));
    mvc.perform(get(BASE).param("mode", "other")).andExpect(status().isBadRequest());
    mvc.perform(get(BASE).param("mode", "link")).andExpect(status().isBadRequest());
    String email = uniqueEmail();
    profile(email, true);
    var start = mvc.perform(get(BASE).param("returnTo", "/profile")).andReturn();
    Flow flow = flow(start);
    callback(flow)
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?returnTo=/profile"));
  }

  @Test
  void exportedUserApiSearchesCountsAndChangesRolesWithValidation() {
    String email = uniqueEmail();
    AppUser user = registered(email);
    assertThat(accounts.search(email, 0, 10).items())
        .extracting(com.meloo.identity.api.UserAccount::id)
        .containsExactly(user.getId());
    assertThat(accounts.get(user.getId()).role()).isEqualTo("USER");
    accounts.setRole(user.getId(), "ADMIN");
    assertThat(accounts.get(user.getId()).role()).isEqualTo("ADMIN");
    assertThat(
            accounts.countCreated(
                "REGISTERED", Instant.now().minusSeconds(60), Instant.now().plusSeconds(60)))
        .isPositive();
    assertThatThrownBy(() -> accounts.setLocked(user.getId(), true, " "))
        .isInstanceOf(com.meloo.shared.api.ApiException.class);
    assertThatThrownBy(() -> accounts.search("", 0, 101))
        .isInstanceOf(com.meloo.shared.api.ApiException.class);
    assertThatThrownBy(() -> accounts.setRole(user.getId(), "ROOT"))
        .isInstanceOf(com.meloo.shared.api.ApiException.class);
  }

  @Test
  void tokenEndpointFailureDoesNotCreateUserOrRefreshCookie() throws Exception {
    String email = uniqueEmail();
    profile(email, true);
    GOOGLE.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.serverError()));
    callback(start(null, "login"))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=OAUTH_STATE_INVALID"))
        .andExpect(cookie().doesNotExist("meloo_refresh"));
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void simultaneousFirstLoginsResolveOneUserAndIdentity() throws Exception {
    String email = uniqueEmail();
    profile(email, true);
    Flow one = start(null, "login");
    Flow two = start(null, "login");
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      var first =
          executor.submit(
              () -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return callback(one).andReturn();
              });
      var second =
          executor.submit(
              () -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return callback(two).andReturn();
              });
      assertThat(
              first.get(20, java.util.concurrent.TimeUnit.SECONDS).getResponse().getRedirectedUrl())
          .isEqualTo("http://localhost:5173/auth/callback");
      assertThat(
              second
                  .get(20, java.util.concurrent.TimeUnit.SECONDS)
                  .getResponse()
                  .getRedirectedUrl())
          .isEqualTo("http://localhost:5173/auth/callback");
    }
    AppUser user = users.findByEmail(email).orElseThrow();
    assertThat(identities.findAllByUserId(user.getId())).hasSize(1);
  }

  @Test
  void loginWithRegisteredCookieCanSwitchAccountsWithoutMergingThem() throws Exception {
    String firstEmail = uniqueEmail();
    profile(firstEmail, true);
    Cookie refresh =
        callback(start(null, "login")).andReturn().getResponse().getCookie("meloo_refresh");
    String secondEmail = uniqueEmail();
    profile(secondEmail, true);
    callback(start(refresh, "login"))
        .andExpect(redirectedUrl("http://localhost:5173/auth/callback"));
    assertThat(users.findByEmail(firstEmail).orElseThrow().getMergedIntoId()).isNull();
    assertThat(users.findByEmail(secondEmail)).isPresent();
  }

  @Test
  void linkRequestValidationKeepsRequiredFieldsAndUriLengthConstraint() throws Exception {
    var guest = guest();
    Flow flow = start(refreshCookie(guest), "link");
    mvc.perform(
            post(BASE + "/link")
                .cookie(flow.cookie())
                .header("Authorization", "Bearer " + guest.session().getAccessToken())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    String longUri = "https://meloo.example/" + "x".repeat(501);
    mvc.perform(
            post(BASE + "/link")
                .cookie(flow.cookie())
                .header("Authorization", "Bearer " + guest.session().getAccessToken())
                .contentType("application/json")
                .content(
                    "{\"code\":\"code\",\"state\":\""
                        + flow.state()
                        + "\",\"redirectUri\":\""
                        + longUri
                        + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private void profile(String email, boolean verified) {
    GOOGLE.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
            .willReturn(
                okJson(
                    "{\"access_token\":\"mock-access\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"email profile\"}")));
    // Same subject for each email across repeated attempts, different subjects between tests.
    String subject =
        UUID.nameUUIDFromBytes(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
            .toString();
    GOOGLE.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/userinfo"))
            .withHeader("Authorization", equalTo("Bearer mock-access"))
            .willReturn(
                okJson(
                    "{\"sub\":\""
                        + subject
                        + "\",\"email\":\""
                        + email
                        + "\",\"email_verified\":"
                        + verified
                        + "}")));
  }

  private Flow start(Cookie refresh, String mode) throws Exception {
    var request = get(BASE).param("mode", mode);
    if (refresh != null) {
      request.cookie(refresh);
    }
    return flow(mvc.perform(request).andExpect(status().isFound()).andReturn());
  }

  private Flow flow(MvcResult result) {
    var params =
        UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
            .build()
            .getQueryParams();
    assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
    return new Flow(
        result.getResponse().getCookie(OAuthStateRepository.COOKIE),
        URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8),
        URLDecoder.decode(params.getFirst("code_challenge"), StandardCharsets.UTF_8));
  }

  private org.springframework.test.web.servlet.ResultActions callback(Flow flow) throws Exception {
    return mvc.perform(
        get(BASE + "/callback")
            .servletPath(BASE + "/callback")
            .param("code", "mock-code")
            .param("state", flow.state())
            .cookie(flow.cookie()));
  }

  private GuestAuthService.AuthResult guest() {
    return guests.createGuest(new GuestRequestDto().nickname("Guest_Test"));
  }

  private Cookie refreshCookie(GuestAuthService.AuthResult guest) {
    return new Cookie("meloo_refresh", guest.rawRefreshToken());
  }

  private AppUser registered(String email) {
    return users.saveAndFlush(
        AppUser.builder()
            .id(UUID.randomUUID())
            .kind(UserKind.REGISTERED)
            .nickname("Player_Test")
            .email(email)
            .build());
  }

  private String uniqueEmail() {
    return UUID.randomUUID() + "@example.com";
  }

  private Map<String, String> parseForm(String form) {
    Map<String, String> result = new HashMap<>();
    for (String entry : form.split("&")) {
      String[] pair = entry.split("=", 2);
      result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    return result;
  }

  private record Flow(Cookie cookie, String state, String challenge) {}
}
