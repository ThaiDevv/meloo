package com.meloo.progression.web;

import com.meloo.generated.api.ProgressionApi;
import com.meloo.generated.model.*;
import com.meloo.progression.achievement.AchievementQueries;
import com.meloo.progression.service.ProgressionProfiles;
import com.meloo.progression.service.ProgressionQueries;
import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressionController implements ProgressionApi {
  private final ProgressionQueries queries;
  private final ProgressionProfiles profiles;
  private final AchievementQueries achievements;

  public ProgressionController(
      ProgressionQueries queries, ProgressionProfiles profiles, AchievementQueries achievements) {
    this.queries = queries;
    this.profiles = profiles;
    this.achievements = achievements;
  }

  @Override
  public ResponseEntity<UserProfileDto> getMyProfile() {
    return ResponseEntity.ok(profiles.get(caller()));
  }

  @Override
  public ResponseEntity<UserProfileDto> updateMyProfile(UpdateProfileRequestDto request) {
    return ResponseEntity.ok(
        profiles.update(
            caller(),
            request.getNickname(),
            request.getAvatarId() == null ? null : request.getAvatarId().getValue()));
  }

  @Override
  public ResponseEntity<MatchHistoryPageDto> listMyMatches(Integer page, Integer size) {
    return ResponseEntity.ok(queries.history(caller(), page, size));
  }

  @Override
  public ResponseEntity<MatchHistoryDetailDto> getMyMatchDetail(UUID matchId) {
    return ResponseEntity.ok(queries.detail(caller(), matchId));
  }

  @Override
  public ResponseEntity<PlayerStatsDto> getMyStats() {
    return ResponseEntity.ok(queries.stats(caller()));
  }

  @Override
  public ResponseEntity<RankLeaderboardPageDto> getAllTimeLeaderboard(Integer page, Integer size) {
    return ResponseEntity.ok(queries.leaderboard(caller(), page, size));
  }

  @Override
  public ResponseEntity<AchievementListResponseDto> listMyAchievements() {
    return ResponseEntity.ok(achievements.list(caller()));
  }

  private static UUID caller() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      try {
        return UUID.fromString(jwt.getSubject());
      } catch (IllegalArgumentException ignored) {
        // Invalid subjects receive the same public error as missing credentials.
      }
    }
    throw new ApiException(
        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid credentials");
  }

  // The generated interface uses AOP @Validated rather than MVC method validation.
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> invalidParameters(HttpServletRequest request) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    problem.setTitle("Validation failed");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "VALIDATION_FAILED");
    return ResponseEntity.badRequest().body(problem);
  }
}
