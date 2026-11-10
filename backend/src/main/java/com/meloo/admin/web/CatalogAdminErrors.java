package com.meloo.admin.web;

import com.meloo.admin.catalog.InsufficientPlaylistTracks;
import com.meloo.catalog.api.CatalogAdminException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminController.class)
public class CatalogAdminErrors {
  @ExceptionHandler(InsufficientPlaylistTracks.class)
  public ProblemDetail insufficient(InsufficientPlaylistTracks error, HttpServletRequest request) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage());
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Not enough tracks");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "NOT_ENOUGH_TRACKS");
    problem.setProperty("missingTracks", error.missing());
    return problem;
  }

  @ExceptionHandler(CatalogAdminException.class)
  public ProblemDetail handle(CatalogAdminException error, HttpServletRequest request) {
    HttpStatus status =
        switch (error.reason()) {
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
          case CONFLICT -> HttpStatus.CONFLICT;
          case PLAYLIST_NOT_ENOUGH_TRACKS -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    String code =
        switch (error.reason()) {
          case NOT_FOUND ->
              request.getRequestURI().contains("/import")
                  ? "NOT_FOUND"
                  : request.getRequestURI().contains("/tracks/")
                      ? "TRACK_NOT_FOUND"
                      : "PLAYLIST_NOT_FOUND";
          case INVALID_ARGUMENT -> "VALIDATION_FAILED";
          case CONFLICT -> "CONFLICT";
          case PLAYLIST_NOT_ENOUGH_TRACKS -> "NOT_ENOUGH_TRACKS";
        };
    var problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
    problem.setType(URI.create("about:blank"));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    return problem;
  }
}
