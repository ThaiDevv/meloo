package com.meloo.room.realtime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "meloo.room.realtime")
@Validated
public record RoomRealtimeProperties(
    @NotNull Duration heartbeatInterval, @Min(1) int schedulerPoolSize) {

  public RoomRealtimeProperties {
    if (heartbeatInterval == null
        || heartbeatInterval.isZero()
        || heartbeatInterval.isNegative()
        || heartbeatInterval.toMillis() < 1) {
      throw new IllegalArgumentException("Realtime heartbeat interval must be positive");
    }
    if (schedulerPoolSize < 1) {
      throw new IllegalArgumentException("Realtime scheduler pool size must be positive");
    }
  }
}
