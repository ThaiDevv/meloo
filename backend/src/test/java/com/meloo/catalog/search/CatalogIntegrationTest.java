package com.meloo.catalog.search;

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
import com.meloo.generated.model.ArtistDto;
import com.meloo.generated.model.FilterOptionsDto;
import com.meloo.generated.model.GenreDto;
import com.meloo.generated.model.PlaylistDto;
import com.meloo.generated.model.PlaylistPageDto;
import com.meloo.generated.model.TrackPageDto;
import com.meloo.generated.model.TrackSuggestionDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogIntegrationTest extends IntegrationTest {

  @Autowired private TrackSearchService searchService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;

  private Artist sonTung;
  private Artist vu;
  private Genre vpop;
  private Genre indie;

  @BeforeEach
  void setUpData() {
    playlistTrackRepository.deleteAll();
    trackRepository.deleteAll();
    playlistRepository.deleteAll();
    artistRepository.deleteAll();
    genreRepository.deleteAll();

    sonTung =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Sơn Tùng M-TP")
                .nameNormalized("son tung m tp")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-st-01")
                .build());

    vu =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Vũ.")
                .nameNormalized("vu")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-vu-01")
                .build());

    vpop =
        genreRepository.saveAndFlush(
            Genre.builder()
                .id(UUID.randomUUID())
                .code("vpop")
                .nameVi("V-Pop")
                .nameEn("V-Pop")
                .build());

    indie =
        genreRepository.saveAndFlush(
            Genre.builder()
                .id(UUID.randomUUID())
                .code("indie-viet")
                .nameVi("Indie Việt")
                .nameEn("Indie Viet")
                .build());
  }

  @Test
  @DisplayName(
      "AC 1: Autocomplete returns <= 10 suggestions 'Tên bài - Ca sĩ', accent and case insensitive, empty on < 2 chars")
  void autocompleteSearchMatchesAccentedAndUnaccented() {
    Track t1 =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Lạc Trôi")
                .titleNormalized("lac troi")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-st-01")
                .enabled(true)
                .build());

    Track t2 =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Chúng Ta Của Hiện Tại")
                .titleNormalized("chung ta cua hien tai")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-st-02")
                .enabled(true)
                .build());

    // 1. Accented search
    TrackPageDto resAccented = searchService.searchTracks("Lạc Trôi", 0, 10);
    assertThat(resAccented.getItems()).isNotEmpty();
    assertThat(resAccented.getItems().getFirst().getTitle()).isEqualTo("Lạc Trôi");
    assertThat(resAccented.getItems().getFirst().getArtistName()).isEqualTo("Sơn Tùng M-TP");

    // 2. Unaccented & lowercase search
    TrackPageDto resUnaccented = searchService.searchTracks("lac troi", 0, 10);
    assertThat(resUnaccented.getItems()).isNotEmpty();
    assertThat(resUnaccented.getItems().getFirst().getId()).isEqualTo(t1.getId());

    // 3. Search by artist name: "son tung" returns tracks by Sơn Tùng M-TP
    TrackPageDto resArtist = searchService.searchTracks("son tung", 0, 10);
    assertThat(resArtist.getItems()).hasSize(2);
    assertThat(resArtist.getItems())
        .extracting(TrackSuggestionDto::getArtistName)
        .containsOnly("Sơn Tùng M-TP");

    // 4. Special characters: "Lạc Trôi!?" matches
    TrackPageDto resSpecial = searchService.searchTracks("Lạc Trôi!?", 0, 10);
    assertThat(resSpecial.getItems()).hasSize(1);
    assertThat(resSpecial.getItems().getFirst().getId()).isEqualTo(t1.getId());

    // 5. Short string (< 2 characters) and empty return empty results
    assertThat(searchService.searchTracks("", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks(" ", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks("s", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks(" s ", 0, 10).getItems()).isEmpty();
  }

  @Test
  @DisplayName("AC 2: Ranking: Khớp đầu chuỗi > Khớp từ > Trigram")
  void searchRankingOrder() {
    // Prefix match: starts with "mua" -> "Mùa Hè Của Em"
    Track prefixTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Mùa Hè Của Em")
                .titleNormalized("mua he cua em")
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-01")
                .enabled(true)
                .popularityRank(10)
                .build());

    // Word boundary match: contains " mua" -> "Cơn Mưa Ngang Qua"
    Track wordTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Cơn Mưa Ngang Qua")
                .titleNormalized("con mua ngang qua")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-02")
                .enabled(true)
                .popularityRank(5)
                .build());

    // Trigram match: "Mưa" misspelled as "mia" -> "Mưa Hồng" (similarity on 'mua' vs 'muaf')
    // Or title similar to search query "mua"
    Track trigramTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Muôn Màu")
                .titleNormalized("muon mau")
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-03")
                .enabled(true)
                .popularityRank(1)
                .build());

    TrackPageDto result = searchService.searchTracks("mua", 0, 10);

    // Prefix track should come first (tier 1), then word track (tier 2)
    assertThat(result.getItems()).isNotEmpty();
    assertThat(result.getItems().getFirst().getId()).isEqualTo(prefixTrack.getId());
    assertThat(result.getItems().get(1).getId()).isEqualTo(wordTrack.getId());
  }

  @Test
  @DisplayName("AC 3: Search searches whole catalog (BR-05) and excludes disabled tracks (BR-27)")
  void searchIncludesAllCatalogExcludingDisabled() {
    Track enabledTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Chạy Ngay Đi")
                .titleNormalized("chay ngay di")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-st-enabled")
                .enabled(true)
                .build());

    Track disabledTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Chạy Trốn")
                .titleNormalized("chay tron")
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-vu-disabled")
                .enabled(false)
                .disabledReason("Preview unavailable")
                .build());

    TrackPageDto result = searchService.searchTracks("chay", 0, 10);
    List<UUID> ids = result.getItems().stream().map(TrackSuggestionDto::getId).toList();

    assertThat(ids).contains(enabledTrack.getId());
    assertThat(ids).doesNotContain(disabledTrack.getId());
  }

  @Test
  @DisplayName(
      "AC 4: GET /playlists returns only enabled playlists and trackCount counts only enabled tracks")
  void playlistTrackCountOnlyEnabled() {
    Playlist enabledPlaylist =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("vpop-hits")
                .nameVi("V-Pop Tuyển Chọn")
                .nameEn("V-Pop Hits")
                .kind(PlaylistKind.CURATED)
                .enabled(true)
                .build());

    Playlist disabledPlaylist =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("disabled-list")
                .nameVi("Playlist Tắt")
                .nameEn("Disabled Playlist")
                .kind(PlaylistKind.CURATED)
                .enabled(false)
                .build());

    Track t1 =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Track 1")
                .titleNormalized("track 1")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-p-01")
                .enabled(true)
                .build());

    Track t2Disabled =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Track 2 Disabled")
                .titleNormalized("track 2 disabled")
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-p-02")
                .enabled(false)
                .build());

    playlistTrackRepository.saveAndFlush(
        PlaylistTrack.builder()
            .playlistId(enabledPlaylist.getId())
            .trackId(t1.getId())
            .position(1)
            .build());

    playlistTrackRepository.saveAndFlush(
        PlaylistTrack.builder()
            .playlistId(enabledPlaylist.getId())
            .trackId(t2Disabled.getId())
            .position(2)
            .build());

    PlaylistPageDto page = searchService.listPlaylists(0, 10);

    assertThat(page.getItems()).hasSize(1);
    PlaylistDto dto = page.getItems().getFirst();
    assertThat(dto.getCode()).isEqualTo("vpop-hits");
    // trackCount must be 1 because t2 is disabled
    assertThat(dto.getTrackCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "AC 5: GET /filters lists only values with at least one enabled track and artists sorted by name")
  void filtersOnlyWithEnabledTracks() {
    // Artist with enabled track
    Track t1 =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Bước Qua Nhau")
                .titleNormalized("buoc qua nhau")
                .primaryArtistId(vu.getId())
                .genreId(indie.getId())
                .countryCode("VN")
                .releaseYear((short) 2021)
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-fl-01")
                .enabled(true)
                .build());

    // Artist with ONLY disabled track
    Artist disabledArtist =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Artist Khong Co Bai")
                .nameNormalized("artist khong co bai")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-empty")
                .build());

    trackRepository.saveAndFlush(
        Track.builder()
            .id(UUID.randomUUID())
            .title("Bài Bị Tắt")
            .titleNormalized("bai bi tat")
            .primaryArtistId(disabledArtist.getId())
            .genreId(vpop.getId())
            .countryCode("US")
            .releaseYear((short) 1999)
            .provider(MusicProvider.DEEZER)
            .providerTrackId("trk-fl-disabled")
            .enabled(false)
            .build());

    FilterOptionsDto filters = searchService.getFilters();

    // Genres: should only include indie-viet (vpop only has disabled track)
    assertThat(filters.getGenres())
        .extracting(GenreDto::getCode)
        .contains("indie-viet")
        .doesNotContain("vpop");

    // Countries: should contain "VN", not "US"
    assertThat(filters.getCountries()).contains("VN").doesNotContain("US");

    // Decades: 2021 -> 2020. 1999 should not appear because its track is disabled.
    assertThat(filters.getDecades()).contains(2020).doesNotContain(1990);

    // Artists: only Vũ., disabledArtist is excluded
    assertThat(filters.getArtists())
        .extracting(ArtistDto::getName)
        .contains("Vũ.")
        .doesNotContain("Artist Khong Co Bai");
  }

  @Test
  @DisplayName("AC 2: p95 latency under 100ms for search")
  void searchLatencyP95() {
    // Warm up
    searchService.searchTracks("lac troi", 0, 10);

    List<Long> latencies = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      long start = System.nanoTime();
      searchService.searchTracks("son tung", 0, 10);
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      latencies.add(elapsedMs);
    }

    Collections.sort(latencies);
    int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
    long p95Latency = latencies.get(Math.max(0, p95Index));

    assertThat(p95Latency)
        .withFailMessage("p95 latency (%d ms) should be < 100 ms", p95Latency)
        .isLessThan(100L);
  }
}
