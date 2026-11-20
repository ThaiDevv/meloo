package com.meloo.shared.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Emits only operational metadata; arbitrary messages, arguments and stack traces may contain
 * answers.
 */
public final class SafeJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
  private static final Set<String> SAFE_TEMPLATES =
      Set.of(
          "Failed to resolve Deezer preview URL: {}",
          "Audio source unreachable for roundId {}",
          "Remote upstream audio fetch failed with status: {}",
          "Remote audio fetch failed: {}",
          "Unhandled request failure, correlationId={}, exceptionType={}");
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Override
  public String format(ILoggingEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
    fields.put("level", event.getLevel().toString());
    fields.put("logger", event.getLoggerName());
    fields.put("event", "backend_log");
    // Only exact, reviewed static templates; never interpolate arguments or log arbitrary text.
    if (event.getMessage() != null && SAFE_TEMPLATES.contains(event.getMessage()))
      fields.put("message", event.getMessage());
    String id = event.getMDCPropertyMap().get("correlationId");
    fields.put("correlationId", id != null && id.matches("[a-f0-9-]{36}") ? id : "unavailable");
    if (event.getThrowableProxy() != null) {
      fields.put("exceptionType", event.getThrowableProxy().getClassName());
    }
    return mapper.writeValueAsString(fields) + "\n";
  }
}
