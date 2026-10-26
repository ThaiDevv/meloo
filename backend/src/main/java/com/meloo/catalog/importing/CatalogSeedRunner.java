package com.meloo.catalog.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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

  public CatalogSeedRunner(CatalogSeedService catalogSeedService) {
    this.catalogSeedService = catalogSeedService;
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
    } catch (Exception e) {
      log.error("Catalog seed failed with error", e);
      throw e;
    }
  }
}
