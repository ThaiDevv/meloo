package com.meloo.shared.observability;

import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.spring7.tracing.SpringMvcTransactionNameProvider;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MonitoringConfiguration {
  @Bean
  Sentry.OptionsConfiguration<SentryOptions> releaseName(
      @Value("${sentry.release}") String release) {
    // Container image paths are useful versions but Sentry forbids slashes in release names.
    return options -> options.setRelease(release.replace('/', '_'));
  }

  /** Backs off Sentry's own MVC resolver, which reports 4xx client errors as FATAL. */
  @Bean
  ServerErrorReporter serverErrorReporter() {
    return new ServerErrorReporter(
        ScopesAdapter.getInstance(), new SpringMvcTransactionNameProvider());
  }

  @Bean
  ServerErrorReportingFilter serverErrorReportingFilter(ServerErrorReporter reporter) {
    return new ServerErrorReportingFilter(reporter);
  }

  @Bean
  SentryOptions.BeforeSendCallback safeSentryEvent(
      @Value("${sentry.release}") String release,
      @Value("${sentry.environment}") String environment) {
    return (event, hint) -> {
      // Rebuild instead of denylisting: request, messages, scope and future fields are untrusted.
      hint.clearAttachments();
      Throwable error = event.getThrowable();
      if (error == null) return null;
      SentryEvent safe = new SentryEvent(event.getTimestamp());
      safe.setEventId(event.getEventId());
      safe.setRelease(release.replace('/', '_'));
      safe.setEnvironment(environment);
      safe.setLevel(event.getLevel());
      String id = MDC.get("correlationId");
      if (id != null && id.matches("[a-f0-9-]{36}")) safe.setTag("correlationId", id);
      SentryException exception = new SentryException();
      exception.setType(error.getClass().getName());
      var frames =
          Arrays.stream(error.getStackTrace())
              .limit(50)
              .map(
                  frame -> {
                    SentryStackFrame clean = new SentryStackFrame();
                    clean.setModule(frame.getClassName());
                    clean.setFunction(frame.getMethodName());
                    clean.setLineno(frame.getLineNumber());
                    return clean;
                  })
              .toList();
      exception.setStacktrace(new SentryStackTrace(frames.reversed()));
      safe.setExceptions(List.of(exception));
      return safe;
    };
  }
}
