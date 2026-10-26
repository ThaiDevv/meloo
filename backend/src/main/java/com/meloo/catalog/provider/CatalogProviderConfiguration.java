package com.meloo.catalog.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogProviderProperties.class)
public class CatalogProviderConfiguration {

  // User-defined registries that take precedence over resilience4j-spring-boot4 auto-configuration.
  // Provider resilience settings are loaded programmatically under meloo.catalog.provider.* rather
  // than resilience4j.* in application.yml.
  // When BE-CAT-02 or other modules need shared YAML configuration via resilience4j.*, remove these
  // beans
  // and let resilience4j-spring-boot4 auto-configuration provide them.
  @Bean
  @ConditionalOnMissingBean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    return CircuitBreakerRegistry.ofDefaults();
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryRegistry retryRegistry() {
    return RetryRegistry.ofDefaults();
  }

  @Bean
  @ConditionalOnMissingBean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  @Bean("deezerMusicProvider")
  @ConditionalOnMissingBean(name = "deezerMusicProvider")
  public DeezerMusicProvider deezerMusicProvider(
      CatalogProviderProperties properties,
      RestClient.Builder restClientBuilder,
      CircuitBreakerRegistry circuitBreakerRegistry,
      RetryRegistry retryRegistry) {
    return new DeezerMusicProvider(
        properties, restClientBuilder, circuitBreakerRegistry, retryRegistry);
  }

  @Bean("itunesMusicProvider")
  @ConditionalOnExpression(
      "${meloo.catalog.provider.itunes.enabled:false} or '${meloo.catalog.provider.type:deezer}'.equalsIgnoreCase('itunes')")
  public ItunesMusicProvider itunesMusicProvider(
      CatalogProviderProperties properties,
      RestClient.Builder restClientBuilder,
      CircuitBreakerRegistry circuitBreakerRegistry,
      RetryRegistry retryRegistry) {
    return new ItunesMusicProvider(
        properties, restClientBuilder, circuitBreakerRegistry, retryRegistry);
  }

  @Bean("fakeMusicProvider")
  @ConditionalOnMissingBean(name = "fakeMusicProvider")
  public FakeMusicProvider fakeMusicProvider(CatalogProviderProperties properties) {
    return new FakeMusicProvider(properties.fake().mediaLocation());
  }

  @Bean
  @Primary
  public MusicProvider musicProvider(
      CatalogProviderProperties properties,
      ObjectProvider<DeezerMusicProvider> deezerProvider,
      ObjectProvider<ItunesMusicProvider> itunesProvider,
      ObjectProvider<FakeMusicProvider> fakeProvider) {

    String type = properties.type().toLowerCase(Locale.ROOT);
    MusicProvider activeProvider =
        switch (type) {
          case "itunes" ->
              itunesProvider.getIfAvailable(
                  () -> {
                    throw new IllegalStateException(
                        "iTunes music provider is selected as active provider but is not available.");
                  });
          case "fake" -> fakeProvider.getIfAvailable(FakeMusicProvider::new);
          case "deezer" ->
              deezerProvider.getIfAvailable(
                  () -> {
                    throw new IllegalStateException(
                        "Deezer music provider is selected as active provider but is not available.");
                  });
          default ->
              throw new IllegalArgumentException("Unknown music provider type configured: " + type);
        };

    if (properties.cache().enabled()) {
      return new CachedMusicProvider(activeProvider, properties);
    }
    return activeProvider;
  }
}
