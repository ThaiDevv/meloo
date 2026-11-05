package com.meloo.room.match;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meloo.room.match")
public record RoomMatchProperties(
    @NotNull Duration prepareTimeout,
    @NotNull Duration roundDuration,
    @NotNull Duration timeoutBuffer,
    @NotNull Duration revealDuration,
    @NotNull Duration podiumDuration) {
  public RoomMatchProperties {
    for (Duration duration :
        new Duration[] {
          prepareTimeout, roundDuration, timeoutBuffer, revealDuration, podiumDuration
        }) {
      if (duration == null
          || duration.isNegative()
          || duration.isZero()
          || duration.toMillis() < 1
          || duration.toMillis() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "Match durations must be positive milliseconds within int range");
      }
    }
  }
}
