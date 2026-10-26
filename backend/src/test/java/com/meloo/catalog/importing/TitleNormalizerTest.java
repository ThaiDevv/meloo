package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TitleNormalizerTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "Chúng Ta Của Tương Lai | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai (Remix) | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai [Official MV] | chung ta cua tuong lai",
        "Chạy Ngay Đi - Onionn Remix | chay ngay di",
        "Em Của Ngày Hôm Qua (Live at Sky Tour) | em cua ngay hom qua",
        "Đừng Làm Trái Tim Anh Đau | dung lam trai tim anh dau",
        "Shape of You (feat. Stormzy) [Remix] | shape of you",
        "Shape of You feat. Stormzy | shape of you",
        "Song ft. Someone | song",
        "Track Name featuring Famous Singer | track name",
        "Blinding Lights - Acoustic | blinding lights",
        "Waiting For You (Speed Up Version) | waiting for you",
        "Nơi Này Có Anh - Live Version | noi nay co anh",
        "T-Shirt | t shirt",
        "봄날 (Spring Day) | 봄날"
      })
  @DisplayName("BR-05: Title normalizer strips accents, brackets, and version suffixes")
  void normalizeTitle_groupsRemixesAndCovers(String input, String expected) {
    assertThat(TitleNormalizer.normalizeTitle(input)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Handles null and blank titles safely")
  void normalizeTitle_nullOrBlank() {
    assertThat(TitleNormalizer.normalizeTitle(null)).isEmpty();
    assertThat(TitleNormalizer.normalizeTitle("")).isEmpty();
    assertThat(TitleNormalizer.normalizeTitle("   ")).isEmpty();
  }

  @Test
  @DisplayName("Artist name normalization preserves hyphens and strips accents")
  void normalizeArtist() {
    assertThat(TitleNormalizer.normalizeArtist("Sơn Tùng M-TP")).isEqualTo("son tung m-tp");
    assertThat(TitleNormalizer.normalizeArtist("Đen Vâu")).isEqualTo("den vau");
    assertThat(TitleNormalizer.normalizeArtist("The Weeknd")).isEqualTo("the weeknd");
    assertThat(TitleNormalizer.normalizeArtist(null)).isEmpty();
  }
}
