package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.ProviderTrack;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CatalogImportServiceIntegrationTest extends IntegrationTest {

  @Autowired private CatalogImportService importService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private com.meloo.catalog.provider.MusicProvider musicProvider;

  @Test
  @DisplayName("Import from chart skips tracks without preview and saves coverUrl")
  void importFromChart_skipsNoPreviewAndSavesCover() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    ProviderTrack validTrack =
        ProviderTrack.builder()
            .providerTrackId("dz-chart-1")
            .title("Chúng Ta Của Tương Lai (Remix)")
            .artistName("Sơn Tùng M-TP")
            .artistId("dz-art-st")
            .previewUrl("https://cdns.dzcdn.net/preview-1.mp3")
            .coverUrl("https://e-cdns.dzcdn.net/cover-1.jpg")
            .countryCode("VN")
            .releaseYear((short) 2024)
            .popularityRank(90)
            .build();

    ProviderTrack noPreviewTrack =
        ProviderTrack.builder()
            .providerTrackId("dz-chart-2")
            .title("No Preview Song")
            .artistName("Unknown Artist")
            .previewUrl(null) // missing preview!
            .coverUrl("https://e-cdns.dzcdn.net/cover-2.jpg")
            .build();

    ProviderTrack blankPreviewTrack =
        ProviderTrack.builder()
            .providerTrackId("dz-chart-3")
            .title("Blank Preview Song")
            .artistName("Unknown Artist")
            .previewUrl("   ") // blank preview!
            .build();

    when(musicProvider.chart(anyInt()))
        .thenReturn(List.of(validTrack, noPreviewTrack, blankPreviewTrack));

    CatalogImportService.ImportResult result = importService.importFromChart(10, null, "VN");

    assertThat(result.totalProcessed()).isEqualTo(3);
    assertThat(result.importedCount()).isEqualTo(1);
    assertThat(result.skippedNoPreviewCount()).isEqualTo(2);
    assertThat(result.duplicateCount()).isEqualTo(0);

    Track savedTrack =
        entityManager
            .createQuery("select t from Track t where t.providerTrackId = :pid", Track.class)
            .setParameter("pid", "dz-chart-1")
            .getSingleResult();

    assertThat(savedTrack.getTitle()).isEqualTo("Chúng Ta Của Tương Lai (Remix)");
    assertThat(savedTrack.getTitleNormalized()).isEqualTo("chung ta cua tuong lai");
    assertThat(savedTrack.getCoverUrl()).isEqualTo("https://e-cdns.dzcdn.net/cover-1.jpg");
    assertThat(savedTrack.getCountryCode()).isEqualTo("VN");
    assertThat(savedTrack.getReleaseYear()).isEqualTo((short) 2024);
    assertThat(savedTrack.isEnabled()).isTrue();

    Artist savedArtist = artistRepository.findById(savedTrack.getPrimaryArtistId()).orElseThrow();
    assertThat(savedArtist.getName()).isEqualTo("Sơn Tùng M-TP");
    assertThat(savedArtist.getNameNormalized()).isEqualTo("son tung m-tp");
  }

  @Test
  @DisplayName(
      "Importing duplicate tracks does not recreate them (idempotent duplicate prevention)")
  void importTracks_duplicatePrevention() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    ProviderTrack track1 =
        ProviderTrack.builder()
            .providerTrackId("dz-dup-1")
            .title("Đừng Làm Trái Tim Anh Đau")
            .artistName("Sơn Tùng M-TP")
            .artistId("dz-art-st")
            .previewUrl("https://cdns.dzcdn.net/preview-dup.mp3")
            .coverUrl("https://e-cdns.dzcdn.net/cover-dup.jpg")
            .build();

    // First import
    CatalogImportService.ImportResult result1 =
        importService.importTracks(List.of(track1), null, "VN");
    assertThat(result1.importedCount()).isEqualTo(1);
    assertThat(result1.duplicateCount()).isEqualTo(0);

    long trackCountBefore = trackRepository.count();
    long artistCountBefore = artistRepository.count();

    // Second import with same track ID
    CatalogImportService.ImportResult result2 =
        importService.importTracks(List.of(track1), null, "VN");
    assertThat(result2.importedCount()).isEqualTo(0);
    assertThat(result2.duplicateCount()).isEqualTo(1);

    assertThat(trackRepository.count()).isEqualTo(trackCountBefore);
    assertThat(artistRepository.count()).isEqualTo(artistCountBefore);
  }

  @Test
  @DisplayName("Importing track without cover leaves cover_url null")
  void importTrack_withoutCover_leavesCoverUrlNull() {
    when(musicProvider.getProviderName()).thenReturn("deezer");

    ProviderTrack trackNoCover =
        ProviderTrack.builder()
            .providerTrackId("dz-no-cover-1")
            .title("Song Without Cover")
            .artistName("Indie Artist")
            .previewUrl("https://cdns.dzcdn.net/preview-nocover.mp3")
            .coverUrl(null)
            .build();

    CatalogImportService.ImportResult result =
        importService.importTracks(List.of(trackNoCover), null, "VN");

    assertThat(result.importedCount()).isEqualTo(1);

    Track saved =
        entityManager
            .createQuery("select t from Track t where t.providerTrackId = :pid", Track.class)
            .setParameter("pid", "dz-no-cover-1")
            .getSingleResult();

    assertThat(saved.getCoverUrl()).isNull();
  }
}
