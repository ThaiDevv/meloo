package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TitleNormalizerTest {

  @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
  @CsvSource(
      delimiter = '|',
      value = {
        "Chúng Ta Của Tương Lai | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai (Remix) | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai [Remix] | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai {Remix} | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai - Remix | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai - Cukak Remix | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai (feat. Ben) | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai feat. Ben | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai ft. Ben | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai featuring Ben | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai (Live at Hanoi) | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai - Live Version | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai (Acoustic Version) | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai - Acoustic | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai [Cover] | chung ta cua tuong lai",
        "Chúng Ta Của Tương Lai - Instrumental | chung ta cua tuong lai",
        "Đừng Làm Trái Tim Anh Đau | dung lam trai tim anh dau",
        "Đừng Làm Trái Tim Anh Đau (Official MV) | dung lam trai tim anh dau",
        "Nơi Này Có Anh (Sped Up) | noi nay co anh",
        "See Tình | see tinh",
        "See Tình (Speed Up Version) | see tinh",
        "Em Của Ngày Hôm Qua | em cua ngay hom qua",
        "Shape of You (Stormzy Remix) | shape of you",
        "Shape of You feat. Stormzy | shape of you",
        "Shape of You - Acoustic Cover | shape of you",
        "봄날 | 봄날",
        "봄날 (Spring Day) | 봄날",
        "夜に駆ける | 夜に駆ける",
        "ありがとう (Thank You) | ありがとう",
        "Группа крови | группа крови",
        "(Untitled) | untitled"
      })
  @DisplayName("BR-05: Chuẩn hóa tên bài bỏ dấu, bỏ ngoặc, bỏ tag phiên bản")
  void testNormalizationVariants(String rawTitle, String expectedNormalized) {
    assertThat(TitleNormalizer.normalize(rawTitle)).isEqualTo(expectedNormalized);
  }

  @Test
  @DisplayName("BR-05: Hai phiên bản hoặc cover cùng tên sau chuẩn hóa phải so khớp đúng")
  void testMatchingVersionsAndCovers() {
    String target = "Chúng Ta Của Tương Lai";
    assertThat(TitleNormalizer.matches("Chúng Ta Của Tương Lai (Remix)", target)).isTrue();
    assertThat(TitleNormalizer.matches("chung ta cua tuong lai", target)).isTrue();
    assertThat(TitleNormalizer.matches("Chúng Ta Của Tương Lai - Acoustic Cover", target)).isTrue();
    assertThat(TitleNormalizer.matches("Chúng Ta Của Tương Lai feat. DTAP", target)).isTrue();
    assertThat(TitleNormalizer.matches("CHÚNG TA CỦA TƯƠNG LAI", target)).isTrue();

    // Khác tên bài
    assertThat(TitleNormalizer.matches("Em Của Ngày Hôm Qua", target)).isFalse();

    // Hỗ trợ nhạc quốc tế (Hangul, Kana, Cyrillic)
    assertThat(TitleNormalizer.matches("봄날 (Spring Day)", "봄날")).isTrue();
    assertThat(TitleNormalizer.matches("夜に駆ける (Live)", "夜に駆ける")).isTrue();
    assertThat(TitleNormalizer.matches("Группа крови (Remix)", "Группа крови")).isTrue();
    assertThat(TitleNormalizer.matches("(Untitled)", "(Untitled)")).isTrue();
  }

  @Test
  @DisplayName("BR-06: Chuẩn hóa tên ca sĩ và so khớp ca sĩ chính")
  void testArtistNormalizationAndMatching() {
    assertThat(TitleNormalizer.normalizeArtist("Sơn Tùng M-TP")).isEqualTo("son tung m tp");
    assertThat(TitleNormalizer.normalizeArtist("Son Tung M-TP")).isEqualTo("son tung m tp");
    assertThat(TitleNormalizer.normalizeArtist("Đen Vâu")).isEqualTo("den vau");
    assertThat(TitleNormalizer.normalizeArtist("방탄소년단")).isEqualTo("방탄소년단");
    assertThat(TitleNormalizer.normalizeArtist("YOASOBI")).isEqualTo("yoasobi");
    assertThat(TitleNormalizer.normalizeArtist("Кино")).isEqualTo("кино");

    assertThat(TitleNormalizer.matchesArtist("Sơn Tùng M-TP", "Son Tung M-TP")).isTrue();
    assertThat(TitleNormalizer.matchesArtist("Đen Vâu", "den vau")).isTrue();
    assertThat(TitleNormalizer.matchesArtist("방탄소년단", "방탄소년단")).isTrue();
    assertThat(TitleNormalizer.matchesArtist("Vũ", "Đen Vâu")).isFalse();
  }

  @Test
  @DisplayName("Xử lý chuỗi rỗng và null an toàn")
  void testNullAndBlankInput() {
    assertThat(TitleNormalizer.normalize(null)).isEmpty();
    assertThat(TitleNormalizer.normalize("")).isEmpty();
    assertThat(TitleNormalizer.normalize("   ")).isEmpty();

    assertThat(TitleNormalizer.normalizeArtist(null)).isEmpty();
    assertThat(TitleNormalizer.normalizeArtist("")).isEmpty();

    assertThat(TitleNormalizer.matches(null, "See Tình")).isFalse();
    assertThat(TitleNormalizer.matches("See Tình", null)).isFalse();
  }
}
