package com.meloo.catalog.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeMusicProviderTest {

  private FakeMusicProvider provider;

  @BeforeEach
  void setUp() {
    provider = new FakeMusicProvider();
  }

  @Test
  @DisplayName("provider name is FAKE")
  void providerNameIsFake() {
    assertThat(provider.getProviderName()).isEqualTo("FAKE");
  }

  @Test
  @DisplayName("mediaLocation defaults to classpath:fake-media/ and can be configured")
  void mediaLocation_configurable() {
    assertThat(provider.getMediaLocation()).isEqualTo("classpath:fake-media/");

    FakeMusicProvider custom = new FakeMusicProvider("/custom-location/");
    assertThat(custom.getMediaLocation()).isEqualTo("/custom-location/");
  }

  @Test
  @DisplayName("contains both tracks with coverUrl and tracks without coverUrl (empty cover)")
  void tracksWithAndWithoutCovers() {
    List<ProviderTrack> all = provider.search("*", 100);

    List<ProviderTrack> withCover =
        all.stream().filter(t -> t.coverUrl() != null && !t.coverUrl().isBlank()).toList();
    List<ProviderTrack> withoutCover =
        all.stream().filter(t -> t.coverUrl() == null || t.coverUrl().isBlank()).toList();

    assertThat(withCover).isNotEmpty();
    assertThat(withoutCover).isNotEmpty();

    // fake-2 and fake-4 specifically do not have covers
    assertThat(provider.getTrack("fake-2")).isPresent();
    assertThat(provider.getTrack("fake-2").get().coverUrl()).isNull();

    assertThat(provider.getTrack("fake-4")).isPresent();
    assertThat(provider.getTrack("fake-4").get().coverUrl()).isNull();

    // fake-1 has cover
    assertThat(provider.getTrack("fake-1")).isPresent();
    assertThat(provider.getTrack("fake-1").get().coverUrl()).isNotBlank();
  }

  @Test
  @DisplayName("search filters by title or artistName case-insensitively")
  void search_filtersCorrectly() {
    List<ProviderTrack> seeTinh = provider.search("see tình", 10);
    assertThat(seeTinh).hasSize(1);
    assertThat(seeTinh.getFirst().providerTrackId()).isEqualTo("fake-1");

    List<ProviderTrack> mono = provider.search("mono", 10);
    assertThat(mono).hasSize(1);
    assertThat(mono.getFirst().providerTrackId()).isEqualTo("fake-2");

    List<ProviderTrack> all = provider.search("", 10);
    assertThat(all).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  @DisplayName("getTrack returns track or empty optional")
  void getTrack_byId() {
    Optional<ProviderTrack> track = provider.getTrack("fake-1");
    assertThat(track).isPresent();
    assertThat(track.get().title()).isEqualTo("See Tình");

    Optional<ProviderTrack> missing = provider.getTrack("non-existent");
    assertThat(missing).isEmpty();
  }

  @Test
  @DisplayName("chart returns tracks ordered by popularity rank")
  void chart_orderedByRank() {
    List<ProviderTrack> chart = provider.chart(3);
    assertThat(chart).hasSize(3);
    assertThat(chart.get(0).popularityRank()).isEqualTo(1);
    assertThat(chart.get(1).popularityRank()).isEqualTo(2);
    assertThat(chart.get(2).popularityRank()).isEqualTo(3);
  }

  @Test
  @DisplayName("artistTopTracks returns tracks matching artist")
  void artistTopTracks_filtersArtist() {
    List<ProviderTrack> tracks = provider.artistTopTracks("art-1", 5);
    assertThat(tracks).hasSize(1);
    assertThat(tracks.getFirst().artistName()).isEqualTo("Hoàng Thùy Linh");
  }

  @Test
  @DisplayName("sample mp3 files exist in classpath fake-media/")
  void sampleMp3FilesExistInClasspath() throws Exception {
    String[] samples = {
      "/fake-media/sample1.mp3", "/fake-media/sample2.mp3", "/fake-media/sample3.mp3"
    };
    for (String sample : samples) {
      try (InputStream stream = getClass().getResourceAsStream(sample)) {
        assertThat(stream).as("Classpath resource %s must exist", sample).isNotNull();
        byte[] bytes = stream.readNBytes(100);
        assertThat(bytes.length).isGreaterThan(10);
      }
    }
  }
}
