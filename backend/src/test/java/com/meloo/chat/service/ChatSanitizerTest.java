package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.chat.config.ChatProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatSanitizerTest {

  private ChatSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 50);
    sanitizer = new ChatSanitizer(properties);
  }

  @Test
  @DisplayName("Accepts normal text within 200 characters")
  void acceptsNormalText() {
    ChatSanitizer.ValidationResult result = sanitizer.validate("Bài này hay quá cả phòng ơi!");
    assertThat(result.valid()).isTrue();
    assertThat(result.sanitizedContent()).isEqualTo("Bài này hay quá cả phòng ơi!");
  }

  @Test
  @DisplayName("Accepts exactly 200 characters and rejects 201 characters (BR-16)")
  void enforcesMaxLength() {
    String str200 = "a".repeat(200);
    ChatSanitizer.ValidationResult passResult = sanitizer.validate(str200);
    assertThat(passResult.valid()).isTrue();

    String str201 = "a".repeat(201);
    ChatSanitizer.ValidationResult failResult = sanitizer.validate(str201);
    assertThat(failResult.valid()).isFalse();
    assertThat(failResult.errorCode()).isEqualTo("INVALID_STATE");
    assertThat(failResult.detail()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
  }

  @Test
  @DisplayName("Rejects null, empty or blank messages")
  void rejectsBlankMessages() {
    assertThat(sanitizer.validate(null).valid()).isFalse();
    assertThat(sanitizer.validate("").valid()).isFalse();
    assertThat(sanitizer.validate("   ").valid()).isFalse();
    assertThat(sanitizer.validate("").errorCode()).isEqualTo("INVALID_STATE");
    assertThat(sanitizer.validate("").detail()).isEqualTo("CHAT_MESSAGE_EMPTY");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script>",
        "<b>In đậm</b>",
        "<img src=x onerror=alert(1)>",
        "<a href=\"https://example.com\">click me</a>",
        "<DIV>test</DIV>",
        "<style>body { display: none; }</style>",
        "xin chào <iframe src=evil.com></iframe>",
        "</p>",
        "<br/>",
        "<span style=\"color:red\">red</span>"
      })
  @DisplayName("Rejects messages containing HTML tags")
  void rejectsHtmlTags(String content) {
    ChatSanitizer.ValidationResult result = sanitizer.validate(content);
    assertThat(result.valid()).isFalse();
    assertThat(result.errorCode()).isEqualTo("INVALID_STATE");
    assertThat(result.detail()).isEqualTo("CHAT_HTML_NOT_ALLOWED");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Yêu bài này ghê <3",
        "Tỉ số hiện tại 3 < 5 rồi",
        "Ôi trời >_<",
        "Đi từ điểm A -> điểm B",
        "x > y and a < b"
      })
  @DisplayName("Allows emoticons and math expressions that are not HTML tags")
  void allowsEmoticonsAndMath(String content) {
    ChatSanitizer.ValidationResult result = sanitizer.validate(content);
    assertThat(result.valid()).isTrue();
  }
}
