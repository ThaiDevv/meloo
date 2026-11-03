package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.TrackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

class CatalogSeedRunnerIntegrationTest {

  @Nested
  @SpringBootTest(properties = "meloo.catalog.seed.enabled=true")
  @DisplayName("When meloo.catalog.seed.enabled=true")
  class SeedEnabledTest extends IntegrationTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private TrackRepository trackRepository;
    @Autowired private PlaylistRepository playlistRepository;

    @Test
    @DisplayName("CatalogSeedRunner bean is created and seeds tracks and playlists")
    void runnerBeanCreated_andSeedsData() {
      assertThat(applicationContext.containsBean("catalogSeedRunner")).isTrue();
      assertThat(applicationContext.getBean(CatalogSeedRunner.class)).isNotNull();
      assertThat(trackRepository.count()).isGreaterThanOrEqualTo(1000);
      assertThat(playlistRepository.count()).isGreaterThanOrEqualTo(5);
    }
  }

  @Nested
  @SpringBootTest(properties = "meloo.catalog.seed.enabled=false")
  @DisplayName("When meloo.catalog.seed.enabled=false")
  class SeedDisabledTest extends IntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    @DisplayName("CatalogSeedRunner bean is not created when seed is disabled")
    void runnerBeanNotCreated() {
      assertThat(applicationContext.containsBean("catalogSeedRunner")).isFalse();
    }
  }
}
