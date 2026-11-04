package com.meloo.chat.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "meloo.chat")
@Validated
public record ChatProperties(
    @NotNull @DefaultValue("1s") Duration rateLimitInterval,
    @Min(1) @DefaultValue("200") int maxMessageLength,
    @NotNull @DefaultValue("5m") Duration bannedWordsCacheTtl,
    @Min(5) @DefaultValue("50") int maxHistoryPerRoom) {

  public ChatProperties {
    if (rateLimitInterval == null || rateLimitInterval.isNegative() || rateLimitInterval.isZero()) {
      throw new IllegalArgumentException("Rate limit interval must be positive");
    }
    if (maxMessageLength < 1) {
      throw new IllegalArgumentException("Max message length must be at least 1");
    }
    if (bannedWordsCacheTtl == null
        || bannedWordsCacheTtl.isNegative()
        || bannedWordsCacheTtl.isZero()) {
      throw new IllegalArgumentException("Banned words cache TTL must be positive");
    }
    if (maxHistoryPerRoom < 5) {
      throw new IllegalArgumentException("Max history per room must be at least 5");
    }
  }
}
