package com.meloo.game.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * BR-05, BR-06, BR-16: Hàm chuẩn hóa tên bài hát và ca sĩ (hàm thuần trong game/domain). Bỏ dấu
 * tiếng Việt, viết thường, bỏ phần trong ngoặc và phần ghi phiên bản (Remix, Live, Acoustic, feat.
 * …). Mọi phiên bản và bản cover cùng tên đều tính đúng.
 */
public final class TitleNormalizer {

  private static final Pattern BRACKET_PATTERN =
      Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}");

  private static final Pattern FEAT_PATTERN =
      Pattern.compile(
          "\\b(feat\\.|featuring|ft\\.|feat\\b|ft\\b)\\s+.*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern VERSION_SUFFIX_PATTERN =
      Pattern.compile(
          "\\s*[-–—/]\\s*.*\\b(remix|live|acoustic|cover|instrumental|version|ver\\b|ver\\.|karaoke|official.*|audio|mv|sped\\s*up|slowed)\\b.*$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern INLINE_VERSION_PATTERN =
      Pattern.compile(
          "\\b(remix|live|acoustic|cover|instrumental|sped\\s*up|slowed)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern DIACRITICS_PATTERN =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9\\s]");

  private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

  private TitleNormalizer() {}

  /** Chuẩn hóa tên bài hát theo BR-05. */
  public static String normalize(String title) {
    if (title == null || title.isBlank()) {
      return "";
    }

    String result = title.trim();

    // 1. Bỏ phần trong ngoặc tròn, vuông, nhọn
    result = BRACKET_PATTERN.matcher(result).replaceAll(" ");

    // 2. Bỏ phần phiên bản sau dấu gạch (- Remix, - Acoustic Version, v.v.)
    result = VERSION_SUFFIX_PATTERN.matcher(result).replaceAll(" ");

    // 3. Bỏ phần feat. / ft. / featuring cho đến hết chuỗi
    result = FEAT_PATTERN.matcher(result).replaceAll(" ");

    // 4. Bỏ các từ chỉ phiên bản đứng độc lập trong câu
    result = INLINE_VERSION_PATTERN.matcher(result).replaceAll(" ");

    // 5. Thay thế ký tự đ / Đ của tiếng Việt
    result = result.replace('đ', 'd').replace('Đ', 'd');

    // 6. Tách dấu tổ hợp (NFD) và xóa dấu
    result = Normalizer.normalize(result, Normalizer.Form.NFD);
    result = DIACRITICS_PATTERN.matcher(result).replaceAll("");

    // 7. Chuyển chữ thường
    result = result.toLowerCase(Locale.ROOT);

    // 8. Bỏ các ký tự không phải chữ số hoặc khoảng trắng
    result = NON_ALPHANUMERIC_PATTERN.matcher(result).replaceAll(" ");

    // 9. Rút gọn khoảng trắng thừa và trim
    result = MULTI_SPACE_PATTERN.matcher(result).replaceAll(" ").trim();

    return result;
  }

  /** Chuẩn hóa tên ca sĩ theo BR-06. */
  public static String normalizeArtist(String artistName) {
    if (artistName == null || artistName.isBlank()) {
      return "";
    }

    String result = artistName.trim().replace('đ', 'd').replace('Đ', 'd');
    result = Normalizer.normalize(result, Normalizer.Form.NFD);
    result = DIACRITICS_PATTERN.matcher(result).replaceAll("");
    result = result.toLowerCase(Locale.ROOT);
    result = NON_ALPHANUMERIC_PATTERN.matcher(result).replaceAll(" ");
    return MULTI_SPACE_PATTERN.matcher(result).replaceAll(" ").trim();
  }

  /** So khớp đáp án tên bài theo BR-05. */
  public static boolean matches(String guessTitle, String targetTitle) {
    String normGuess = normalize(guessTitle);
    String normTarget = normalize(targetTitle);
    return !normGuess.isEmpty() && normGuess.equals(normTarget);
  }

  /** So khớp ca sĩ chính theo BR-06. */
  public static boolean matchesArtist(String guessArtist, String targetArtist) {
    String normGuess = normalizeArtist(guessArtist);
    String normTarget = normalizeArtist(targetArtist);
    return !normGuess.isEmpty() && normGuess.equals(normTarget);
  }
}
