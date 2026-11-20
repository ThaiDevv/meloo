package com.meloo.shared.observability;

import io.sentry.IScopes;
import io.sentry.spring7.SentryExceptionResolver;
import io.sentry.spring7.tracing.TransactionNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Replaces Sentry's MVC resolver, which captures every exception as FATAL before the application
 * advice has chosen a status. This one only remembers the failure; {@link
 * ServerErrorReportingFilter} reports it once the final status proves it was a server error, so
 * client errors (4xx) never reach Sentry.
 */
final class ServerErrorReporter extends SentryExceptionResolver {
  static final String FAILURE_ATTRIBUTE = ServerErrorReporter.class.getName() + ".failure";

  private final IScopes scopes;

  ServerErrorReporter(IScopes scopes, TransactionNameProvider transactionNames) {
    super(scopes, transactionNames, Ordered.HIGHEST_PRECEDENCE);
    this.scopes = scopes;
  }

  @Override
  public @Nullable ModelAndView resolveException(
      HttpServletRequest request,
      HttpServletResponse response,
      @Nullable Object handler,
      Exception exception) {
    request.setAttribute(FAILURE_ATTRIBUTE, exception);
    return null;
  }

  /** Called after MVC wrote the response: only a 5xx status is a server error. */
  void reportIfServerError(HttpServletRequest request, HttpServletResponse response) {
    if (response.getStatus() >= 500 && takeFailure(request) instanceof Exception failure) {
      capture(request, response, failure);
    }
  }

  /**
   * Called when an exception escaped MVC. Spring Security turns its own exceptions into 401/403
   * outside this filter; anything else becomes a container 500.
   */
  void reportEscaped(HttpServletRequest request, HttpServletResponse response, Exception escaped) {
    Exception failure = takeFailure(request) instanceof Exception recorded ? recorded : escaped;
    if (!(failure instanceof AccessDeniedException || failure instanceof AuthenticationException)) {
      capture(request, response, failure);
    }
  }

  private static @Nullable Object takeFailure(HttpServletRequest request) {
    Object failure = request.getAttribute(FAILURE_ATTRIBUTE);
    request.removeAttribute(FAILURE_ATTRIBUTE);
    return failure;
  }

  private void capture(HttpServletRequest request, HttpServletResponse response, Exception error) {
    scopes.captureEvent(createEvent(request, error), createHint(request, response));
  }
}
