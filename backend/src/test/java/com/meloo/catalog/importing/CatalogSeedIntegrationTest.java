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

    // Verify AC4 & N1: Every curated playlist has ample tracks (minimum 10)
    playlistRepository
        .findAll()
        .forEach(
            playlist -> {
              long tracksInPlaylist =
                  playlistTrackRepository.findAll().stream()
                      .filter(pt -> pt.getPlaylistId().equals(playlist.getId()))
                      .count();
              assertThat(tracksInPlaylist)
                  .as("Playlist '%s' must not be empty", playlist.getCode())
                  .isGreaterThanOrEqualTo(10);
            });

    // Verify AC4 & N1: Catalog contains balanced country representation (VN, US, GB, KR)
    java.util.Set<String> countries =
        trackRepository.findAll().stream()
            .map(t -> t.getCountryCode())
            .filter(c -> c != null)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(countries).contains("VN", "US", "GB", "KR");

    // Verify AC1 & N3 & N4: Decade checks with real release years and strict country consistency
    var nostalgiaPlaylist =
        playlistRepository.findAll().stream()
            .filter(p -> "vpop-nostalgia-2010s".equals(p.getCode()))
            .findFirst()
            .orElseThrow();
    var nostalgiaTracks =
        playlistTrackRepository.findAll().stream()
            .filter(pt -> pt.getPlaylistId().equals(nostalgiaPlaylist.getId()))
            .map(pt -> trackRepository.findById(pt.getTrackId()).orElseThrow())
            .toList();
    assertThat(nostalgiaTracks.size()).isGreaterThanOrEqualTo(50);
    assertThat(nostalgiaTracks)
        .allSatisfy(
            t -> {
              assertThat(t.getReleaseYear().intValue())
                  .as("Track %s in vpop-nostalgia-2010s must be 2010-2019", t.getTitle())
                  .isBetween(2010, 2019);
              assertThat(t.getCountryCode())
                  .as("Track %s in vpop-nostalgia-2010s must have VN country", t.getTitle())
                  .isEqualTo("VN");
            });

    var classicsPlaylist =
        playlistRepository.findAll().stream()
            .filter(p -> "usuk-pop-classics".equals(p.getCode()))
            .findFirst()
            .orElseThrow();
    var classicsTracks =
        playlistTrackRepository.findAll().stream()
            .filter(pt -> pt.getPlaylistId().equals(classicsPlaylist.getId()))
            .map(pt -> trackRepository.findById(pt.getTrackId()).orElseThrow())
            .toList();
    assertThat(classicsTracks.size()).isGreaterThanOrEqualTo(50);
    assertThat(classicsTracks)
        .allSatisfy(
            t -> {
              assertThat(t.getReleaseYear().intValue())
                  .as("Track %s in usuk-pop-classics must be before 2020", t.getTitle())
                  .isLessThan(2020);
              assertThat(t.getCountryCode())
                  .as("Track %s in usuk-pop-classics must have Western country", t.getTitle())
                  .isIn("US", "GB", "CA", "SE", "NO");
            });

    var kpopPlaylist =
        playlistRepository.findAll().stream()
            .filter(p -> "kpop-top-hits".equals(p.getCode()))
            .findFirst()
            .orElseThrow();
    var kpopTracks =
        playlistTrackRepository.findAll().stream()
            .filter(pt -> pt.getPlaylistId().equals(kpopPlaylist.getId()))
            .map(pt -> trackRepository.findById(pt.getTrackId()).orElseThrow())
            .toList();
    assertThat(kpopTracks.size()).isGreaterThanOrEqualTo(50);
    assertThat(kpopTracks)
        .allSatisfy(
            t -> {
              assertThat(t.getCountryCode())
                  .as("Track %s in kpop-top-hits must have KR country", t.getTitle())
                  .isEqualTo("KR");
            });

    // Verify all seeded tracks have non-null real release years
    assertThat(trackRepository.findAll())
        .allSatisfy(
            t -> {
              assertThat(t.getReleaseYear())
                  .as("Track %s must have non-null release year", t.getTitle())
                  .isNotNull();
            });

    // Re-run seed: must be completely idempotent (no duplicates created)
    CatalogSeedService.CatalogSeedResult rerunResult = seedService.seedCatalog();
    assertThat(rerunResult.tracksCount()).isEqualTo(0);
    assertThat(rerunResult.playlistTracksCount()).isEqualTo(0);

    assertThat(trackRepository.count()).isEqualTo(totalTracksInDb);
    assertThat(playlistTrackRepository.count()).isEqualTo(totalLinks);
  }
}
