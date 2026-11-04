package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatLeakDetectorTest {

  private ChatLeakDetector detector;

  @BeforeEach
  void setUp() {
    detector = new ChatLeakDetector();
  }

  @Test
  @DisplayName("Detects exact match of answer title")
  void exactMatch() {
    assertThat(detector.isLeakingAnswer("bài này là Lạc Trôi nè", "Lạc Trôi")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "lac troi", // không dấu
        "l a c   t r o i", // chèn dấu cách
        "lAc TRoI", // viết hoa xen kẽ
        "L-ạ-c_T-r-ô-i", // ký tự đặc biệt xen kẽ
        "bài này chắc chắn là l.a.c  t.r.o.i rồi", // lách trong câu dài
        "LAC TROI", // in hoa toàn bộ không dấu
        "l a c t r o i" // dấu cách từng ký tự
      })
  @DisplayName("Detects evasion tricks: no diacritics, spaces, mixed case, punctuation (BR-16)")
  void detectsEvasions(String message) {
    assertThat(detector.isLeakingAnswer(message, "Lạc Trôi")).isTrue();
  }

  @Test
  @DisplayName("Normalizes answer title per BR-05 (removes bracketed text, version, feat)")
  void normalizesAnswerPerBr05() {
    // Official MV, Remix, Feat should be stripped from answer key
    assertThat(detector.isLeakingAnswer("lac troi", "Lạc Trôi (Official Music Video)")).isTrue();
    assertThat(detector.isLeakingAnswer("con mua ngang qua", "Cơn Mưa Ngang Qua - Remix")).isTrue();
    assertThat(detector.isLeakingAnswer("see tinh", "See Tình (feat. ABC)")).isTrue();
    assertThat(detector.isLeakingAnswer("nang am xa dan", "Nắng Ấm Xa Dần [Acoustic Version]"))
        .isTrue();
  }

  @Test
  @DisplayName("Allows benign messages not containing answer title")
  void allowsBenignMessages() {
    assertThat(detector.isLeakingAnswer("bài này hay quá ae ơi", "Lạc Trôi")).isFalse();
    assertThat(detector.isLeakingAnswer("nghe quen ghê mà chưa nhớ ra", "Lạc Trôi")).isFalse();
    assertThat(detector.isLeakingAnswer("chào cả phòng!", "Lạc Trôi")).isFalse();
  }

  @Test
  @DisplayName("Handles null or blank safely")
  void handlesNullOrBlank() {
    assertThat(detector.isLeakingAnswer(null, "Lạc Trôi")).isFalse();
    assertThat(detector.isLeakingAnswer("bài này hay", null)).isFalse();
    assertThat(detector.isLeakingAnswer("bài này hay", "")).isFalse();
    assertThat(detector.isLeakingAnswer("", "Lạc Trôi")).isFalse();
  }
}
