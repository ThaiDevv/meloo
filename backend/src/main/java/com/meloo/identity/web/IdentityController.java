package com.meloo.identity.web;

import com.meloo.generated.api.IdentityApi;
import com.meloo.generated.model.AuthSessionDto;
import com.meloo.generated.model.FacebookDataDeletionRequestDto;
import com.meloo.generated.model.FacebookDataDeletionResponseDto;
import com.meloo.generated.model.GuestRequestDto;
import com.meloo.generated.model.MeDto;
import com.meloo.generated.model.OAuthLinkRequestDto;
import com.meloo.generated.model.OAuthProviderSlugDto;
import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.oauth.OAuthLinkService;
import com.meloo.identity.service.AccountDeletionService;
import com.meloo.identity.service.FacebookDataDeletion;
import com.meloo.identity.service.GuestAuthService;
import com.meloo.identity.service.GuestRateLimiter;
import com.meloo.identity.service.NicknameValidator;
import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController implements IdentityApi {

  private final ObjectProvider<OAuthLinkService> oauthLink;
  private final AccountDeletionService deletion;
  private final FacebookDataDeletion facebookDeletion;
  private final GuestAuthService guestAuthService;
  private final GuestRateLimiter guestRateLimiter;
  private final NicknameValidator nicknameValidator;
  private final IdentityProperties properties;
  private final List<String> allowedOrigins;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  public IdentityController(
      ObjectProvider<OAuthLinkService> oauthLink,
      AccountDeletionService deletion,
      FacebookDataDeletion facebookDeletion,
      GuestAuthService guestAuthService,
      GuestRateLimiter guestRateLimiter,
      NicknameValidator nicknameValidator,
      IdentityProperties properties,
      @Value("${meloo.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins,
      HttpServletRequest request,
      HttpServletResponse response) {
    this.oauthLink = oauthLink;
    this.deletion = deletion;
    this.facebookDeletion = facebookDeletion;
    this.guestAuthService = guestAuthService;
    this.guestRateLimiter = guestRateLimiter;
    this.nicknameValidator = nicknameValidator;
    this.properties = properties;
    this.allowedOrigins = List.copyOf(allowedOrigins);
    this.request = request;
    this.response = response;
  }

  @Override
  public ResponseEntity<AuthSessionDto> linkOAuthProvider(
      OAuthProviderSlugDto provider, OAuthLinkRequestDto body) {
    var service = oauthLink.getIfAvailable();
    if (service == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "OAuth provider unavailable");
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token required");
    }
    return ResponseEntity.ok(
        service
            .link(provider.getValue(), body, UUID.fromString(jwt.getSubject()), request, response)
            .session());
  }

  @Override
  public ResponseEntity<AuthSessionDto> createGuest(GuestRequestDto guestRequestDto) {
    if (guestRequestDto == null || guestRequestDto.getNickname() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname is required");
    }
    nicknameValidator.validate(guestRequestDto.getNickname());

    String clientIp = resolveClientIp(request);
    GuestRateLimiter.RateLimitResult rateLimitResult = guestRateLimiter.tryAcquire(clientIp);
    if (!rateLimitResult.allowed()) {
      response.setHeader("Retry-After", String.valueOf(rateLimitResult.retryAfterSeconds()));
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Rate limit exceeded. Try again later.");
    }

    GuestAuthService.AuthResult result = guestAuthService.createGuest(guestRequestDto);
    ResponseCookie cookie =
        buildRefreshCookie(result.rawRefreshToken(), properties.jwt().refreshTokenTtl());

    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(result.session());
  }

  @Override
  public ResponseEntity<AuthSessionDto> refreshSession() {
    String rawRefreshToken = extractRefreshCookie(request);
    String origin = request.getHeader(HttpHeaders.ORIGIN);

    GuestAuthService.AuthResult result =
        guestAuthService.refreshSession(rawRefreshToken, origin, allowedOrigins);
    ResponseCookie cookie =
        buildRefreshCookie(result.rawRefreshToken(), properties.jwt().refreshTokenTtl());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(result.session());
  }

  @Override
  public ResponseEntity<Void> logout() {
    String rawRefreshToken = extractRefreshCookie(request);
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    guestAuthService.logout(rawRefreshToken, origin, allowedOrigins);

    ResponseCookie cookie = buildClearRefreshCookie();

    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
  }

  @Override
  public ResponseEntity<MeDto> getMe() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject");
    }

    MeDto me = guestAuthService.getMe(userId);
    return ResponseEntity.ok(me);
  }

  @Override
  public ResponseEntity<Void> deleteMyAccount() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token required");
    }
    deletion.deleteMyAccount(UUID.fromString(jwt.getSubject()));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, buildClearRefreshCookie().toString())
        .build();
  }

  @Override
  public ResponseEntity<FacebookDataDeletionResponseDto> handleFacebookDataDeletion(
      FacebookDataDeletionRequestDto body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(facebookDeletion.handle(body.getSignedRequest()));
  }

  private ResponseCookie buildRefreshCookie(String rawToken, Duration maxAge) {
    return ResponseCookie.from(properties.cookie().name(), rawToken)
        .httpOnly(true)
        .secure(properties.cookie().secure())
        .sameSite(properties.cookie().sameSite())
        .path(properties.cookie().path())
        .maxAge(maxAge)
        .build();
  }

  private ResponseCookie buildClearRefreshCookie() {
    return ResponseCookie.from(properties.cookie().name(), "")
        .httpOnly(true)
        .secure(properties.cookie().secure())
        .sameSite(properties.cookie().sameSite())
        .path(properties.cookie().path())
        .maxAge(Duration.ZERO)
        .build();
  }

  private String extractRefreshCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> properties.cookie().name().equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * Resolves client IP for rate limiting.
   *
   * <p>Assumptions & Constraints (MEL-33 / MEL-127): Backend is deployed behind Caddy reverse proxy
   * on EC2 (ADR-006, infra/README.md). It assumes Caddy overwrites X-Forwarded-For with the real
   * client IP ({remote_host}) and backend port is bound only to internal Docker network (not
   * exposed publicly). Ticket MEL-127 will formalize Caddyfile headers and evaluate
   * server.forward-headers-strategy.
   */
  private String resolveClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
