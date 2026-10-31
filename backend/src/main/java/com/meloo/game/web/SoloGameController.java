package com.meloo.game.web;

import com.meloo.game.solo.SoloGameService;
import com.meloo.game.solo.SoloRateLimiter;
import com.meloo.generated.api.GameApi;
import com.meloo.generated.model.CreateSoloMatchRequestDto;
import com.meloo.generated.model.GuessRequestDto;
import com.meloo.generated.model.GuessResponseDto;
import com.meloo.generated.model.MatchResultDto;
import com.meloo.generated.model.RoundEndDto;
import com.meloo.generated.model.RoundResultDto;
import com.meloo.generated.model.RoundStateDto;
import com.meloo.generated.model.SoloMatchDto;
import com.meloo.shared.api.ApiException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

/** Controller triển khai API chơi đơn theo hợp đồng OpenAPI v1. */
@RestController
public class SoloGameController implements GameApi {

  private final SoloGameService soloGameService;
  private final SoloRateLimiter soloRateLimiter;

  public SoloGameController(SoloGameService soloGameService, SoloRateLimiter soloRateLimiter) {
    this.soloGameService =
        Objects.requireNonNull(soloGameService, "soloGameService must not be null");
    this.soloRateLimiter =
        Objects.requireNonNull(soloRateLimiter, "soloRateLimiter must not be null");
  }

  @Override
  public ResponseEntity<SoloMatchDto> createSoloMatch(
      CreateSoloMatchRequestDto createSoloMatchRequestDto) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkCreateMatch(user.userId());
    SoloMatchDto match =
        soloGameService.createSoloMatch(
            user.userId(), user.nickname(), user.isGuest(), createSoloMatchRequestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(match);
  }

  @Override
  public ResponseEntity<SoloMatchDto> getActiveSoloMatch() {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    SoloMatchDto match = soloGameService.getActiveSoloMatch(user.userId());
    return ResponseEntity.ok(match);
  }

  @Override
  public ResponseEntity<RoundStateDto> getCurrentRound(UUID matchId) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    RoundStateDto round = soloGameService.getCurrentRound(user.userId(), matchId);
    return ResponseEntity.ok(round);
  }

  @Override
  public ResponseEntity<RoundStateDto> listenMore(
      UUID matchId, Integer roundNo, @Nullable Integer expectedLevel) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    RoundStateDto round =
        soloGameService.listenMore(user.userId(), matchId, roundNo, expectedLevel);
    return ResponseEntity.ok(round);
  }

  @Override
  public ResponseEntity<RoundEndDto> skipRound(UUID matchId, Integer roundNo) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    RoundEndDto result = soloGameService.skipRound(user.userId(), matchId, roundNo);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<GuessResponseDto> submitGuess(
      UUID matchId, Integer roundNo, GuessRequestDto guessRequestDto) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    GuessResponseDto result =
        soloGameService.submitGuess(user.userId(), matchId, roundNo, guessRequestDto);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<RoundResultDto> getRoundResult(UUID matchId, Integer roundNo) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    RoundResultDto result = soloGameService.getRoundResult(user.userId(), matchId, roundNo);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<MatchResultDto> getMatchResult(UUID matchId) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    MatchResultDto result = soloGameService.getMatchResult(user.userId(), matchId);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<MatchResultDto> abandonSoloMatch(UUID matchId) {
    UserPrincipal user = getCurrentUser();
    soloRateLimiter.checkAction(user.userId());
    MatchResultDto result = soloGameService.abandonSoloMatch(user.userId(), matchId);
    return ResponseEntity.ok(result);
  }

  private UserPrincipal getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid credentials");
    }

    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject");
    }

    String nickname = jwt.getClaimAsString("nickname");
    if (nickname == null || nickname.isBlank()) {
      nickname = "Player";
    }

    String role = jwt.getClaimAsString("role");
    String kind = jwt.getClaimAsString("kind");
    boolean isGuest = "GUEST".equalsIgnoreCase(kind) || "GUEST".equalsIgnoreCase(role);

    return new UserPrincipal(userId, nickname, isGuest);
  }

  private record UserPrincipal(UUID userId, String nickname, boolean isGuest) {}
}
