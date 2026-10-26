package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class CatalogSeedRunnerTest {

  @Test
  @DisplayName(
      "CatalogSeedRunner executes seedCatalog without exiting when exit-after-seed is false")
  void run_whenExitDisabled_doesNotExit() {
    CatalogSeedService seedService = mock(CatalogSeedService.class);
    when(seedService.seedCatalog())
        .thenReturn(new CatalogSeedService.CatalogSeedResult(8, 8, 100, 200));

    CatalogImportProperties properties =
        new CatalogImportProperties(
            new CatalogImportProperties.SeedProperties(
                true, "classpath:seed/catalog-seed.json", false),
            new CatalogImportProperties.HealthCheckProperties(false, "0 0 3 * * *", 100));

    ApplicationContext context = mock(ApplicationContext.class);
    AtomicInteger exitCode = new AtomicInteger(-1);

    CatalogSeedRunner runner =
        new CatalogSeedRunner(seedService, properties, context, exitCode::set);

    runner.run(new DefaultApplicationArguments());

    verify(seedService).seedCatalog();
    assertThat(exitCode.get()).isEqualTo(-1);
  }

  @Test
  @DisplayName(
      "CatalogSeedRunner calls SpringApplication.exit with code 0 when exit-after-seed is true")
  void run_whenExitEnabled_exitsWithCodeZero() throws InterruptedException {
    CatalogSeedService seedService = mock(CatalogSeedService.class);
    when(seedService.seedCatalog())
        .thenReturn(new CatalogSeedService.CatalogSeedResult(8, 8, 100, 200));

    CatalogImportProperties properties =
        new CatalogImportProperties(
            new CatalogImportProperties.SeedProperties(
                true, "classpath:seed/catalog-seed.json", true),
            new CatalogImportProperties.HealthCheckProperties(false, "0 0 3 * * *", 100));

    ApplicationContext context = mock(ApplicationContext.class);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger exitCode = new AtomicInteger(-1);

    CatalogSeedRunner runner =
        new CatalogSeedRunner(
            seedService,
            properties,
            context,
            code -> {
              exitCode.set(code);
              latch.countDown();
            });

    runner.run(new DefaultApplicationArguments());

    verify(seedService).seedCatalog();
    boolean exited = latch.await(3, TimeUnit.SECONDS);
    assertThat(exited).isTrue();
    assertThat(exitCode.get()).isEqualTo(0);
  }
}
