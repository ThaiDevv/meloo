package com.meloo.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Innermost filter (lowest precedence, inside the correlation id and security filters) that hands
 * MVC failures to {@link ServerErrorReporter} once the response status is known.
 */
final class ServerErrorReportingFilter extends OncePerRequestFilter {
  private final ServerErrorReporter reporter;

  ServerErrorReportingFilter(ServerErrorReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    // Async handlers fail on the async dispatch, so the status is only final there.
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException escaped) {
      reporter.reportEscaped(request, response, escaped);
      throw escaped;
    }
    if (!request.isAsyncStarted()) reporter.reportIfServerError(request, response);
  }
}
