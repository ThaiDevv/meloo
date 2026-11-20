package com.meloo.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.meloo.shared.web.CorrelationIdFilter;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.JsonSerializer;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class MonitoringTest {
  private static final String SECRET = "answer-JWT-audio-refresh-secret";
  private final SentryOptions.BeforeSendCallback callback =
      new MonitoringConfiguration().safeSentryEvent("sha-1234567", "production");

  @Test
  void sentryRebuildsEventWithoutSensitiveFieldsAndKeepsReleaseAndCodeLocation() throws Exception {
    SentryEvent event = new SentryEvent(new IllegalStateException(SECRET));
    event.setExtra("nested", Map.of("answer", SECRET));
    event.setTag("token", SECRET);
    event.getContexts().put("game", Map.of("answer", SECRET));
    Hint hint = new Hint();
    hint.addAttachment(new Attachment(SECRET.getBytes(), "secret.txt"));
    SentryEvent safe;
    MDC.put("correlationId", SECRET);
    try {
      safe = callback.execute(event, hint);
    } finally {
      MDC.clear();
    }
    StringWriter json = new StringWriter();
    new JsonSerializer(new SentryOptions()).serialize(safe, json);
    assertThat(json.toString()).doesNotContain(SECRET);
    assertThat(safe.getExceptions().getFirst().getType())
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(safe.getExceptions().getFirst().getStacktrace().getFrames()).isNotEmpty();
    assertThat(hint.getAttachments()).isEmpty();
  }

  @Test
  void jsonLogAndSentryShareValidatedRequestIdAndCleanUpAfterRequest() throws Exception {
    String id = "12345678-1234-1234-1234-123456789abc";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", id);
    MockHttpServletResponse response = new MockHttpServletResponse();
    new CorrelationIdFilter()
        .doFilter(
            request,
            response,
            (req, res) -> {
              LoggingEvent event = logEvent("Failed to resolve Deezer preview URL: {}");
              MDC.put("token", SECRET);
              event.prepareForDeferredProcessing();
              String json = new SafeJsonLogFormatter().format(event);
              assertThat(json)
                  .contains("Failed to resolve Deezer preview URL")
                  .doesNotContain(SECRET)
                  .endsWith("\n");
              var fields = JsonMapper.builder().build().readTree(json);
              assertThat(fields.get("correlationId").asString()).isEqualTo(id);
              assertThat(fields.get("level").asString()).isEqualTo("ERROR");
              assertThat(
                      callback
                          .execute(new SentryEvent(new IllegalStateException(SECRET)), new Hint())
                          .getTag("correlationId"))
                  .isEqualTo(id);
              MDC.remove("token");
            });
    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(id);
    assertThat(MDC.get("correlationId")).isNull();
    assertThat(new SafeJsonLogFormatter().format(logEvent(SECRET)))
        .contains("unavailable")
        .doesNotContain(SECRET);
  }

  private LoggingEvent logEvent(String message) {
    return new LoggingEvent(
        getClass().getName(),
        (Logger) LoggerFactory.getLogger(getClass()),
        Level.ERROR,
        message,
        new IllegalArgumentException(SECRET),
        new Object[] {SECRET});
  }
}
