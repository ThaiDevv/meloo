package com.meloo.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Genre;
import com.meloo.catalog.persistence.GenreRepository;
import com.meloo.catalog.persistence.MusicProvider;
import com.meloo.catalog.persistence.Playlist;
import com.meloo.catalog.persistence.PlaylistKind;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.PlaylistTrack;
import com.meloo.catalog.persistence.PlaylistTrackRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogServiceIntegrationTest extends IntegrationTest {

  @Autowired private CatalogService catalogService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;

  private Artist artistA;
  private Artist artistB;
  private Genre genreVpop;
  private Genre genreKpop;
  private Playlist playlistStandard;

  @BeforeEach
  void setUpData() {
    cleanDatabase();

    artistA =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Artist A")
                .nameNormalized("artist a")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-a")
                .build());

    artistB =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Artist B")
                .nameNormalized("artist b")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-b")
                .build());

    genreVpop =
        genreRepository.saveAndFlush(
            Genre.builder()
                .id(UUID.randomUUID())
                .code("vpop")
                .nameVi("V-Pop")
                .nameEn("V-Pop")
                .build());

    genreKpop =
        genreRepository.saveAndFlush(
            Genre.builder()
                .id(UUID.randomUUID())
                .code("kpop")
                .nameVi("K-Pop")
                .nameEn("K-Pop")
                .build());

    playlistStandard =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("standard")
                .nameVi("Bộ chuẩn")
                .nameEn("Standard Set")
                .kind(PlaylistKind.STANDARD)
                .enabled(true)
                .build());
  }

  @AfterEach
  void tearDown() {
    cleanDatabase();
  }

  private void cleanDatabase() {
    playlistTrackRepository.deleteAll();
    trackRepository.deleteAll();
    playlistRepository.deleteAll();
    artistRepository.deleteAll();
    genreRepository.deleteAll();
  }

  @Test
  @DisplayName("findPlaylistByCode returns playlist details and counts enabled tracks only")
  void findPlaylistByCodeReturnsPlaylistAndEnabledTrackCount() {
    Track enabledTrack =
        createTrack("Enabled Song", artistA, genreVpop, "VN", (short) 2020, true, null);
    Track disabledTrack =
        createTrack("Disabled Song", artistA, genreVpop, "VN", (short) 2021, false, null);
    linkTrackToPlaylist(playlistStandard, enabledTrack, 1);
    linkTrackToPlaylist(playlistStandard, disabledTrack, 2);

    var result = catalogService.findPlaylistByCode("standard");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().id()).isEqualTo(playlistStandard.getId());
    assertThat(result.orElseThrow().code()).isEqualTo("standard");
    assertThat(result.orElseThrow().name()).isEqualTo("Bộ chuẩn");
    assertThat(result.orElseThrow().enabled()).isTrue();
    assertThat(result.orElseThrow().enabledTrackCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("findPlaylistByCode returns empty when the playlist code does not exist")
  void findPlaylistByCodeReturnsEmptyForUnknownCode() {
    assertThat(catalogService.findPlaylistByCode("unknown")).isEmpty();
  }

  @Test
  @DisplayName("findPlaylistById includes disabled playlists and counts enabled tracks only")
  void findPlaylistByIdReturnsDisabledPlaylistAndEnabledTrackCount() {
    Playlist disabledPlaylist =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("disabled")
                .nameVi("Bộ tắt")
                .nameEn("Disabled Set")
                .kind(PlaylistKind.STANDARD)
                .enabled(false)
                .build());
    Track enabledTrack =
        createTrack("Enabled Song", artistA, genreVpop, "VN", (short) 2020, true, null);
    Track disabledTrack =
        createTrack("Disabled Song", artistA, genreVpop, "VN", (short) 2021, false, null);
    linkTrackToPlaylist(disabledPlaylist, enabledTrack, 1);
    linkTrackToPlaylist(disabledPlaylist, disabledTrack, 2);

    var result = catalogService.findPlaylistById(disabledPlaylist.getId());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().id()).isEqualTo(disabledPlaylist.getId());
    assertThat(result.orElseThrow().name()).isEqualTo("Bộ tắt");
    assertThat(result.orElseThrow().enabled()).isFalse();
    assertThat(result.orElseThrow().enabledTrackCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("findPlaylistById returns empty when the playlist ID does not exist")
  void findPlaylistByIdReturnsEmptyForUnknownId() {
    assertThat(catalogService.findPlaylistById(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName(
      "AC 6: selectRandomTracksByPlaylist returns distinct enabled tracks and excludes disabled or excluded IDs")
  void selectRandomTracksByPlaylistRespectsConditions() {
    Track t1 =
        createTrack(
            "Track 1",
            artistA,
            genreVpop,
            "VN",
            (short) 2020,
            true,
            "https://cdn.example.com/cover1.jpg");
    Track t2 = createTrack("Track 2", artistA, genreVpop, "VN", (short) 2021, true, null);
    Track t3 =
        createTrack(
            "Track 3",
            artistB,
            genreVpop,
            "VN",
            (short) 2022,
            true,
            "https://cdn.example.com/cover3.jpg");
    Track tDisabled =
        createTrack("Track Disabled", artistB, genreVpop, "VN", (short) 2023, false, null);

    linkTrackToPlaylist(playlistStandard, t1, 1);
    linkTrackToPlaylist(playlistStandard, t2, 2);
    linkTrackToPlaylist(playlistStandard, t3, 3);
    linkTrackToPlaylist(playlistStandard, tDisabled, 4);

    // 1. Select 2 tracks without exclusion
    List<TrackInfo> selected2 =
        catalogService.selectRandomTracksByPlaylist(playlistStandard.getId(), 2);
    assertThat(selected2).hasSize(2);
    assertThat(selected2).doesNotHaveDuplicates();
    assertThat(selected2).noneMatch(t -> t.id().equals(tDisabled.getId()));

    // 2. Select with excluded track ID (e.g. t1 already played)
    List<TrackInfo> selectedExcluding =
        catalogService.selectRandomTracksByPlaylist(
            playlistStandard.getId(), 5, Set.of(t1.getId()));
    assertThat(selectedExcluding).hasSize(2);
    assertThat(selectedExcluding)
        .extracting(TrackInfo::id)
        .containsExactlyInAnyOrder(t2.getId(), t3.getId());

    // 3. By playlist code
    List<TrackInfo> byCode = catalogService.selectRandomTracksByPlaylistCode("standard", 10);
    assertThat(byCode).hasSize(3);
  }

  @Test
  @DisplayName(
      "AC 6: selectRandomTracks with filter selects by genres, countries, decades, and artistIds")
  void selectRandomTracksWithFilter() {
    Track tVn2020 = createTrack("VN 2020", artistA, genreVpop, "VN", (short) 2020, true, null);
    Track tVn2015 = createTrack("VN 2015", artistA, genreVpop, "VN", (short) 2015, true, null);
    Track tKr2021 = createTrack("KR 2021", artistB, genreKpop, "KR", (short) 2021, true, null);

    // Filter by genre 'kpop'
    TrackFilter kpopFilter = new TrackFilter(List.of("kpop"), null, null, null);
    List<TrackInfo> kpopTracks = catalogService.selectRandomTracks(kpopFilter, 5);
    assertThat(kpopTracks).hasSize(1);
    assertThat(kpopTracks.getFirst().id()).isEqualTo(tKr2021.getId());

    // Filter by country 'VN' and decade 2020 (decade = 2020 matches release_year 2020, but not
    // 2015)
    TrackFilter vn2020Filter = new TrackFilter(null, List.of("VN"), List.of(2020), null);
    List<TrackInfo> vn2020Tracks = catalogService.selectRandomTracks(vn2020Filter, 5);
    assertThat(vn2020Tracks).hasSize(1);
    assertThat(vn2020Tracks.getFirst().id()).isEqualTo(tVn2020.getId());

    // Empty filter selects from the entire catalog
    List<TrackInfo> allTracks = catalogService.selectRandomTracks(null, 10);
    assertThat(allTracks).hasSize(3);
    assertThat(allTracks).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName(
      "AC 6: getTrackById and getTracksByIds return correct track information including coverUrl (present and null)")
  void getTrackByIdReturnsCoverUrl() {
    Track withCover =
        createTrack(
            "Cover Song",
            artistA,
            genreVpop,
            "VN",
            (short) 2022,
            true,
            "https://cdn.example.com/album.png");
    Track withoutCover =
        createTrack("No Cover Song", artistB, genreKpop, "KR", (short) 2023, true, null);
    Track disabledTrack =
        createTrack(
            "Disabled Song",
            artistA,
            genreVpop,
            "VN",
            (short) 2021,
            false,
            "https://cdn.example.com/dis.png");

    // 1. Track with coverUrl
    Optional<TrackInfo> infoWith = catalogService.getTrackById(withCover.getId());
    assertThat(infoWith).isPresent();
    assertThat(infoWith.get().title()).isEqualTo("Cover Song");
    assertThat(infoWith.get().artistName()).isEqualTo("Artist A");
    assertThat(infoWith.get().provider()).isEqualTo("DEEZER");
    assertThat(infoWith.get().coverUrl()).isEqualTo("https://cdn.example.com/album.png");
    assertThat(infoWith.get().countryCode()).isEqualTo("VN");
    assertThat(infoWith.get().releaseYear()).isEqualTo((short) 2022);

    // 2. Track without coverUrl
    Optional<TrackInfo> infoWithout = catalogService.getTrackById(withoutCover.getId());
    assertThat(infoWithout).isPresent();
    assertThat(infoWithout.get().coverUrl()).isNull();

    // 3. Disabled track or non-existent returns empty
    assertThat(catalogService.getTrackById(disabledTrack.getId())).isEmpty();
    assertThat(catalogService.getTrackById(UUID.randomUUID())).isEmpty();

    // 4. Bulk getTracksByIds
    Map<UUID, TrackInfo> map =
        catalogService.getTracksByIds(
            List.of(withCover.getId(), withoutCover.getId(), disabledTrack.getId()));
    assertThat(map).hasSize(2);
    assertThat(map).containsKey(withCover.getId());
    assertThat(map).containsKey(withoutCover.getId());
    assertThat(map).doesNotContainKey(disabledTrack.getId());
  }

  private Track createTrack(
      String title,
      Artist artist,
      Genre genre,
      String country,
      short year,
      boolean enabled,
      String coverUrl) {
    return trackRepository.saveAndFlush(
        Track.builder()
            .id(UUID.randomUUID())
            .title(title)
            .titleNormalized(title.toLowerCase().replace(" ", ""))
            .primaryArtistId(artist.getId())
            .genreId(genre.getId())
            .countryCode(country)
            .releaseYear(year)
            .provider(MusicProvider.DEEZER)
            .providerTrackId("trk-" + UUID.randomUUID())
            .enabled(enabled)
            .coverUrl(coverUrl)
            .build());
  }

  private void linkTrackToPlaylist(Playlist playlist, Track track, int position) {
    playlistTrackRepository.saveAndFlush(
        PlaylistTrack.builder()
            .playlistId(playlist.getId())
            .trackId(track.getId())
            .position(position)
            .build());
  }
}
