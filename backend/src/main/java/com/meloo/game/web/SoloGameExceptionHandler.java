package com.meloo.game.web;

import com.meloo.game.solo.MatchInProgressException;
import com.meloo.game.solo.NotEnoughTracksException;
import com.meloo.generated.model.ErrorCodeDto;
import com.meloo.generated.model.MatchInProgressProblemDto;
import com.meloo.generated.model.NotEnoughTracksProblemDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
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

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }
}
