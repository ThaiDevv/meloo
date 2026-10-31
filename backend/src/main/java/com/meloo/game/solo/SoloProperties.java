package com.meloo.game.solo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meloo.game")
public record SoloProperties(@Valid @NotNull @DefaultValue RateLimitProperties rateLimit) {

  public record RateLimitProperties(
      @Positive int createMatchCapacity,
      @Positive int createMatchRefillTokens,
      @NotNull Duration createMatchRefillDuration,
      @Positive int actionCapacity,
      @Positive int actionRefillTokens,
      @NotNull Duration actionRefillDuration) {

    public RateLimitProperties(
        @DefaultValue("10") int createMatchCapacity,
        @DefaultValue("10") int createMatchRefillTokens,
        @DefaultValue("1m") Duration createMatchRefillDuration,
        @DefaultValue("60") int actionCapacity,
        @DefaultValue("60") int actionRefillTokens,
        @DefaultValue("1m") Duration actionRefillDuration) {
      if (createMatchCapacity <= 0) {
        throw new IllegalArgumentException("createMatchCapacity must be greater than 0");
      }
      if (createMatchRefillTokens <= 0) {
        throw new IllegalArgumentException("createMatchRefillTokens must be greater than 0");
      }
      if (createMatchRefillDuration == null
          || createMatchRefillDuration.isNegative()
          || createMatchRefillDuration.isZero()) {
        throw new IllegalArgumentException("createMatchRefillDuration must be positive");
      }
      if (actionCapacity <= 0) {
        throw new IllegalArgumentException("actionCapacity must be greater than 0");
      }
      if (actionRefillTokens <= 0) {
        throw new IllegalArgumentException("actionRefillTokens must be greater than 0");
      }
      if (actionRefillDuration == null
          || actionRefillDuration.isNegative()
          || actionRefillDuration.isZero()) {
        throw new IllegalArgumentException("actionRefillDuration must be positive");
      }
      this.createMatchCapacity = createMatchCapacity;
      this.createMatchRefillTokens = createMatchRefillTokens;
      this.createMatchRefillDuration = createMatchRefillDuration;
      this.actionCapacity = actionCapacity;
      this.actionRefillTokens = actionRefillTokens;
      this.actionRefillDuration = actionRefillDuration;
    }
  }
}
