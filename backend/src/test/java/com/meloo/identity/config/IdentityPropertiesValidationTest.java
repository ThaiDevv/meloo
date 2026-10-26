package com.meloo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityPropertiesValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IdentityProperties.class)
  static class TestConfig {}

  @Test
  @DisplayName("Compact constructor throws when secret is null or blank")
  void constructorRejectsBlankSecret() {
    assertThatThrownBy(
            () ->
                new IdentityProperties.JwtProperties(
                    null, Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");

    assertThatThrownBy(
            () ->
                new IdentityProperties.JwtProperties(
                    "   ", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  @DisplayName("Compact constructor throws when secret is shorter than 32 bytes (256 bits)")
  void constructorRejectsShortSecret() {
    String shortSecret = "only-20-chars-secret";
    assertThatThrownBy(
            () ->
                new IdentityProperties.JwtProperties(
                    shortSecret,
                    Duration.ofMinutes(15),
                    Duration.ofDays(30),
                    Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @Test
  @DisplayName("Compact constructor succeeds when secret is at least 32 bytes")
  void constructorAcceptsValidSecret() {
    String validSecret = "01234567890123456789012345678901"; // exactly 32 bytes
    IdentityProperties.JwtProperties jwt =
        new IdentityProperties.JwtProperties(
            validSecret, Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(10));

    assertThat(jwt.secret()).isEqualTo(validSecret);
    assertThat(jwt.rotationGrace()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("ApplicationContext fails to start when JWT secret is missing or blank")
  void contextFailsWhenSecretMissing() {
    contextRunner
        .withPropertyValues("meloo.identity.jwt.secret=")
        .run(
            context -> {
              assertThat(context).hasFailed();
            });
  }

  @Test
  @DisplayName("ApplicationContext fails to start when JWT secret is shorter than 32 bytes")
  void contextFailsWhenSecretIsTooShort() {
    contextRunner
        .withPropertyValues("meloo.identity.jwt.secret=too-short-secret")
        .run(
            context -> {
              assertThat(context).hasFailed();
            });
  }

  @Test
  @DisplayName("ApplicationContext starts successfully when valid 32+ byte secret is provided")
  void contextSucceedsWithValidSecret() {
    contextRunner
        .withPropertyValues(
            "meloo.identity.jwt.secret=01234567890123456789012345678901234567890123456789")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(IdentityProperties.class);
              IdentityProperties properties = context.getBean(IdentityProperties.class);
              assertThat(properties.jwt().rotationGrace()).isEqualTo(Duration.ofSeconds(10));
            });
  }
}
