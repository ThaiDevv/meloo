package com.meloo.catalog.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CatalogProviderConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CatalogProviderConfiguration.class));

  @Test
  @DisplayName("default configuration loads Deezer as active provider and disables iTunes")
  void defaultConfiguration_deezerActive() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("musicProvider");
          assertThat(context).hasBean("deezerMusicProvider");
          assertThat(context).hasBean("fakeMusicProvider");
          assertThat(context).doesNotHaveBean("itunesMusicProvider");

          MusicProvider primary = context.getBean(MusicProvider.class);
          assertThat(primary).isInstanceOf(CachedMusicProvider.class);
          CachedMusicProvider cached = (CachedMusicProvider) primary;
          assertThat(cached.getDelegate()).isInstanceOf(DeezerMusicProvider.class);
          assertThat(cached.getProviderName()).isEqualTo(DeezerMusicProvider.PROVIDER_NAME);
        });
  }

  @Test
  @DisplayName("when provider type is fake, FakeMusicProvider is active")
  void fakeConfiguration_fakeActive() {
    contextRunner
        .withPropertyValues("meloo.catalog.provider.type=fake")
        .run(
            context -> {
              assertThat(context).hasBean("musicProvider");
              MusicProvider primary = context.getBean(MusicProvider.class);
              assertThat(primary).isInstanceOf(CachedMusicProvider.class);
              CachedMusicProvider cached = (CachedMusicProvider) primary;
              assertThat(cached.getDelegate()).isInstanceOf(FakeMusicProvider.class);
              assertThat(cached.getProviderName()).isEqualTo(FakeMusicProvider.PROVIDER_NAME);
            });
  }

  @Test
  @DisplayName("when provider type is itunes and enabled is true, ItunesMusicProvider is active")
  void itunesConfiguration_itunesActive() {
    contextRunner
        .withPropertyValues(
            "meloo.catalog.provider.type=itunes", "meloo.catalog.provider.itunes.enabled=true")
        .run(
            context -> {
              assertThat(context).hasBean("musicProvider");
              assertThat(context).hasBean("itunesMusicProvider");

              MusicProvider primary = context.getBean(MusicProvider.class);
              assertThat(primary).isInstanceOf(CachedMusicProvider.class);
              CachedMusicProvider cached = (CachedMusicProvider) primary;
              assertThat(cached.getDelegate()).isInstanceOf(ItunesMusicProvider.class);
              assertThat(cached.getProviderName()).isEqualTo(ItunesMusicProvider.PROVIDER_NAME);
            });
  }

  @Test
  @DisplayName("when cache is disabled, active provider is not wrapped by CachedMusicProvider")
  void cacheDisabled_noCachedWrapper() {
    contextRunner
        .withPropertyValues(
            "meloo.catalog.provider.type=fake", "meloo.catalog.provider.cache.enabled=false")
        .run(
            context -> {
              assertThat(context).hasBean("musicProvider");
              MusicProvider primary = context.getBean(MusicProvider.class);
              assertThat(primary).isInstanceOf(FakeMusicProvider.class);
              assertThat(primary).isNotInstanceOf(CachedMusicProvider.class);
            });
  }
}
