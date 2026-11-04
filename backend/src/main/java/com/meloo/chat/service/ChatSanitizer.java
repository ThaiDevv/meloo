package com.meloo.chat.service;

import com.meloo.chat.config.ChatProperties;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * BR-16: Validates chat message length (1-200 chars) and strictly rejects HTML tags. Emoticons such
 * as '<3' or math like '3 < 5' are permitted as they are not HTML elements.
 */
@Component
public class ChatSanitizer {

  private static final Pattern HTML_TAG_PATTERN =
      Pattern.compile("<\\s*/?\\s*[a-zA-Z][a-zA-Z0-9]*[^>]*>", Pattern.DOTALL);

  private final int maxMessageLength;

  public ChatSanitizer(ChatProperties properties) {
    Objects.requireNonNull(properties, "properties");
    this.maxMessageLength = properties.maxMessageLength();
  }

  public ValidationResult validate(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.rejected(
          "INVALID_STATE", "Tin nhắn không được để trống", "CHAT_MESSAGE_EMPTY");
    }

    String trimmed = content.trim();
    if (trimmed.length() > maxMessageLength) {
      return ValidationResult.rejected(
          "INVALID_STATE",
          "Tin nhắn vượt quá độ dài tối đa cho phép (" + maxMessageLength + " ký tự)",
          "CHAT_MESSAGE_TOO_LONG");
    }

    if (HTML_TAG_PATTERN.matcher(trimmed).find()) {
      return ValidationResult.rejected(
          "INVALID_STATE",
          "Tin nhắn chỉ là văn bản thuần, không nhận HTML",
          "CHAT_HTML_NOT_ALLOWED");
    }

    return ValidationResult.accepted(trimmed);
  }

  public record ValidationResult(
      boolean valid,
      String sanitizedContent,
      String errorCode,
      String errorMessage,
      String detail) {

    public static ValidationResult accepted(String content) {
      return new ValidationResult(true, content, null, null, null);
    }

    public static ValidationResult rejected(String errorCode, String errorMessage, String detail) {
      return new ValidationResult(false, null, errorCode, errorMessage, detail);
    }
  }
}
