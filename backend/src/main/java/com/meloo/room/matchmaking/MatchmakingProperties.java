package com.meloo.room.matchmaking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meloo.room.matchmaking")
public record MatchmakingProperties(Duration countdown, Duration soloSuggestionDelay) {
  public MatchmakingProperties {
    for (Duration value : new Duration[] {countdown, soloSuggestionDelay}) {
      if (value == null || value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "Matchmaking durations must be positive within int range");
      }
    }
  }
}
