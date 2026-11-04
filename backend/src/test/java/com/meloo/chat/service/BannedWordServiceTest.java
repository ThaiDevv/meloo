package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meloo.chat.config.ChatProperties;
import com.meloo.chat.persistence.BannedWord;
import com.meloo.chat.persistence.BannedWordRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BannedWordServiceTest {

  private BannedWordRepository repository;
  private BannedWordService service;

  @BeforeEach
  void setUp() {
    repository = mock(BannedWordRepository.class);
    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 50);

    when(repository.findAll())
        .thenReturn(
            List.of(
                createBannedWord("địt"),
                createBannedWord("khốn nạn"),
                createBannedWord("ngu"),
                createBannedWord("dm"),
                createBannedWord("đm"),
                createBannedWord("vl")));

    service = new BannedWordService(repository, properties);
  }

  private BannedWord createBannedWord(String word) {
    return BannedWord.builder().id(UUID.randomUUID()).word(word).createdAt(Instant.now()).build();
  }

  @Test
  @DisplayName("Detects exact banned words and multi-word phrases (BR-17)")
  void detectsExactMatches() {
    assertThat(service.containsBannedWord("thằng này ngu thế")).isTrue();
    assertThat(service.containsBannedWord("đồ khốn nạn")).isTrue();
    assertThat(service.containsBannedWord("địt mẹ mày")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "khon nan", // không dấu
        "KhỐn NạN", // viết hoa xen kẽ
        "k h o n   n a n", // chèn dấu cách
        "k-h-o-n  n-a-n", // ký tự lạ xen kẽ
        "đ.m", // ký tự đặc biệt lách viết tắt
        "d.m", // không dấu lách viết tắt
        "v-l", // dấu gạch ngang lách viết tắt
        "v l", // dấu cách lách viết tắt
        "đ   ị   t", // dấu cách từng ký tự
        "d i t", // không dấu và cách từng ký tự
        "khốn n ạ n" // cách chữ từng phần trong cụm từ cấm
      })
  @DisplayName(
      "Detects evasion tricks for banned words: no accents, spaces, case, punctuation (BR-17)")
  void detectsEvasionTricks(String text) {
    assertThat(service.containsBannedWord(text)).isTrue();
  }

  @Test
  @DisplayName("Does not produce false positives on words containing banned substrings")
  void avoidsFalsePositives() {
    // "ngu" is banned, but "người", "nguồn", "phòng ngủ" must NOT be blocked!
    assertThat(service.containsBannedWord("chào mọi người")).isFalse();
    assertThat(service.containsBannedWord("nguồn bài hát từ Deezer")).isFalse();
    assertThat(service.containsBannedWord("đi vào phòng ngủ")).isFalse();
    assertThat(service.containsBannedWord("hôm nay trời đẹp")).isFalse();
  }

  @Test
  @DisplayName("Uses in-memory cache and reloads after invalidation (BR-17)")
  void cacheAndInvalidation() {
    // First call reads from DB
    assertThat(service.containsBannedWord("thằng này ngu")).isTrue();
    verify(repository, times(1)).findAll();

    // Second call within 5m uses cache, no DB query
    assertThat(service.containsBannedWord("đồ khốn nạn")).isTrue();
    verify(repository, times(1)).findAll();

    // After invalidation (e.g. admin updated banned words), reloads DB
    service.invalidateCache();
    assertThat(service.containsBannedWord("thằng này ngu")).isTrue();
    verify(repository, times(2)).findAll();
  }

  @Test
  @DisplayName("Safely handles null and blank input")
  void handlesNullAndBlank() {
    assertThat(service.containsBannedWord(null)).isFalse();
    assertThat(service.containsBannedWord("")).isFalse();
    assertThat(service.containsBannedWord("   ")).isFalse();
  }
}
