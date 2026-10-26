package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CatalogImportPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(CatalogImportProperties.class)
  static class TestConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void shouldBindStandardRelaxedEnvironmentVariableNames() {
    // Test Spring Boot standard relaxed binding for environment variables
    // Standard rule: replace dots and dashes with underscores, uppercase
    runner
        .withPropertyValues(
            "meloo.catalog.health-check.enabled=true",
            "meloo.catalog.health-check.cron=0 0 4 * * *",
            "meloo.catalog.health-check.batch-size=50",
            "meloo.catalog.seed.enabled=true",
            "meloo.catalog.seed.seed-path=classpath:custom-seed.json",
            "meloo.catalog.seed.exit-after-seed=true")
        .run(
            context -> {
              CatalogImportProperties props = context.getBean(CatalogImportProperties.class);
              assertThat(props.healthCheck().enabled()).isTrue();
              assertThat(props.healthCheck().cron()).isEqualTo("0 0 4 * * *");
              assertThat(props.healthCheck().batchSize()).isEqualTo(50);
              assertThat(props.seed().enabled()).isTrue();
              assertThat(props.seed().seedPath()).isEqualTo("classpath:custom-seed.json");
              assertThat(props.seed().exitAfterSeed()).isTrue();
            });
  }

  @Test
  void shouldBindWithoutDashesInEnvironmentVariables() {
    // Spring Boot environment variable: MELOO_CATALOG_HEALTHCHECK_ENABLED
    // maps to meloo.catalog.healthcheck.enabled which binds to healthCheck.enabled
    runner
        .withPropertyValues(
            "meloo.catalog.healthcheck.enabled=true",
            "meloo.catalog.healthcheck.cron=0 0 2 * * *",
            "meloo.catalog.healthcheck.batchsize=75",
            "meloo.catalog.seed.seedpath=classpath:test.json")
        .run(
            context -> {
              CatalogImportProperties props = context.getBean(CatalogImportProperties.class);
              assertThat(props.healthCheck().enabled()).isTrue();
              assertThat(props.healthCheck().cron()).isEqualTo("0 0 2 * * *");
              assertThat(props.healthCheck().batchSize()).isEqualTo(75);
              assertThat(props.seed().seedPath()).isEqualTo("classpath:test.json");
            });
  }
}
