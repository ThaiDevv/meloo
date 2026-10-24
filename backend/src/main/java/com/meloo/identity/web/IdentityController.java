package com.meloo.identity.web;

import com.meloo.generated.api.IdentityApi;
import com.meloo.generated.model.AuthSessionDto;
import com.meloo.generated.model.GuestRequestDto;
import com.meloo.generated.model.MeDto;
import com.meloo.identity.config.IdentityProperties;
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
import org.springframework.beans.factory.annotation.Value;
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

  private final GuestAuthService guestAuthService;
  private final GuestRateLimiter guestRateLimiter;
  private final NicknameValidator nicknameValidator;
  private final IdentityProperties properties;
  private final List<String> allowedOrigins;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  public IdentityController(
      GuestAuthService guestAuthService,
      GuestRateLimiter guestRateLimiter,
      NicknameValidator nicknameValidator,
      IdentityProperties properties,
      @Value("${meloo.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins,
      HttpServletRequest request,
      HttpServletResponse response) {
    this.guestAuthService = guestAuthService;
    this.guestRateLimiter = guestRateLimiter;
    this.nicknameValidator = nicknameValidator;
    this.properties = properties;
    this.allowedOrigins = List.copyOf(allowedOrigins);
    this.request = request;
    this.response = response;
  }

  @Override
  public ResponseEntity<AuthSessionDto> createGuest(GuestRequestDto guestRequestDto) {
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
    guestAuthService.logout(rawRefreshToken);

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

  private String resolveClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
