package com.meloo.game.web;

import com.meloo.game.domain.InvalidRoundStateException;
import com.meloo.game.solo.MatchInProgressException;
import com.meloo.game.solo.NotEnoughTracksException;
import com.meloo.game.solo.SoloRateLimitException;
import com.meloo.generated.model.ErrorCodeDto;
import com.meloo.generated.model.MatchInProgressProblemDto;
import com.meloo.generated.model.NotEnoughTracksProblemDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Xử lý ngoại lệ đặc thù cho API chơi đơn (409 MatchInProgress, 422 NotEnoughTracks). */
@RestControllerAdvice(basePackages = "com.meloo.game.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SoloGameExceptionHandler {

  @ExceptionHandler(MatchInProgressException.class)
  public ResponseEntity<MatchInProgressProblemDto> handleMatchInProgress(
      MatchInProgressException ex, HttpServletRequest request) {
    MatchInProgressProblemDto problem = new MatchInProgressProblemDto();
    problem.setType("about:blank");
    problem.setTitle("Match in progress");
    problem.setStatus(HttpStatus.CONFLICT.value());
    problem.setCode(ErrorCodeDto.SOLO_MATCH_IN_PROGRESS);
    problem.setDetail(ex.getMessage());
    problem.setInstance(request.getRequestURI());
    problem.setMatchId(ex.matchId());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));
    return new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(NotEnoughTracksException.class)
  public ResponseEntity<NotEnoughTracksProblemDto> handleNotEnoughTracks(
      NotEnoughTracksException ex, HttpServletRequest request) {
    NotEnoughTracksProblemDto problem = new NotEnoughTracksProblemDto();
    problem.setType("about:blank");
    problem.setTitle("Not enough tracks");
    problem.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
    problem.setCode(ErrorCodeDto.NOT_ENOUGH_TRACKS);
    problem.setDetail(ex.getMessage());
    problem.setInstance(request.getRequestURI());
    problem.setMissingTracks(ex.missingTracks());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));
    return new ResponseEntity<>(problem, headers, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @ExceptionHandler(InvalidRoundStateException.class)
  public ResponseEntity<ProblemDetail> handleInvalidRoundState(
      InvalidRoundStateException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Conflict");
    problem.setProperty("code", "ROUND_NOT_ACTIVE");
    problem.setProperty("correlationId", correlationId(request));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));
    return new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(SoloRateLimitException.class)
  public ResponseEntity<ProblemDetail> handleSoloRateLimit(
      SoloRateLimitException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later.");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Too Many Requests");
    problem.setProperty("code", ErrorCodeDto.RATE_LIMITED.getValue());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("correlationId", correlationId(request));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));
    headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
    return new ResponseEntity<>(problem, headers, HttpStatus.TOO_MANY_REQUESTS);
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> handleValidationExceptions(
      Exception ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("Bad Request");
    problem.setProperty("code", "VALIDATION_FAILED");
    problem.setProperty("correlationId", correlationId(request));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/problem+json"));
    return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
  }

  private static final String CORRELATION_ID_ATTRIBUTE =
      "com.meloo.shared.web.CorrelationIdFilter.id";

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
    if (value != null) {
      return value.toString();
    }
    String header = request.getHeader("X-Correlation-Id");
    return header != null ? header : "unavailable";
  }
}
