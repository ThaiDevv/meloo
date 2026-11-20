package com.meloo.shared.observability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.spring7.tracing.SpringMvcTransactionNameProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ServerErrorReporterTest {
  private final IScopes scopes = mock(IScopes.class);
  private final ServerErrorReporter reporter =
      new ServerErrorReporter(scopes, new SpringMvcTransactionNameProvider());
  private final ServerErrorReportingFilter filter = new ServerErrorReportingFilter(reporter);

  {
    when(scopes.getOptions()).thenReturn(new SentryOptions());
  }

  @Test
  void reportsOnlyRecordedFailuresWhoseFinalStatusIsServerError() throws Exception {
    run(404, new IllegalArgumentException("client"));
    run(503, null);
    verify(scopes, never()).captureEvent(any(SentryEvent.class), any(Hint.class));

    run(503, new IllegalStateException("server"));
    verify(scopes, times(1)).captureEvent(any(SentryEvent.class), any(Hint.class));
  }

  @Test
  void escapedSecurityFailuresAreLeftToSecurityButOtherEscapesAreReported() {
    escape(new AccessDeniedException("403"));
    escape(new InsufficientAuthenticationException("401"));
    verify(scopes, never()).captureEvent(any(SentryEvent.class), any(Hint.class));

    escape(new IllegalStateException("500"));
    verify(scopes, times(1)).captureEvent(any(SentryEvent.class), any(Hint.class));
  }

  private void run(int status, Exception recorded) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          if (recorded != null) reporter.resolveException(request, response, null, recorded);
          response.setStatus(status);
        });
  }

  private void escape(RuntimeException error) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      reporter.resolveException(request, response, null, error);
                      throw error;
                    }))
        .isSameAs(error);
  }
}
