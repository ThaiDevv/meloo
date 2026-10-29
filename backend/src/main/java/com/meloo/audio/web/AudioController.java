package com.meloo.audio.web;

import com.meloo.audio.api.AccountLockChecker;
import com.meloo.audio.domain.AccountLockedException;
import com.meloo.audio.domain.AudioException;
import com.meloo.audio.domain.AudioRangeNotSatisfiableException;
import com.meloo.audio.domain.AudioRateLimitException;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.AudioTokenCodec;
import com.meloo.audio.domain.AudioTokenInvalidException;
import com.meloo.audio.service.AudioRateLimiter;
import com.meloo.audio.service.AudioStreamService;
import com.meloo.generated.api.AudioApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class AudioController implements AudioApi {

  private static final Logger log = LoggerFactory.getLogger(AudioController.class);

  private final AudioTokenCodec audioTokenCodec;
  private final AudioRateLimiter audioRateLimiter;
  private final AccountLockChecker accountLockChecker;
  private final AudioStreamService audioStreamService;

  public AudioController(
      AudioTokenCodec audioTokenCodec,
      AudioRateLimiter audioRateLimiter,
      AccountLockChecker accountLockChecker,
      AudioStreamService audioStreamService) {
    this.audioTokenCodec = audioTokenCodec;
    this.audioRateLimiter = audioRateLimiter;
    this.accountLockChecker = accountLockChecker;
    this.audioStreamService = audioStreamService;
  }

  @Override
  public ResponseEntity<Resource> getAudioClip(String token, @Nullable String range) {
    AudioToken audioToken = audioTokenCodec.decodeAndVerify(token);

    // If request contains authenticated context (e.g. Bearer token in tests or clients),
    // verify that caller matches the token's bound playerId.
    verifyPlayerOwnershipIfAuthenticated(audioToken.playerId());

    // Check account lock status (BR-28)
    if (accountLockChecker.isAccountLocked(audioToken.playerId())) {
      throw new AccountLockedException();
    }

    // Rate limiting per token and per player (Bucket4j)
    audioRateLimiter.checkRateLimit(
        audioToken.roundId(), audioToken.playerId(), audioToken.level());

    AudioStreamService.SlicedStreamResult result;
    try {
      result = audioStreamService.streamAudioClip(audioToken, range);
    } catch (AudioException ae) {
      throw ae;
    } catch (IOException e) {
      // M1: Do not log raw exception message which could contain URLs
      log.error("I/O error streaming audio clip: {}", e.getClass().getSimpleName());
      throw new AudioException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "INTERNAL_ERROR",
          "Internal server error",
          "Failed to stream audio clip");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "audio/mpeg");
    headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
    headers.setContentLength(result.contentLength());
    if (result.contentRange() != null) {
      headers.set(HttpHeaders.CONTENT_RANGE, result.contentRange());
    }

    return new ResponseEntity<>(result.resource(), headers, result.status());
  }

  private void verifyPlayerOwnershipIfAuthenticated(UUID tokenPlayerId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return;
    }

    UUID authenticatedUserId = null;
    if (auth.getPrincipal() instanceof Jwt jwt) {
      try {
        authenticatedUserId = UUID.fromString(jwt.getSubject());
      } catch (IllegalArgumentException ignored) {
      }
    } else if (auth.getName() != null) {
      try {
        authenticatedUserId = UUID.fromString(auth.getName());
      } catch (IllegalArgumentException ignored) {
      }
    }

    if (authenticatedUserId != null && !authenticatedUserId.equals(tokenPlayerId)) {
      throw AudioTokenInvalidException.invalidSignature();
    }
  }

  @ExceptionHandler(AudioException.class)
  public ResponseEntity<ProblemDetail> handleAudioException(
      AudioException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setType(URI.create("about:blank"));
    problem.setTitle(ex.getTitle());
    // Strictly avoid echoing raw token values in error instance URIs or logs per OpenAPI spec
    problem.setInstance(URI.create("/api/v1/audio/%7Btoken%7D"));
    problem.setProperty("code", ex.getCode());

    String correlationId = request.getHeader("X-Correlation-Id");
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));

    if (ex instanceof AudioRangeNotSatisfiableException rangeEx) {
      headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + rangeEx.getTotalLength());
    } else if (ex instanceof AudioRateLimitException rateLimitEx) {
      headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitEx.getRetryAfterSeconds()));
    }

    return new ResponseEntity<>(problem, headers, ex.getStatus());
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> handleValidationExceptions(
      Exception ex, HttpServletRequest request) {
    return handleAudioException(AudioTokenInvalidException.malformed(), request);
  }
}
