package com.meloo.catalog.importing;

import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.MusicProvider;
import com.meloo.catalog.provider.ProviderTrack;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic health check job verifying track preview availability per BR-27. BR-27: Automatically
 * disables tracks with missing/broken preview so they do not appear in future matches.
 */
@Component
public class TrackPreviewHealthCheckJob {

  private static final Logger log = LoggerFactory.getLogger(TrackPreviewHealthCheckJob.class);

  private final TrackRepository trackRepository;
  private final MusicProvider musicProvider;
  private final EntityManager entityManager;

  public TrackPreviewHealthCheckJob(
      TrackRepository trackRepository, MusicProvider musicProvider, EntityManager entityManager) {
    this.trackRepository = trackRepository;
    this.musicProvider = musicProvider;
    this.entityManager = entityManager;
  }

  public record HealthCheckResult(int checkedCount, int healthyCount, int disabledCount) {}

  @Scheduled(cron = "${meloo.catalog.import.health-check.cron:0 0 3 * * *}")
  @ConditionalOnProperty(
      prefix = "meloo.catalog.import.health-check",
      name = "enabled",
      havingValue = "true")
  public void scheduledCheck() {
    log.info("Starting scheduled track preview health check per BR-27...");
    HealthCheckResult result = runHealthCheck();
    log.info(
        "Track preview health check finished: checked={}, healthy={}, disabled={}",
        result.checkedCount(),
        result.healthyCount(),
        result.disabledCount());
  }

  /** Scans active tracks and disables any track whose preview is missing or invalid. */
  @Transactional
  public HealthCheckResult runHealthCheck() {
    List<Track> enabledTracks =
        entityManager
            .createQuery("select t from Track t where t.enabled = true", Track.class)
            .getResultList();

    int checked = 0;
    int healthy = 0;
    int disabled = 0;

    for (Track track : enabledTracks) {
      checked++;
      boolean hasValidPreview = false;
      try {
        Optional<ProviderTrack> ptOpt = musicProvider.getTrack(track.getProviderTrackId());
        if (ptOpt.isPresent()) {
          ProviderTrack pt = ptOpt.get();
          if (pt.previewUrl() != null && !pt.previewUrl().isBlank()) {
            hasValidPreview = true;
          }
        }
      } catch (Exception e) {
        log.warn(
            "Error querying music provider for track {}: {}",
            track.getProviderTrackId(),
            e.getMessage());
      }

      if (hasValidPreview) {
        healthy++;
      } else {
        track.setEnabled(false);
        track.setDisabledReason("Preview unavailable: missing from provider or broken link");
        trackRepository.save(track);
        disabled++;
        log.warn(
            "Disabled track '{}' (id={}, pid={}) due to missing preview per BR-27",
            track.getTitle(),
            track.getId(),
            track.getProviderTrackId());
      }
    }

    return new HealthCheckResult(checked, healthy, disabled);
  }
}
