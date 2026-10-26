package com.meloo.catalog.importing;

import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.ProviderTrack;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogImportService {

  private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);

  private final com.meloo.catalog.provider.MusicProvider musicProvider;
  private final TrackRepository trackRepository;
  private final ArtistRepository artistRepository;
  private final EntityManager entityManager;

  public CatalogImportService(
      com.meloo.catalog.provider.MusicProvider musicProvider,
      TrackRepository trackRepository,
      ArtistRepository artistRepository,
      EntityManager entityManager) {
    this.musicProvider = musicProvider;
    this.trackRepository = trackRepository;
    this.artistRepository = artistRepository;
    this.entityManager = entityManager;
  }

  public record ImportResult(
      int totalProcessed,
      int importedCount,
      int skippedNoPreviewCount,
      int duplicateCount,
      List<Track> importedTracks) {}

  /**
   * Imports tracks from provider chart. Note: HTTP call is done outside @Transactional boundary per
   * Meloo backend rules.
   */
  public ImportResult importFromChart(
      int limit, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    List<ProviderTrack> providerTracks = musicProvider.chart(limit);
    return importTracks(providerTracks, genreId, defaultCountryCode);
  }

  /** Imports tracks from provider playlist. */
  public ImportResult importFromPlaylist(
      String playlistId, int limit, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    List<ProviderTrack> providerTracks = musicProvider.playlist(playlistId, limit);
    return importTracks(providerTracks, genreId, defaultCountryCode);
  }

  /** Imports tracks from artist top tracks. */
  public ImportResult importFromArtist(
      String artistId, int limit, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    List<ProviderTrack> providerTracks = musicProvider.artistTopTracks(artistId, limit);
    return importTracks(providerTracks, genreId, defaultCountryCode);
  }

  /**
   * Idempotently persists tracks in a single database transaction. - Bỏ bài không có preview URL -
   * Chống trùng theo (provider, providerTrackId) - Chuẩn hóa tên bài gom remix/live theo BR-05 -
   * Lưu coverUrl từ adapter
   */
  @Transactional
  public ImportResult importTracks(
      List<ProviderTrack> providerTracks,
      @Nullable UUID genreId,
      @Nullable String defaultCountryCode) {
    if (providerTracks == null || providerTracks.isEmpty()) {
      return new ImportResult(0, 0, 0, 0, List.of());
    }

    com.meloo.catalog.persistence.MusicProvider persistenceProvider = resolveProviderEnum();

    int imported = 0;
    int skippedNoPreview = 0;
    int duplicates = 0;
    List<Track> resultList = new ArrayList<>();

    for (ProviderTrack pt : providerTracks) {
      // 1. Skip tracks without preview (AC: bỏ bài không có preview)
      if (pt.previewUrl() == null || pt.previewUrl().isBlank()) {
        skippedNoPreview++;
        log.debug("Skipping track {} because previewUrl is missing", pt.providerTrackId());
        continue;
      }

      // 2. Deduplication check (AC: chống trùng theo nhà cung cấp + id)
      Optional<Track> existingTrackOpt =
          findTrackByProvider(persistenceProvider, pt.providerTrackId());

      if (existingTrackOpt.isPresent()) {
        Track existingTrack = existingTrackOpt.get();
        duplicates++;
        // If existing track was missing coverUrl and adapter now has it, update it
        if (existingTrack.getCoverUrl() == null
            && pt.coverUrl() != null
            && !pt.coverUrl().isBlank()) {
          existingTrack.setCoverUrl(pt.coverUrl());
          trackRepository.save(existingTrack);
        }
        resultList.add(existingTrack);
        continue;
      }

      // 3. Find or create Artist
      Artist artist = findOrCreateArtist(persistenceProvider, pt);

      // 4. Compute normalized title per BR-05 (gom remix/live)
      String normalizedTitle = TitleNormalizer.normalizeTitle(pt.title());

      String country = pt.countryCode() != null ? pt.countryCode() : defaultCountryCode;
      if (country != null) {
        country = country.trim().toUpperCase(Locale.ROOT);
        if (country.length() > 2) {
          country = country.substring(0, 2);
        }
      }

      // 5. Create new Track with V10 coverUrl
      Track newTrack =
          Track.builder()
              .id(UUID.randomUUID())
              .provider(persistenceProvider)
              .providerTrackId(pt.providerTrackId())
              .title(pt.title())
              .titleNormalized(normalizedTitle)
              .primaryArtistId(artist.getId())
              .genreId(genreId)
              .countryCode(country)
              .releaseYear(pt.releaseYear())
              .popularityRank(pt.popularityRank())
              .enabled(true)
              .coverUrl(pt.coverUrl())
              .build();

      Track saved = trackRepository.save(newTrack);
      resultList.add(saved);
      imported++;
    }

    log.info(
        "Imported {} tracks (skipped no preview: {}, duplicates: {})",
        imported,
        skippedNoPreview,
        duplicates);

    return new ImportResult(
        providerTracks.size(), imported, skippedNoPreview, duplicates, resultList);
  }

  private com.meloo.catalog.persistence.MusicProvider resolveProviderEnum() {
    String name = musicProvider.getProviderName();
    if (name != null && name.equalsIgnoreCase("itunes")) {
      return com.meloo.catalog.persistence.MusicProvider.ITUNES;
    }
    return com.meloo.catalog.persistence.MusicProvider.DEEZER;
  }

  private Optional<Track> findTrackByProvider(
      com.meloo.catalog.persistence.MusicProvider provider, String providerTrackId) {
    List<Track> tracks =
        entityManager
            .createQuery(
                "select t from Track t where t.provider = :provider and t.providerTrackId = :pid",
                Track.class)
            .setParameter("provider", provider)
            .setParameter("pid", providerTrackId)
            .getResultList();
    return tracks.isEmpty() ? Optional.empty() : Optional.of(tracks.getFirst());
  }

  private Artist findOrCreateArtist(
      com.meloo.catalog.persistence.MusicProvider provider, ProviderTrack pt) {
    String providerArtistId =
        (pt.artistId() != null && !pt.artistId().isBlank())
            ? pt.artistId()
            : "art-" + TitleNormalizer.normalizeArtist(pt.artistName());

    List<Artist> artists =
        entityManager
            .createQuery(
                "select a from Artist a where a.provider = :provider and a.providerArtistId = :pid",
                Artist.class)
            .setParameter("provider", provider)
            .setParameter("pid", providerArtistId)
            .getResultList();

    if (!artists.isEmpty()) {
      return artists.getFirst();
    }

    String artistName =
        (pt.artistName() != null && !pt.artistName().isBlank()) ? pt.artistName() : "Unknown";

    Artist newArtist =
        Artist.builder()
            .id(UUID.randomUUID())
            .name(artistName)
            .nameNormalized(TitleNormalizer.normalizeArtist(artistName))
            .provider(provider)
            .providerArtistId(providerArtistId)
            .build();

    return artistRepository.save(newArtist);
  }
}
