package com.meloo.identity.oauth;

import com.meloo.identity.config.IdentityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticated encryption protects the verifier and initiating account from browser modification.
 */
public final class OAuthStateRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
  public static final String COOKIE = "meloo_oauth_state";
  public static final String ATTRIBUTE = OAuthStateRepository.class.getName();
  private static final Duration TTL = Duration.ofMinutes(5);
  private final IdentityProperties properties;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public OAuthStateRepository(IdentityProperties properties, ObjectMapper mapper, Clock clock) {
    this.properties = properties;
    this.mapper = mapper;
    this.clock = clock;
    try {
      key =
          new SecretKeySpec(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      ("meloo-oauth-state:" + properties.jwt().secret())
                          .getBytes(StandardCharsets.UTF_8)),
              "AES");
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  @Nullable public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    try {
      String raw = cookie(request, COOKIE);
      if (raw == null || raw.length() > 4096) {
        return null;
      }
      byte[] bytes = Base64.getUrlDecoder().decode(raw);
      if (bytes.length < 29) {
        return null;
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
      cipher.updateAAD(COOKIE.getBytes(StandardCharsets.US_ASCII));
      State state = mapper.readValue(cipher.doFinal(bytes, 12, bytes.length - 12), State.class);
      String submitted = (String) request.getAttribute("submittedOAuthState");
      if (state.expiresAt() <= clock.instant().getEpochSecond()
          || !equal(state.state(), submitted == null ? request.getParameter("state") : submitted)) {
        return null;
      }
      OAuth2AuthorizationRequest result =
          OAuth2AuthorizationRequest.authorizationCode()
              .authorizationUri(state.authorizationUri())
              .clientId(state.clientId())
              .redirectUri(state.redirectUri())
              .scopes(
                  "facebook".equals(state.attributes().get("registration_id"))
                      ? Set.of("email", "public_profile")
                      : Set.of("email", "profile"))
              .state(state.state())
              .attributes(
                  a -> {
                    a.putAll(state.attributes());
                  })
              .additionalParameters(
                  Map.of("code_challenge", state.challenge(), "code_challenge_method", "S256"))
              .build();
      return result;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void saveAuthorizationRequest(
      @Nullable OAuth2AuthorizationRequest authorization,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorization == null) {
      clear(response);
      return;
    }
    try {
      State state =
          new State(
              authorization.getAuthorizationUri(),
              authorization.getClientId(),
              authorization.getRedirectUri(),
              authorization.getState(),
              authorization.getAdditionalParameters().get("code_challenge").toString(),
              authorization.getAttributes().entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          Map.Entry::getKey, e -> e.getValue().toString())),
              clock.instant().plus(TTL).getEpochSecond());
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(COOKIE.getBytes(StandardCharsets.US_ASCII));
      byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(state));
      byte[] packed = new byte[nonce.length + encrypted.length];
      System.arraycopy(nonce, 0, packed, 0, nonce.length);
      System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
      write(response, Base64.getUrlEncoder().withoutPadding().encodeToString(packed), TTL);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to protect OAuth state", e);
    }
  }

  @Override
  @Nullable public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorization = loadAuthorizationRequest(request);
    if (authorization != null) {
      request.setAttribute(ATTRIBUTE, authorization);
    }
    clear(response);
    return authorization;
  }

  public void clear(HttpServletResponse response) {
    write(response, "", Duration.ZERO);
  }

  private void write(HttpServletResponse response, String value, Duration age) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(properties.cookie().secure())
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(age)
            .build()
            .toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
  }

  @Nullable public static String cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> c.getName().equals(name))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  private static boolean equal(@Nullable String a, @Nullable String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private record State(
      String authorizationUri,
      String clientId,
      String redirectUri,
      String state,
      String challenge,
      Map<String, String> attributes,
      long expiresAt) {}
}
