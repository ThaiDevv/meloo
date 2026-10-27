package com.meloo.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.catalog.importing.TitleNormalizer;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CatalogIntegrationTest extends IntegrationTest {

  @Autowired private TrackSearchService searchService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  private Artist sonTung;
  private Artist vu;
  private Genre vpop;
  private Genre indie;

  @BeforeEach
  void setUpData() {
    cleanDatabase();

    // Use TitleNormalizer to accurately match DB importing logic (e.g. "son tung m-tp")
    sonTung =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Sơn Tùng M-TP")
                .nameNormalized(TitleNormalizer.normalizeArtist("Sơn Tùng M-TP"))
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-st-01")
                .build());

    vu =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Vũ.")
                .nameNormalized(TitleNormalizer.normalizeArtist("Vũ."))
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
  @DisplayName(
      "AC 1: Autocomplete returns <= 10 suggestions 'Tên bài - Ca sĩ', accent and case insensitive, empty on < 2 chars")
  void autocompleteSearchMatchesAccentedAndUnaccented() {
    Track t1 =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Lạc Trôi")
                .titleNormalized(TitleNormalizer.normalizeTitle("Lạc Trôi"))
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
                .titleNormalized(TitleNormalizer.normalizeTitle("Chúng Ta Của Hiện Tại"))
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

    // 4. Search by artist hyphenated suffix: "m-tp" and "m tp"
    TrackPageDto resMtp = searchService.searchTracks("m-tp", 0, 10);
    assertThat(resMtp.getItems()).hasSize(2);
    TrackPageDto resMtpSpace = searchService.searchTracks("m tp", 0, 10);
    assertThat(resMtpSpace.getItems()).hasSize(2);

    // 5. Special characters: "Lạc Trôi!?" matches
    TrackPageDto resSpecial = searchService.searchTracks("Lạc Trôi!?", 0, 10);
    assertThat(resSpecial.getItems()).hasSize(1);
    assertThat(resSpecial.getItems().getFirst().getId()).isEqualTo(t1.getId());

    // 6. Short string (< 2 characters) and empty return empty results
    assertThat(searchService.searchTracks("", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks(" ", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks("s", 0, 10).getItems()).isEmpty();
    assertThat(searchService.searchTracks(" s ", 0, 10).getItems()).isEmpty();
  }

  @Test
  @DisplayName("AC 2: Ranking: Khớp đầu chuỗi (Tier 1) > Khớp từ (Tier 2) > Trigram (Tier 3)")
  void searchRankingOrder() {
    // Tier 1 (Khớp đầu chuỗi): starts with "lac troi"
    Track prefixTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Lạc Trôi")
                .titleNormalized(TitleNormalizer.normalizeTitle("Lạc Trôi")) // "lac troi"
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-01")
                .enabled(true)
                .popularityRank(50)
                .build());

    // Tier 2 (Khớp từ / substring): contains "lac troi" as word
    Track wordTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Bài Ca Lạc Trôi")
                .titleNormalized(
                    TitleNormalizer.normalizeTitle("Bài Ca Lạc Trôi")) // "bai ca lac troi"
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-02")
                .enabled(true)
                .popularityRank(1)
                .build());

    // Tier 3 (Trigram similarity): typo "Lạc Tối" vs query "lac troi"
    Track trigramTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Lạc Tối")
                .titleNormalized(TitleNormalizer.normalizeTitle("Lạc Tối")) // "lac toi"
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-rk-03")
                .enabled(true)
                .popularityRank(1)
                .build());

    TrackPageDto result = searchService.searchTracks("lac troi", 0, 10);

    // Verify ordering: Tier 1 > Tier 2 > Tier 3
    assertThat(result.getItems()).hasSize(3);
    assertThat(result.getItems().get(0).getId())
        .withFailMessage("First result must be prefix match (Tier 1)")
        .isEqualTo(prefixTrack.getId());
    assertThat(result.getItems().get(1).getId())
        .withFailMessage("Second result must be word/substring match (Tier 2)")
        .isEqualTo(wordTrack.getId());
    assertThat(result.getItems().get(2).getId())
        .withFailMessage("Third result must be trigram similarity match (Tier 3)")
        .isEqualTo(trigramTrack.getId());
  }

  @Test
  @DisplayName(
      "AC 2: Ranking for hyphenated artist query: Prefix (Tier 1) / Word (Tier 2) > Trigram (Tier 3)")
  void searchRankingForHyphenatedArtistOrder() {
    // 1. Sơn Tùng M-TP track (artist name normalized: "son tung m-tp")
    Track sonTungTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Nắng Ấm Xa Dần")
                .titleNormalized(TitleNormalizer.normalizeTitle("Nắng Ấm Xa Dần"))
                .primaryArtistId(sonTung.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-st-rank")
                .enabled(true)
                .popularityRank(10)
                .build());

    // 2. Artist with similar name that matches via trigram similarity but not prefix or substring
    Artist similarArtist =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Sơn Tùng M-TO")
                .nameNormalized("son tung m-to")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-st-similar")
                .build());

    Track trigramArtistTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Khác Biệt To Lớn")
                .titleNormalized(TitleNormalizer.normalizeTitle("Khác Biệt To Lớn"))
                .primaryArtistId(similarArtist.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-similar-rank")
                .enabled(true)
                .popularityRank(1) // Higher popularity, but lower tier
                .build());

    // 3. Track with title "M-Tip" (similar trigram to query "m-tp", but doesn't contain "m-tp")
    Track trigramTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("M-Tip")
                .titleNormalized(TitleNormalizer.normalizeTitle("M-Tip"))
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-mtip-rank")
                .enabled(true)
                .popularityRank(1)
                .build());

    // Test query "m-tp": Sơn Tùng M-TP matches word pattern (Tier 2), "M-Tip" matches trigram (Tier
    // 3)
    TrackPageDto resMtp = searchService.searchTracks("m-tp", 0, 10);
    assertThat(resMtp.getItems()).isNotEmpty();
    assertThat(resMtp.getItems().getFirst().getId())
        .withFailMessage(
            "Sơn Tùng M-TP (Tier 2) must rank before trigram similarity match (Tier 3)")
        .isEqualTo(sonTungTrack.getId());

    // 4. Competitor track whose title contains "Sơn Tùng M-TP" (word/substring match -> Tier 2)
    Track competitorTrack =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Hát Về Sơn Tùng M-TP")
                .titleNormalized(TitleNormalizer.normalizeTitle("Hát Về Sơn Tùng M-TP"))
                .primaryArtistId(vu.getId())
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-competitor-rank")
                .enabled(true)
                .popularityRank(1) // Higher popularity than sonTungTrack (1 vs 10)
                .build());

    // Test query "son tung m-tp":
    // - Sơn Tùng M-TP matches artist prefix via replace('-', ' ') -> Tier 1
    // - Competitor track matches word in title -> Tier 2
    // - "Sơn Tùng M-TO" matches trigram -> Tier 3
    // On old code (without hyphen handling), sonTungTrack fell into Tier 3 so competitorTrack (Tier
    // 2) was ranked first. This test explicitly verifies Tier 1 > Tier 2 > Tier 3.
    TrackPageDto resFullName = searchService.searchTracks("son tung m-tp", 0, 10);
    assertThat(resFullName.getItems()).hasSizeGreaterThanOrEqualTo(3);
    assertThat(resFullName.getItems().get(0).getId())
        .withFailMessage("Sơn Tùng M-TP (Tier 1) must rank before title word match (Tier 2)")
        .isEqualTo(sonTungTrack.getId());
    assertThat(resFullName.getItems().get(1).getId())
        .withFailMessage(
            "Title word match (Tier 2) must rank before trigram similarity match (Tier 3)")
        .isEqualTo(competitorTrack.getId());
    assertThat(resFullName.getItems().get(2).getId())
        .withFailMessage("Third result must be trigram similarity match (Tier 3)")
        .isEqualTo(trigramArtistTrack.getId());
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
  @DisplayName("AC 2: p95 latency under 100ms with >= 10,000 tracks in database")
  void searchLatencyP95With10kTracks() {
    try {
      // 1. Seed 1,000 artists and 10,000 tracks via batch insert
      seed10kTracks();

      long trackCount = trackRepository.count();
      assertThat(trackCount).isGreaterThanOrEqualTo(10_000L);

      // Analyze tables so Postgres planner has accurate statistics for GIN indexes
      jdbcTemplate.getJdbcOperations().execute("ANALYZE track; ANALYZE artist;");

      // Warm-up queries
      searchService.searchTracks("son tung", 0, 10);
      searchService.searchTracks("mua", 0, 10);

      String[] testQueries = {
        "son tung", "mua", "lac troi", "em", "ngay", "viet nam", "anh", "nang"
      };
      List<Long> latencies = new ArrayList<>();

      for (int i = 0; i < 50; i++) {
        String q = testQueries[i % testQueries.length];
        long start = System.nanoTime();
        TrackPageDto res = searchService.searchTracks(q, 0, 10);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        latencies.add(elapsedMs);
        assertThat(res.getItems()).isNotNull();
      }

      Collections.sort(latencies);
      int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
      long p95Latency = latencies.get(Math.max(0, p95Index));

      assertThat(p95Latency)
          .withFailMessage(
              "p95 latency (%d ms) with %d tracks should be < 100 ms", p95Latency, trackCount)
          .isLessThan(100L);
    } finally {
      cleanDatabase();
    }
  }

  @Test
  @DisplayName(
      "AC 1/4: Large page number (e.g. Integer.MAX_VALUE) returns empty page without arithmetic overflow")
  void largePageReturnsEmptyPageWithoutOverflow() {
    // Seed at least one enabled track and one enabled playlist so total > 0,
    // ensuring the query verifies offset arithmetic with non-empty tables.
    trackRepository.saveAndFlush(
        Track.builder()
            .id(UUID.randomUUID())
            .title("Cơn Mưa Ngang Qua")
            .titleNormalized(TitleNormalizer.normalizeTitle("Cơn Mưa Ngang Qua"))
            .primaryArtistId(sonTung.getId())
            .provider(MusicProvider.DEEZER)
            .providerTrackId("trk-st-large-page")
            .enabled(true)
            .popularityRank(1)
            .build());

    playlistRepository.saveAndFlush(
        Playlist.builder()
            .id(UUID.randomUUID())
            .code("vpop-hits-large-page")
            .nameVi("V-Pop Tuyển Chọn")
            .nameEn("V-Pop Hits")
            .kind(PlaylistKind.CURATED)
            .enabled(true)
            .build());

    TrackPageDto trackPage = searchService.searchTracks("son tung", Integer.MAX_VALUE, 10);
    assertThat(trackPage.getItems()).isEmpty();
    assertThat(trackPage.getPage()).isEqualTo(Integer.MAX_VALUE);
    assertThat(trackPage.getTotalItems()).isGreaterThanOrEqualTo(1L);

    PlaylistPageDto playlistPage = searchService.listPlaylists(Integer.MAX_VALUE, 10);
    assertThat(playlistPage.getItems()).isEmpty();
    assertThat(playlistPage.getPage()).isEqualTo(Integer.MAX_VALUE);
    assertThat(playlistPage.getTotalItems()).isGreaterThanOrEqualTo(1L);
  }

  private void seed10kTracks() {
    int artistCount = 1_000;
    int totalTracks = 10_000;

    List<UUID> artistIds = new ArrayList<>(artistCount);
    for (int i = 0; i < artistCount; i++) {
      artistIds.add(UUID.randomUUID());
    }

    String insertArtistSql =
        """
        INSERT INTO artist (id, name, name_normalized, provider, provider_artist_id, created_at)
        VALUES (?, ?, ?, 'DEEZER', ?, ?)
        """;

    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate
        .getJdbcOperations()
        .batchUpdate(
            insertArtistSql,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int i) throws SQLException {
                UUID id = artistIds.get(i);
                String name = "Artist " + i + " " + (i % 2 == 0 ? "V-Pop" : "M-TP");
                String nameNorm = TitleNormalizer.normalizeArtist(name);
                ps.setObject(1, id);
                ps.setString(2, name);
                ps.setString(3, nameNorm);
                ps.setString(4, "batch-art-" + i);
                ps.setTimestamp(5, now);
              }

              @Override
              public int getBatchSize() {
                return artistCount;
              }
            });

    String insertTrackSql =
        """
        INSERT INTO track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, genre_id, country_code, release_year, popularity_rank, enabled, created_at)
        VALUES (?, 'DEEZER', ?, ?, ?, ?, ?, 'VN', 2022, ?, true, ?)
        """;

    String[] sampleWords = {
      "Mưa",
      "Nắng",
      "Lạc Trôi",
      "Em Của Ngày Hôm Qua",
      "Chạy Ngay Đi",
      "Bước Qua Mùa Cô Đơn",
      "Gió",
      "Yêu",
      "Nhớ",
      "Thương"
    };

    jdbcTemplate
        .getJdbcOperations()
        .batchUpdate(
            insertTrackSql,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int i) throws SQLException {
                UUID trackId = UUID.randomUUID();
                UUID artistId = artistIds.get(i % artistCount);
                String word = sampleWords[i % sampleWords.length];
                String title = word + " " + i;
                String titleNorm = TitleNormalizer.normalizeTitle(title);

                ps.setObject(1, trackId);
                ps.setString(2, "batch-trk-" + i);
                ps.setString(3, title);
                ps.setString(4, titleNorm);
                ps.setObject(5, artistId);
                ps.setObject(6, vpop.getId());
                ps.setInt(7, (i % 100) + 1);
                ps.setTimestamp(8, now);
              }

              @Override
              public int getBatchSize() {
                return totalTracks;
              }
            });
  }
}
