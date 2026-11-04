package com.meloo.room.lobby;

import com.meloo.generated.model.ErrorCodeDto;
import com.meloo.generated.model.NotEnoughTracksProblemDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Xử lý ngoại lệ đặc thù cho API phòng nhiều người chơi (422 NotEnoughTracks). */
@RestControllerAdvice(basePackages = "com.meloo.room.lobby")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoomLobbyExceptionHandler {

  @ExceptionHandler(RoomNotEnoughTracksException.class)
  public ResponseEntity<NotEnoughTracksProblemDto> handleNotEnoughTracks(
      RoomNotEnoughTracksException ex, HttpServletRequest request) {
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
}
