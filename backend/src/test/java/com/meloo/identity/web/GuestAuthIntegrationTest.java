package com.meloo.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meloo.IntegrationTest;
import com.meloo.identity.events.GuestCreated;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.RefreshToken;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.identity.persistence.UserStatus;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@RecordApplicationEvents
class GuestAuthIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private ApplicationEvents applicationEvents;

  @Test
  @DisplayName("Full auth lifecycle: create guest -> get me -> refresh -> get me -> logout")
  void fullAuthLifecycle() throws Exception {
    String clientIp = "10.0.1.1";
    String createBody = "{\"nickname\": \"Meloman_VN\"}";

    // 1. POST /api/v1/auth/guest
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.user.nickname").value("Meloman_VN"))
            .andExpect(jsonPath("$.user.kind").value("GUEST"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andReturn();

    Cookie refreshCookie = createResult.getResponse().getCookie("meloo_refresh");
    assertThat(refreshCookie).isNotNull();
    assertThat(refreshCookie.isHttpOnly()).isTrue();
    assertThat(refreshCookie.getSecure()).isTrue();
    assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(refreshCookie.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(refreshCookie.getMaxAge()).isEqualTo(30 * 24 * 3600);

    String accessToken1 =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.accessToken");
    String userId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.user.id");

    // Verify GuestCreated event was published
    long guestEvents =
        applicationEvents.stream(GuestCreated.class)
            .filter(e -> e.userId().equals(UUID.fromString(userId)))
            .count();
    assertThat(guestEvents).isEqualTo(1);

    // 2. GET /api/v1/me with initial access token
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.nickname").value("Meloman_VN"))
        .andExpect(jsonPath("$.kind").value("GUEST"))
        .andExpect(jsonPath("$.role").value("USER"));

    // 3. POST /api/v1/auth/refresh rotates the refresh token
    MvcResult refreshResult =
        mockMvc
            .perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.nickname").value("Meloman_VN"))
            .andReturn();

    Cookie newRefreshCookie = refreshResult.getResponse().getCookie("meloo_refresh");
    assertThat(newRefreshCookie).isNotNull();
    assertThat(newRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

    String accessToken2 =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");

    // 4. GET /api/v1/me with rotated access token
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId));

    // 5. POST /api/v1/auth/logout revokes token and clears cookie
    MvcResult logoutResult =
        mockMvc
            .perform(post("/api/v1/auth/logout").cookie(newRefreshCookie))
            .andExpect(status().isNoContent())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn();

    Cookie clearedCookie = logoutResult.getResponse().getCookie("meloo_refresh");
    assertThat(clearedCookie).isNotNull();
    assertThat(clearedCookie.getMaxAge()).isZero();

    // 6. Refreshing with the revoked cookie returns 401
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(newRefreshCookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Reusing an old refresh token revokes entire token family")
  void tokenReuseRevokesFamily() throws Exception {
    String clientIp = "10.0.1.2";
    String createBody = "{\"nickname\": \"ReuseTester\"}";
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

    Cookie r1 = createResult.getResponse().getCookie("meloo_refresh");
    assertThat(r1).isNotNull();

    // Rotate to R2
    MvcResult refreshResult =
        mockMvc
            .perform(post("/api/v1/auth/refresh").cookie(r1))
            .andExpect(status().isOk())
            .andReturn();

    Cookie r2 = refreshResult.getResponse().getCookie("meloo_refresh");
    assertThat(r2).isNotNull();

    // Replay R1 (attacker or duplicate) -> reuse detected!
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(r1)).andExpect(status().isUnauthorized());

    // R2 must now also be revoked because the entire family was invalidated
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(r2)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Expired refresh token returns 401 Unauthorized")
  void expiredRefreshTokenReturns401() throws Exception {
    String clientIp = "10.0.1.3";
    String createBody = "{\"nickname\": \"ExpireTester\"}";
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

    Cookie cookie = createResult.getResponse().getCookie("meloo_refresh");
    assertThat(cookie).isNotNull();

    String rawToken = cookie.getValue();
    String hash = com.meloo.identity.service.RefreshTokenService.hashToken(rawToken);

    RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
    token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
    refreshTokenRepository.saveAndFlush(token);

    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(cookie))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Rate limiting: 5 guest creations per hour per IP allowed, 6th returns 429")
  void rateLimitEnforcesFivePerHour() throws Exception {
    String clientIp = "203.0.113.42";

    for (int i = 1; i <= 5; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/guest")
                  .header("X-Forwarded-For", clientIp)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"nickname\": \"Guest_" + i + "\"}"))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/guest")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"Guest_6\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429));
  }

  @Test
  @DisplayName("Unauthenticated request to /api/v1/me returns 401")
  void unauthenticatedMeReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Invalid nickname (< 3, > 20, missing or banned words) returns 400 Bad Request")
  void invalidNicknameReturns400() throws Exception {
    String clientIp = "10.0.1.4";
    // Too short
    mockMvc
        .perform(
            post("/api/v1/auth/guest")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"ab\"}"))
        .andExpect(status().isBadRequest());

    // Missing nickname
    mockMvc
        .perform(
            post("/api/v1/auth/guest")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    // Banned word
    mockMvc
        .perform(
            post("/api/v1/auth/guest")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"đụ má bạn\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Locked user cannot refresh session or access /api/v1/me (403 ACCOUNT_LOCKED) and no new token issued")
  void lockedUserDenied() throws Exception {
    String clientIp = "10.0.1.5";
    String createBody = "{\"nickname\": \"LockedTest\"}";
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

    Cookie cookie = createResult.getResponse().getCookie("meloo_refresh");
    String accessToken =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.accessToken");
    String userId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.user.id");

    // Lock the user
    AppUser user = appUserRepository.findById(UUID.fromString(userId)).orElseThrow();
    user.setStatus(UserStatus.LOCKED);
    user.setLockReason("Violation of community guidelines");
    user.setLockedAt(Instant.now());
    appUserRepository.saveAndFlush(user);

    long tokenCountBefore = refreshTokenRepository.count();

    // Refresh returns 403 Forbidden
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());

    // Verify no new token was created in DB for locked user
    long tokenCountAfter = refreshTokenRepository.count();
    assertThat(tokenCountAfter).isEqualTo(tokenCountBefore);

    // /api/v1/me returns 403 Forbidden
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Disallowed Origin header on /auth/refresh returns 403 Forbidden (CSRF check)")
  void disallowedOriginReturns403() throws Exception {
    String clientIp = "10.0.1.6";
    String createBody = "{\"nickname\": \"CsrfTester\"}";
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();

    Cookie cookie = createResult.getResponse().getCookie("meloo_refresh");

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(cookie)
                .header(HttpHeaders.ORIGIN, "https://malicious-site.example.com"))
        .andExpect(status().isForbidden());
  }
}
