package com.meloo.audio.config;

import com.meloo.audio.domain.AudioTokenCodec;
import com.meloo.audio.service.AccountLockChecker;
import com.meloo.audio.service.AudioRateLimiter;
import com.meloo.audio.service.AudioSourceResolver;
import com.meloo.audio.service.AudioStreamService;
import com.meloo.audio.service.DeezerAudioSourceResolver;
import com.meloo.audio.service.FakeAudioSourceResolver;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AudioProperties.class)
public class AudioConfiguration {

  @Bean
  @ConditionalOnMissingBean(AccountLockChecker.class)
  public AccountLockChecker accountLockChecker() {
    return playerId -> false;
  }

  @Bean
  @ConditionalOnMissingBean(AudioSourceResolver.class)
  public AudioSourceResolver audioSourceResolver(
      AudioProperties properties,
      RestClient.Builder restClientBuilder,
      ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistry,
      ObjectProvider<RetryRegistry> retryRegistry) {
    var fakeResolver = new FakeAudioSourceResolver(properties.provider().fake().mediaLocation());
    if ("fake".equalsIgnoreCase(properties.provider().type())) {
      return fakeResolver;
    }
    var deezerResolver =
        new DeezerAudioSourceResolver(
            properties.provider().deezer(),
            restClientBuilder,
            circuitBreakerRegistry.getIfAvailable(),
            retryRegistry.getIfAvailable());

    return (trackId, provider, providerTrackId) -> {
      if ("fake".equalsIgnoreCase(provider)) {
        return fakeResolver.resolvePreviewUrl(trackId, provider, providerTrackId);
      }
      return deezerResolver.resolvePreviewUrl(trackId, provider, providerTrackId);
    };
  }

  @Bean
  @ConditionalOnMissingBean(AudioTokenCodec.class)
  public AudioTokenCodec audioTokenCodec(
      AudioProperties properties, ObjectProvider<Clock> clockProvider) {
    return new AudioTokenCodec(
        properties.hmacSecret(), clockProvider.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnMissingBean(AudioRateLimiter.class)
  public AudioRateLimiter audioRateLimiter(AudioProperties properties) {
    return new AudioRateLimiter(properties.rateLimit());
  }

  @Bean
  @ConditionalOnMissingBean(AudioStreamService.class)
  public AudioStreamService audioStreamService(
      AudioProperties properties,
      AudioSourceResolver sourceResolver,
      ResourceLoader resourceLoader,
      RestClient.Builder restClientBuilder) {
    return new AudioStreamService(properties, sourceResolver, resourceLoader, restClientBuilder);
  }
}
