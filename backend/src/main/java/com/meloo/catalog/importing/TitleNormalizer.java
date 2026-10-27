package com.meloo.catalog.importing;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for normalizing track titles and artist names according to BR-05. BR-05:
 * case-insensitive, unaccented, strips brackets/parentheses and version suffixes (Remix, Live,
 * Acoustic, feat., MV, etc.) to group remixes, live versions, and covers.
 */
public final class TitleNormalizer {

  private static final Pattern BRACKETS_PATTERN =
      Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]", Pattern.CASE_INSENSITIVE);

  private static final Pattern VERSION_SUFFIX_PATTERN =
      Pattern.compile(
          "(?i)\\s+-\\s+.*\\b(remix|live|acoustic|ver|version|cover|instrumental|official.*|mv)\\b.*$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern FEAT_PATTERN =
      Pattern.compile("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern DIACRITICS_PATTERN =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}\\s]+");

  private static final Pattern ARTIST_NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}\\s\\-]+");

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private TitleNormalizer() {}

  /**
   * Normalizes a song/track title per BR-05: 1. Remove text inside parentheses (...) and brackets
   * [...] 2. Strip trailing version qualifiers like "- ... Remix", "- Live", "feat. ..." 3. Convert
   * to lowercase 4. Remove diacritics (including Vietnamese đ/Đ -> d) 5. Remove special
   * punctuation, collapse whitespace and trim
   */
  public static String normalizeTitle(String title) {
    if (title == null || title.isBlank()) {
      return "";
    }

    String result = title.trim();

    // 1. Remove bracketed text: (Remix), [Official MV], (feat. ABC), etc.
    result = BRACKETS_PATTERN.matcher(result).replaceAll(" ");

    // 2. Remove trailing version suffixes: " - Remix", " - Live 2024", etc.
    result = VERSION_SUFFIX_PATTERN.matcher(result).replaceAll("");

    // 3. Remove " feat. XYZ" or " ft. XYZ"
    result = FEAT_PATTERN.matcher(result).replaceAll("");

    // 4. Diacritics and case
    result = stripDiacritics(result.toLowerCase(Locale.ROOT));

    // 5. Non-alphanumeric characters
    result = NON_ALPHANUMERIC.matcher(result).replaceAll(" ");

    // 6. Normalize whitespace
    return WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim();
  }

  /** Normalizes an artist name: lowercased, diacritics removed, trimmed. */
  public static String normalizeArtist(String artistName) {
    if (artistName == null || artistName.isBlank()) {
      return "";
    }

    String result = stripDiacritics(artistName.trim().toLowerCase(Locale.ROOT));
    result = ARTIST_NON_ALPHANUMERIC.matcher(result).replaceAll(" ");
    return WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim();
  }

  /**
   * Replaces Vietnamese 'đ' / 'Đ' with 'd', decomposes characters, removes diacritical marks, and
   * recomposes remaining characters into canonical NFC form.
   */
  public static String stripDiacritics(String text) {
    if (text == null) {
      return "";
    }
    String dReplaced = text.replace('đ', 'd').replace('Đ', 'd');
    String normalized = Normalizer.normalize(dReplaced, Normalizer.Form.NFD);
    String stripped = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    return Normalizer.normalize(stripped, Normalizer.Form.NFC);
  }
}
