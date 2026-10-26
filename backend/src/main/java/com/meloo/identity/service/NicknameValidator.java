package com.meloo.identity.service;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.shared.api.ApiException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NicknameValidator {

  private final List<List<String>> bannedWordTokenLists;

  public NicknameValidator(IdentityProperties properties) {
    List<String> rawBannedWords =
        properties.bannedWords() != null ? properties.bannedWords() : List.of();
    this.bannedWordTokenLists =
        rawBannedWords.stream().map(this::tokenize).filter(tokens -> !tokens.isEmpty()).toList();
  }

  public void validate(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname must not be blank");
    }
    String normalizedNfc = Normalizer.normalize(nickname.trim(), Normalizer.Form.NFC);
    int codePoints = normalizedNfc.codePointCount(0, normalizedNfc.length());
    if (codePoints < 3 || codePoints > 20) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Nickname must be between 3 and 20 characters");
    }

    List<String> tokens = tokenize(normalizedNfc);
    for (List<String> bannedTokens : bannedWordTokenLists) {
      if (containsSubsequence(tokens, bannedTokens)) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname contains inappropriate content");
      }
    }
  }

  private List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    String[] parts = normalized.split("[^\\p{L}\\p{N}]+");
    List<String> tokens = new ArrayList<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        tokens.add(part);
      }
    }
    return tokens;
  }

  private boolean containsSubsequence(List<String> source, List<String> target) {
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
}
