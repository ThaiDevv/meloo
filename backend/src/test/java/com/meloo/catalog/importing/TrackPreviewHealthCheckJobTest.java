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
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
    assertThat(reloadedHealthy.getLastPreviewCheckedAt()).isNotNull();

    Track reloadedBroken = trackRepository.findById(missingPreviewTrack.getId()).orElseThrow();
    assertThat(reloadedBroken.isEnabled()).isFalse();
    assertThat(reloadedBroken.getDisabledReason()).contains("Preview unavailable");
    assertThat(reloadedBroken.getLastPreviewCheckedAt()).isNotNull();

    Track reloadedNotFound = trackRepository.findById(notFoundTrack.getId()).orElseThrow();
    assertThat(reloadedNotFound.isEnabled()).isFalse();
    assertThat(reloadedNotFound.getDisabledReason()).contains("Preview unavailable");
    assertThat(reloadedNotFound.getLastPreviewCheckedAt()).isNotNull();
  }

  @Test
  @DisplayName(
      "BR-27 / N2: Round-robin scanning prioritizes uninspected tracks (nulls first) and updates check timestamp")
  void healthCheckJob_roundRobinScanning_prioritizesUncheckedTracks() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    Artist artist =
        artistRepository.save(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Round Robin Artist")
                .nameNormalized("round robin artist")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-rr-1")
                .build());

    Track alreadyChecked =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-rr-already-checked")
                .title("Already Checked Track")
                .titleNormalized("already checked track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .lastPreviewCheckedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(7)))
                .build());

    Track neverChecked =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("dz-rr-never-checked")
                .title("Never Checked Track")
                .titleNormalized("never checked track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .lastPreviewCheckedAt(null)
                .build());

    when(musicProvider.getTrack("dz-rr-never-checked"))
        .thenReturn(
            Optional.of(
                ProviderTrack.builder()
                    .providerTrackId("dz-rr-never-checked")
                    .title("Never Checked Track")
                    .artistName("Round Robin Artist")
                    .previewUrl("https://cdns.dzcdn.net/preview-rr.mp3")
                    .build()));

    when(musicProvider.getTrack("dz-rr-already-checked"))
        .thenReturn(
            Optional.of(
                ProviderTrack.builder()
                    .providerTrackId("dz-rr-already-checked")
                    .title("Already Checked Track")
                    .artistName("Round Robin Artist")
                    .previewUrl("https://cdns.dzcdn.net/preview-already.mp3")
                    .build()));

    healthCheckJob.runHealthCheck();

    Track reloadedNeverChecked = trackRepository.findById(neverChecked.getId()).orElseThrow();
    assertThat(reloadedNeverChecked.getLastPreviewCheckedAt())
        .as("Track with null timestamp must be updated after health check")
        .isNotNull();
    assertThat(reloadedNeverChecked.getLastPreviewCheckedAt())
        .isAfter(alreadyChecked.getLastPreviewCheckedAt());
  }

  @Test
  @DisplayName(
      "BR-27 / m3: scheduledCheck respects disabled configuration flag without running inspection")
  void scheduledCheck_whenDisabled_doesNotCheckTracks() {
    CatalogImportProperties disabledProps =
        new CatalogImportProperties(
            new CatalogImportProperties.SeedProperties(
                false, "classpath:seed/catalog-seed.json", false),
            new CatalogImportProperties.HealthCheckProperties(false, "0 0 3 * * *", 100));

    TrackPreviewHealthCheckJob disabledJob =
        new TrackPreviewHealthCheckJob(
            trackRepository, musicProvider, entityManager, transactionTemplate, disabledProps);

    // Should return early and not throw or check
    disabledJob.scheduledCheck();
  }

  @Test
  @DisplayName(
      "BR-27 / m5: Tracks belonging to other inactive providers update check timestamp so they do not starve queue")
  void healthCheckJob_otherProviderTrack_updatesTimestampAndDoesNotBlockQueue() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    Artist artist =
        artistRepository.save(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("iTunes Artist")
                .nameNormalized("itunes artist")
                .provider(MusicProvider.ITUNES)
                .providerArtistId("itunes-art-1")
                .build());

    Track itunesTrack =
        trackRepository.save(
            Track.builder()
                .id(UUID.randomUUID())
                .provider(MusicProvider.ITUNES)
                .providerTrackId("itunes-track-1")
                .title("iTunes Exclusive Track")
                .titleNormalized("itunes exclusive track")
                .primaryArtistId(artist.getId())
                .enabled(true)
                .lastPreviewCheckedAt(null)
                .build());

    healthCheckJob.runHealthCheck();

    Track reloaded = trackRepository.findById(itunesTrack.getId()).orElseThrow();
    assertThat(reloaded.getLastPreviewCheckedAt())
        .as(
            "Track belonging to another provider must update check timestamp to avoid starving the batch")
        .isNotNull();
    assertThat(reloaded.isEnabled()).isTrue();
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
