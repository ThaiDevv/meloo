package com.meloo.catalog.importing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meloo.catalog.import")
public record CatalogImportProperties(
    @DefaultValue("classpath:seed/catalog-seed.json") String seedPath,
    @DefaultValue SeedProperties seed,
    @DefaultValue HealthCheckProperties healthCheck) {

  public record SeedProperties(
      @DefaultValue("false") boolean enabled, @DefaultValue("false") boolean exitAfterSeed) {}

  public record HealthCheckProperties(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("0 0 3 * * *") String cron,
      @DefaultValue("100") int batchSize) {}
}
