package com.meloo.identity.service;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.shared.api.ApiException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NicknameValidator {

  private final List<String> bannedWords;

  public NicknameValidator(IdentityProperties properties) {
    this.bannedWords =
        properties.bannedWords().stream()
            .map(w -> Normalizer.normalize(w.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFC))
            .filter(w -> !w.isBlank())
            .toList();
  }

  public void validate(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname must not be blank");
    }
    String trimmed = nickname.trim();
    int codePoints = trimmed.codePointCount(0, trimmed.length());
    if (codePoints < 3 || codePoints > 20) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Nickname must be between 3 and 20 characters");
    }

    String normalized = Normalizer.normalize(trimmed.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    for (String banned : bannedWords) {
      if (normalized.contains(banned)) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname contains inappropriate content");
      }
    }
  }
}
