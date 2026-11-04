package com.meloo.chat.service;

import com.meloo.chat.config.ChatProperties;
import com.meloo.chat.persistence.BannedWord;
import com.meloo.chat.persistence.BannedWordRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * BR-17: Banned words filtering with database backed list and in-memory cache (TTL <= 5 minutes).
 * Detects evasion tricks: no diacritics, spaced letters, mixed case, and punctuation.
 */
@Service
public class BannedWordService {

  private static final Pattern DIACRITICS_PATTERN =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

  private final BannedWordRepository repository;
  private final Duration cacheTtl;
  private final AtomicReference<CachedWords> cache = new AtomicReference<>();

  public BannedWordService(BannedWordRepository repository, ChatProperties properties) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.cacheTtl = properties.bannedWordsCacheTtl();
  }

  public boolean containsBannedWord(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    List<BannedWordEntry> entries = getCachedEntries();
    if (entries.isEmpty()) {
      return false;
    }

    // 1. Tokenize text into lowercased NFC tokens (preserving diacritics)
    List<String> rawTokens = tokenize(text, false);
    // 2. Tokenize text into diacritic-stripped tokens (for catching evasions of accented words)
    List<String> strippedTokens = tokenize(text, true);

    // 3. Build merged single-character acronyms/words from sequences of single letters
    List<String> collapsedTokens = collapseSingleLetterSequences(rawTokens);
    List<String> collapsedStrippedTokens = collapseSingleLetterSequences(strippedTokens);

    for (BannedWordEntry entry : entries) {
      // Subsequence match on raw tokens (e.g. "ngu", or multi-word phrase "khốn nạn")
      if (containsSubsequence(rawTokens, entry.tokens())
          || containsSubsequence(collapsedTokens, entry.tokens())) {
        return true;
      }
      // Check collapsed raw tokens (e.g. "đ.m" -> ["đ", "m"] collapsed to "đm", or "đ ị t" ->
      // "địt")
      if (collapsedTokens.contains(entry.compacted())) {
        return true;
      }

      // If the banned word originally has diacritics, also check diacritic-stripped evasions
      // (e.g. user typed "khon nan" for "khốn nạn", or "dit" for "địt").
      // If the banned word had NO diacritics (e.g. "ngu"), we do NOT match against stripped text
      // to avoid false positives on valid words like "ngủ" (sleep) or "ngũ".
      if (entry.hasDiacritics()) {
        if (containsSubsequence(strippedTokens, entry.strippedTokens())
            || containsSubsequence(collapsedStrippedTokens, entry.strippedTokens())) {
          return true;
        }
        if (collapsedStrippedTokens.contains(entry.strippedCompacted())) {
          return true;
        }
      }
    }

    return false;
  }

  public void invalidateCache() {
    cache.set(null);
  }

  List<BannedWordEntry> getCachedEntries() {
    Instant now = Instant.now();
    CachedWords current = cache.get();
    if (current != null && now.isBefore(current.expiresAt())) {
      return current.entries();
    }

    List<BannedWord> records = repository.findAll();
    List<BannedWordEntry> entries = records.stream().map(this::toEntry).toList();
    cache.set(new CachedWords(entries, now.plus(cacheTtl)));
    return entries;
  }

  private BannedWordEntry toEntry(BannedWord bw) {
    String word = bw.getWord().trim().toLowerCase(Locale.ROOT);
    List<String> tokens = tokenize(word, false);
    List<String> strippedTokens = tokenize(word, true);
    String compacted = String.join("", tokens);
    String strippedCompacted = String.join("", strippedTokens);
    boolean hasDiacritics = !compacted.equals(strippedCompacted);
    return new BannedWordEntry(
        word, tokens, strippedTokens, compacted, strippedCompacted, hasDiacritics);
  }

  private List<String> tokenize(String input, boolean stripDiacritics) {
    if (input == null || input.isBlank()) {
      return List.of();
    }
    String processed = input;
    if (stripDiacritics) {
      processed = processed.replace('đ', 'd').replace('Đ', 'd');
      String nfd = Normalizer.normalize(processed, Normalizer.Form.NFD);
      processed = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
    }
    processed = Normalizer.normalize(processed, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    String[] parts = NON_ALPHANUMERIC.split(processed);
    List<String> tokens = new ArrayList<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  private static boolean containsSubsequence(List<String> source, List<String> target) {
    if (target.isEmpty() || target.size() > source.size()) {
      return false;
    }
    for (int i = 0; i <= source.size() - target.size(); i++) {
      boolean match = true;
      for (int j = 0; j < target.size(); j++) {
        if (!source.get(i + j).equals(target.get(j))) {
          match = false;
          break;
        }
      }
      if (match) {
        return true;
      }
    }
    return false;
  }

  private static List<String> collapseSingleLetterSequences(List<String> tokens) {
    if (tokens.isEmpty()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    StringBuilder currentSeq = new StringBuilder();

    for (String token : tokens) {
      if (token.length() == 1) {
        currentSeq.append(token);
      } else {
        if (currentSeq.length() > 1) {
          result.add(currentSeq.toString());
        }
        currentSeq.setLength(0);
        result.add(token);
      }
    }
    if (currentSeq.length() > 1) {
      result.add(currentSeq.toString());
    }
    return result;
  }

  record BannedWordEntry(
      String rawWord,
      List<String> tokens,
      List<String> strippedTokens,
      String compacted,
      String strippedCompacted,
      boolean hasDiacritics) {}

  private record CachedWords(List<BannedWordEntry> entries, Instant expiresAt) {}
}
