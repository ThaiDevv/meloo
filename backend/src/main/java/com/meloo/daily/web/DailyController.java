package com.meloo.daily.web;

import com.meloo.daily.service.DailyService;
import com.meloo.generated.api.DailyApi;
import com.meloo.generated.model.DailyGuessResponseDto;
import com.meloo.generated.model.DailyHistoryResponseDto;
import com.meloo.generated.model.DailyLeaderboardPageDto;
import com.meloo.generated.model.DailyResultDto;
import com.meloo.generated.model.DailyRoundStateDto;
import com.meloo.generated.model.DailyTodayResponseDto;
import com.meloo.generated.model.GuessRequestDto;
import com.meloo.shared.api.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DailyController implements DailyApi {
  private final DailyService service;
  private final DailyRateLimiter limiter;

  public DailyController(DailyService service, DailyRateLimiter limiter) {
    this.service = service;
    this.limiter = limiter;
  }

  @Override
  public ResponseEntity<DailyTodayResponseDto> getDailyToday() {
    return ResponseEntity.ok(service.today(player().id()));
  }

  @Override
  public ResponseEntity<DailyRoundStateDto> startDailyAttempt() {
    var player = player();
    return ResponseEntity.ok(service.start(player.id(), player.guest()));
  }

  @Override
  public ResponseEntity<DailyRoundStateDto> listenMoreDaily(@Nullable Integer expectedLevel) {
    return ResponseEntity.ok(service.listen(player().id(), expectedLevel));
  }

  @Override
  public ResponseEntity<DailyResultDto> skipDaily() {
    return ResponseEntity.ok(service.skip(player().id()));
  }

  @Override
  public ResponseEntity<DailyGuessResponseDto> submitDailyGuess(GuessRequestDto request) {
    return ResponseEntity.ok(service.guess(player().id(), request.getTrackId()));
  }

  @Override
  public ResponseEntity<DailyResultDto> getDailyResult(@Nullable LocalDate date) {
    return ResponseEntity.ok(service.result(player().id(), date));
  }

  @Override
  public ResponseEntity<DailyHistoryResponseDto> getDailyHistory(
      @Nullable LocalDate from, @Nullable LocalDate to) {
    return ResponseEntity.ok(service.history(player().id(), from, to));
  }

  @Override
  public ResponseEntity<DailyLeaderboardPageDto> getDailyLeaderboard(
      @Nullable LocalDate date, Integer page, Integer size) {
    var player = player();
    return ResponseEntity.ok(service.leaderboard(player.id(), player.guest(), date, page, size));
  }

  private Player player() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Credentials required");
    }
    try {
      UUID userId = UUID.fromString(jwt.getSubject());
      limiter.check(userId);
      return new Player(
          userId,
          "GUEST".equals(jwt.getClaimAsString("kind"))
              || "GUEST".equals(jwt.getClaimAsString("role")));
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject");
    }
  }

  private record Player(UUID id, boolean guest) {}
}
