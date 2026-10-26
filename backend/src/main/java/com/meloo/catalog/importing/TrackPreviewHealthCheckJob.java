package com.meloo.catalog.importing;

import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.MusicProviderException;
import com.meloo.catalog.provider.MusicProviderRateLimitException;
import com.meloo.catalog.provider.ProviderTrack;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Periodic health check job verifying track preview availability per BR-27. BR-27: Automatically
 * disables tracks with missing/broken preview so they do not appear in future matches.
 *
 * <p>Resilience & Transaction rules: - HTTP calls to external provider are performed OUTSIDE
 * database transactions. - Transient errors (HTTP 429 rate limit, 5xx, timeout, circuit breaker
 * open) do NOT disable tracks. - Only confirmed missing previews (provider returns 404/empty or
 * null/blank previewUrl) disable tracks. - Batch size throttling avoids overwhelming external APIs.
 */
@Component
public class TrackPreviewHealthCheckJob {

  private static final Logger log = LoggerFactory.getLogger(TrackPreviewHealthCheckJob.class);

  private final TrackRepository trackRepository;
  private final com.meloo.catalog.provider.MusicProvider musicProvider;
  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;
  private final CatalogImportProperties properties;

  public TrackPreviewHealthCheckJob(
      TrackRepository trackRepository,
      com.meloo.catalog.provider.MusicProvider musicProvider,
      EntityManager entityManager,
      TransactionTemplate transactionTemplate,
      CatalogImportProperties properties) {
    this.trackRepository = trackRepository;
    this.musicProvider = musicProvider;
    this.entityManager = entityManager;
    this.transactionTemplate = transactionTemplate;
    this.properties = properties;
  }

  public record HealthCheckResult(
      int checkedCount,
      int healthyCount,
      int disabledCount,
      int transientErrorCount,
      int skippedProviderCount) {
    public HealthCheckResult(
        int checkedCount, int healthyCount, int disabledCount, int transientErrorCount) {
      this(checkedCount, healthyCount, disabledCount, transientErrorCount, 0);
    }
  }

  @Scheduled(
      cron =
          "${meloo.catalog.health-check.cron:${meloo.catalog.import.health-check.cron:0 0 3 * * *}}")
  public void scheduledCheck() {
    // Explicit guard: verify scheduling is enabled before running
    if (!properties.healthCheck().enabled()) {
      log.debug("Catalog preview health check is disabled, skipping scheduled execution");
      return;
    }

    log.info("Starting scheduled track preview health check per BR-27...");
    HealthCheckResult result = runHealthCheck();
    log.info(
        "Track preview health check finished: checked={}, healthy={}, disabled={}, transientErrors={}, skippedProvider={}",
        result.checkedCount(),
        result.healthyCount(),
        result.disabledCount(),
        result.transientErrorCount(),
        result.skippedProviderCount());
  }

  /**
   * Scans a batch of active tracks and disables only tracks with confirmed missing previews. HTTP
   * calls run outside transactions; database updates run in individual short transactions.
   */
  public HealthCheckResult runHealthCheck() {
    int batchSize = Math.max(1, properties.healthCheck().batchSize());

    // Fetch batch of enabled tracks prioritized by oldest check timestamp (nulls first for
    // uninspected tracks)
    List<Track> candidateTracks =
        entityManager
            .createQuery(
                "select t from Track t where t.enabled = true order by t.lastPreviewCheckedAt nulls first, t.createdAt asc",
                Track.class)
            .setMaxResults(batchSize)
            .getResultList();

    int checked = 0;
    int healthy = 0;
    int disabled = 0;
    int transientErrors = 0;
    int skippedProvider = 0;

    String activeProviderName = musicProvider != null ? musicProvider.getProviderName() : null;

    for (Track track : candidateTracks) {
      // Provider matching: only check track if its provider matches the active musicProvider
      if (track.getProvider() != null
          && activeProviderName != null
          && !track.getProvider().name().equalsIgnoreCase(activeProviderName)
          && !"fake".equalsIgnoreCase(activeProviderName)) {
        // Update check timestamp so tracks belonging to other inactive providers don't starve the
        // queue
        markCheckedInTransaction(track.getId());
        skippedProvider++;
        continue;
      }

      checked++;

      Optional<ProviderTrack> ptOpt;
      try {
        // HTTP call strictly outside database transaction
        ptOpt = musicProvider.getTrack(track.getProviderTrackId());
      } catch (MusicProviderRateLimitException e) {
        log.warn(
            "Rate limit (HTTP 429) encountered while checking track {}. Halting health check batch early to prevent false positives.",
            track.getProviderTrackId());
        transientErrors++;
        break; // Fail-fast: do not disable tracks when rate limited!
      } catch (MusicProviderException e) {
        log.warn(
            "Transient provider error checking track {} ({}). Skipping without disabling.",
            track.getProviderTrackId(),
            e.getMessage());
        transientErrors++;
        // If circuit breaker is open, halt early
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("circuit breaker")) {
          log.warn("Circuit breaker open, aborting remaining health check batch");
          break;
        }
        continue;
      } catch (Exception e) {
        log.warn(
            "Unexpected error checking track {}: {}. Skipping without disabling.",
            track.getProviderTrackId(),
            e.getMessage());
        transientErrors++;
        continue;
      }

      boolean hasValidPreview = false;
      if (ptOpt.isPresent()) {
        ProviderTrack pt = ptOpt.get();
        if (pt.previewUrl() != null && !pt.previewUrl().isBlank()) {
          hasValidPreview = true;
        }
      }

      if (hasValidPreview) {
        markCheckedInTransaction(track.getId());
        healthy++;
      } else {
        // Confirmed preview failure: track 404 or preview URL missing from provider
        disableTrackInTransaction(track.getId());
        disabled++;
        log.warn(
            "Disabled track '{}' (id={}, pid={}) due to missing preview per BR-27",
            track.getTitle(),
            track.getId(),
            track.getProviderTrackId());
      }
    }

    return new HealthCheckResult(checked, healthy, disabled, transientErrors, skippedProvider);
  }

  private void markCheckedInTransaction(java.util.UUID trackId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          trackRepository
              .findById(trackId)
              .ifPresent(
                  t -> {
                    t.setLastPreviewCheckedAt(Instant.now());
                    trackRepository.save(t);
                  });
        });
  }

  private void disableTrackInTransaction(java.util.UUID trackId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          trackRepository
              .findById(trackId)
              .ifPresent(
                  t -> {
                    t.setEnabled(false);
                    t.setDisabledReason(
                        "Preview unavailable: track not found or preview URL missing from provider");
                    t.setLastPreviewCheckedAt(Instant.now());
                    trackRepository.save(t);
                  });
        });
  }
}
