package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.MusicProvider;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.MusicProviderRateLimitException;
import com.meloo.catalog.provider.ProviderTrack;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class TrackPreviewHealthCheckJobTest extends IntegrationTest {

  @Autowired private TrackPreviewHealthCheckJob healthCheckJob;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;

  @MockitoBean private com.meloo.catalog.provider.MusicProvider musicProvider;

  @Test
  @DisplayName("BR-27: Health check job automatically disables tracks with invalid/broken preview")
  void healthCheckJob_disablesBrokenTracks() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    Artist artist =
        artistRepository.save(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Test Artist")
                .nameNormalized("test artist")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-hc-1")
                .build());

    Track healthyTrack =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-hc-healthy")
                .title("Healthy Track")
                .titleNormalized("healthy track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .build());

    Track missingPreviewTrack =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-hc-nopreview")
                .title("Broken Preview Track")
                .titleNormalized("broken preview track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .build());

    Track notFoundTrack =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-hc-notfound")
                .title("Not Found Track")
                .titleNormalized("not found track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .build());

    // Mock provider behavior
    when(musicProvider.getTrack("dz-hc-healthy"))
        .thenReturn(
            Optional.of(
                ProviderTrack.builder()
                    .providerTrackId("dz-hc-healthy")
                    .title("Healthy Track")
                    .artistName("Test Artist")
                    .previewUrl("https://cdns.dzcdn.net/preview-healthy.mp3")
                    .build()));

    when(musicProvider.getTrack("dz-hc-nopreview"))
        .thenReturn(
            Optional.of(
                ProviderTrack.builder()
                    .providerTrackId("dz-hc-nopreview")
                    .title("Broken Preview Track")
                    .artistName("Test Artist")
                    .previewUrl("") // blank preview!
                    .build()));

    when(musicProvider.getTrack("dz-hc-notfound"))
        .thenReturn(Optional.empty()); // track removed from provider!

    TrackPreviewHealthCheckJob.HealthCheckResult result = healthCheckJob.runHealthCheck();

    assertThat(result.checkedCount()).isGreaterThanOrEqualTo(3);
    assertThat(result.disabledCount()).isGreaterThanOrEqualTo(2);

    Track reloadedHealthy = trackRepository.findById(healthyTrack.getId()).orElseThrow();
    assertThat(reloadedHealthy.isEnabled()).isTrue();
    assertThat(reloadedHealthy.getDisabledReason()).isNull();

    Track reloadedBroken = trackRepository.findById(missingPreviewTrack.getId()).orElseThrow();
    assertThat(reloadedBroken.isEnabled()).isFalse();
    assertThat(reloadedBroken.getDisabledReason()).contains("Preview unavailable");

    Track reloadedNotFound = trackRepository.findById(notFoundTrack.getId()).orElseThrow();
    assertThat(reloadedNotFound.isEnabled()).isFalse();
    assertThat(reloadedNotFound.getDisabledReason()).contains("Preview unavailable");
  }

  @Test
  @DisplayName("BR-27 / M1: Transient errors (429 rate limit, 5xx) do NOT disable tracks")
  void healthCheckJob_transientErrors_doNotDisableTracks() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    Artist artist =
        artistRepository.save(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Transient Artist")
                .nameNormalized("transient artist")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-hc-transient")
                .build());

    Track rateLimitedTrack =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-hc-ratelimit")
                .title("Rate Limited Track")
                .titleNormalized("rate limited track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .build());

    when(musicProvider.getTrack("dz-hc-ratelimit"))
        .thenThrow(new MusicProviderRateLimitException("HTTP 429 Too Many Requests"));

    TrackPreviewHealthCheckJob.HealthCheckResult result = healthCheckJob.runHealthCheck();

    assertThat(result.transientErrorCount()).isGreaterThanOrEqualTo(1);

    // Track must remain ENABLED despite rate limit error
    Track reloaded = trackRepository.findById(rateLimitedTrack.getId()).orElseThrow();
    assertThat(reloaded.isEnabled()).isTrue();
    assertThat(reloaded.getDisabledReason()).isNull();
  }
}
