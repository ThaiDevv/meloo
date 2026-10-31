package com.meloo.game.solo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SoloPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SoloProperties.class)
  static class TestConfig {}

  @Test
  @DisplayName(
      "Default properties provide recommended limits: 10/min create match and 60/min actions")
  void defaultValuesProvideRecommendedLimits() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          SoloProperties properties = context.getBean(SoloProperties.class);
          assertThat(properties.rateLimit()).isNotNull();
          assertThat(properties.rateLimit().createMatchCapacity()).isEqualTo(10);
          assertThat(properties.rateLimit().createMatchRefillTokens()).isEqualTo(10);
          assertThat(properties.rateLimit().createMatchRefillDuration())
              .isEqualTo(Duration.ofMinutes(1));
          assertThat(properties.rateLimit().actionCapacity()).isEqualTo(60);
          assertThat(properties.rateLimit().actionRefillTokens()).isEqualTo(60);
          assertThat(properties.rateLimit().actionRefillDuration())
              .isEqualTo(Duration.ofMinutes(1));
        });
  }

  @Test
  @DisplayName("Rejects non-positive or null rate limit properties in compact constructor")
  void constructorRejectsInvalidProperties() {
    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(
                    0, 10, Duration.ofMinutes(1), 60, 60, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createMatchCapacity");

    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(
                    10, 0, Duration.ofMinutes(1), 60, 60, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createMatchRefillTokens");

    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(
                    10, 10, Duration.ZERO, 60, 60, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createMatchRefillDuration");

    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(
                    10, 10, Duration.ofMinutes(1), -1, 60, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actionCapacity");

    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(
                    10, 10, Duration.ofMinutes(1), 60, -5, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actionRefillTokens");

    assertThatThrownBy(
            () ->
                new SoloProperties.RateLimitProperties(10, 10, Duration.ofMinutes(1), 60, 60, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actionRefillDuration");
  }

  @Test
  @DisplayName("ApplicationContextRunner binds custom properties correctly")
  void bindsCustomProperties() {
    contextRunner
        .withPropertyValues(
            "meloo.game.rate-limit.create-match-capacity=5",
            "meloo.game.rate-limit.create-match-refill-tokens=5",
            "meloo.game.rate-limit.create-match-refill-duration=30s",
            "meloo.game.rate-limit.action-capacity=30",
            "meloo.game.rate-limit.action-refill-tokens=30",
            "meloo.game.rate-limit.action-refill-duration=30s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              SoloProperties properties = context.getBean(SoloProperties.class);
              assertThat(properties.rateLimit().createMatchCapacity()).isEqualTo(5);
              assertThat(properties.rateLimit().createMatchRefillTokens()).isEqualTo(5);
              assertThat(properties.rateLimit().createMatchRefillDuration())
                  .isEqualTo(Duration.ofSeconds(30));
              assertThat(properties.rateLimit().actionCapacity()).isEqualTo(30);
              assertThat(properties.rateLimit().actionRefillTokens()).isEqualTo(30);
              assertThat(properties.rateLimit().actionRefillDuration())
                  .isEqualTo(Duration.ofSeconds(30));
            });
  }

  @Test
  @DisplayName("ApplicationContextRunner fails on invalid configuration")
  void failsOnInvalidConfiguration() {
    contextRunner
        .withPropertyValues("meloo.game.rate-limit.create-match-capacity=-1")
        .run(
            context -> {
              assertThat(context).hasFailed();
            });
  }
}
