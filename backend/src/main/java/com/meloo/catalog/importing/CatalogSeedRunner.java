package com.meloo.catalog.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Single-run catalog seeder executed on application startup when enabled by configuration. Enables
 * one-off seed in local dev or on production without needing an admin HTTP endpoint (since ADMIN
 * accounts are introduced in S4/BE-ID-02).
 */
@Component
@ConditionalOnExpression(
    "${meloo.catalog.import.seed.enabled:false} or ${meloo.catalog.seed.enabled:false}")
public class CatalogSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CatalogSeedRunner.class);

  private final CatalogSeedService catalogSeedService;
  private final ApplicationContext applicationContext;
  private final Environment environment;

  public CatalogSeedRunner(
      CatalogSeedService catalogSeedService,
      ApplicationContext applicationContext,
      Environment environment) {
    this.catalogSeedService = catalogSeedService;
    this.applicationContext = applicationContext;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("Starting one-off catalog seed runner...");
    try {
      CatalogSeedService.CatalogSeedResult result = catalogSeedService.seedCatalog();
      log.info(
          "One-off catalog seed finished successfully: {} genres, {} playlists, {} tracks seeded, {} playlist links",
          result.genresCount(),
          result.playlistsCount(),
          result.tracksCount(),
          result.playlistTracksCount());

      boolean exitAfterSeed =
          Boolean.parseBoolean(
                  environment.getProperty("meloo.catalog.seed.exit-after-seed", "false"))
              || args.containsOption("meloo.catalog.seed.exit-after-seed")
              || args.containsOption("exit-after-seed");

      if (exitAfterSeed) {
        log.info(
            "One-off seed completed and exit-after-seed is enabled. Shutting down application...");
        new Thread(
                () -> {
                  int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                  System.exit(exitCode);
                },
                "seed-shutdown")
            .start();
      }
    } catch (Exception e) {
      log.error("Catalog seed failed with error", e);
      throw e;
    }
  }
}
