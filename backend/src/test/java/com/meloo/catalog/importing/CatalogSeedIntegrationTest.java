package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.GenreRepository;
import com.meloo.catalog.persistence.PlaylistKind;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.PlaylistTrackRepository;
import com.meloo.catalog.persistence.TrackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogSeedIntegrationTest extends IntegrationTest {

  @Autowired private CatalogSeedService seedService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;

  @Test
  @DisplayName("Seed loads >= 1000 tracks and 5-10 curated playlists idempotently")
  void seedCatalog_loadsAtLeast1000TracksAndCuratedPlaylists() {
    CatalogSeedService.CatalogSeedResult result = seedService.seedCatalog();

    assertThat(result.tracksCount()).isGreaterThanOrEqualTo(1000);
    assertThat(result.genresCount()).isGreaterThanOrEqualTo(8);
    assertThat(result.playlistsCount()).isBetween(5, 10);
    assertThat(result.playlistTracksCount()).isPositive();

    // Verify in database
    long totalTracksInDb = trackRepository.count();
    assertThat(totalTracksInDb).isGreaterThanOrEqualTo(1000);

    long curatedPlaylists =
        playlistRepository.findAll().stream()
            .filter(p -> p.getKind() == PlaylistKind.CURATED)
            .count();
    assertThat(curatedPlaylists).isBetween(5L, 10L);

    long totalGenres = genreRepository.count();
    assertThat(totalGenres).isGreaterThanOrEqualTo(8);

    long totalLinks = playlistTrackRepository.count();
    assertThat(totalLinks).isPositive();

    // Re-run seed: must be completely idempotent (no duplicates created)
    CatalogSeedService.CatalogSeedResult rerunResult = seedService.seedCatalog();
    assertThat(rerunResult.tracksCount()).isEqualTo(0);
    assertThat(rerunResult.playlistTracksCount()).isEqualTo(0);

    assertThat(trackRepository.count()).isEqualTo(totalTracksInDb);
    assertThat(playlistTrackRepository.count()).isEqualTo(totalLinks);
  }
}
