package com.meloo.chat.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * BR-05, BR-16: Detects answer leaks in chat messages during active rounds. Normalizes title per
 * BR-05 (removes brackets, version tags, feat, diacritics, lowercase) and strips all whitespace and
 * special characters. Normalizes the message identically to catch evasion attempts (no diacritics,
 * inserted spaces, mixed case, punctuation).
 */
@Component
public class ChatLeakDetector {

  private static final Pattern BRACKET_PATTERN =
      Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}");

  private static final Pattern VERSION_SUFFIX_PATTERN =
      Pattern.compile(
          "\\s*[-–—/]\\s*.*\\b(remix|live|acoustic|cover|instrumental|version|ver\\b|ver\\.|karaoke|official.*|audio|mv|sped\\s*up|slowed)\\b.*$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern FEAT_PATTERN =
      Pattern.compile(
          "\\b(feat\\.|featuring|ft\\.|feat\\b|ft\\b)\\s+.*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern INLINE_VERSION_PATTERN =
      Pattern.compile(
          "\\b(remix|live|acoustic|cover|instrumental|sped\\s*up|slowed)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern DIACRITICS_PATTERN =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

  /** Normalizes a song title per BR-05 and strips whitespace/special chars for leak detection. */
  public String normalizeAnswerKey(String title) {
    if (title == null || title.isBlank()) {
      return "";
    }

    String result = title.trim();
    result = BRACKET_PATTERN.matcher(result).replaceAll(" ");
    result = VERSION_SUFFIX_PATTERN.matcher(result).replaceAll(" ");
    result = FEAT_PATTERN.matcher(result).replaceAll(" ");
    result = INLINE_VERSION_PATTERN.matcher(result).replaceAll(" ");

    result = cleanToAlphanumeric(result);
    if (result.isEmpty()) {
      result = cleanToAlphanumeric(title);
    }
    return result;
  }

  /** Normalizes a chat message by stripping diacritics, case, spaces, and punctuation. */
  public String normalizeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "";
    }
    return cleanToAlphanumeric(message);
  }

  /**
   * Checks whether the message leaks the target song title.
   *
   * @param message the chat message content
   * @param answerTitle the song title from the active round (if any)
   * @return true if the message leaks the answer
   */
  public boolean isLeakingAnswer(String message, String answerTitle) {
    if (answerTitle == null || answerTitle.isBlank()) {
      return false;
    }
    String answerKey = normalizeAnswerKey(answerTitle);
    if (answerKey.isEmpty()) {
      return false;
    }
    String normalizedMessage = normalizeMessage(message);
    if (normalizedMessage.isEmpty()) {
      return false;
    }

    if (answerKey.length() == 1) {
      // Avoid false positive single-character matching across the entire message
      return normalizedMessage.equals(answerKey);
    }

    return normalizedMessage.contains(answerKey);
  }

  private static String cleanToAlphanumeric(String input) {
    String replaced = input.replace('đ', 'd').replace('Đ', 'd');
    String nfd = Normalizer.normalize(replaced, Normalizer.Form.NFD);
    String stripped = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
    String lower = stripped.toLowerCase(Locale.ROOT);
    return NON_ALPHANUMERIC.matcher(lower).replaceAll("");
  }
}
