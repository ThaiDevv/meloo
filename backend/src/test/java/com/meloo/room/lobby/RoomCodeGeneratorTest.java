package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

  private RoomCodeGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new RoomCodeGenerator();
  }

  @Test
  @DisplayName("Mã sinh ra phải đúng 6 ký tự và khớp regex [A-HJ-NP-Z2-9]{6}")
  void generatedCodeMatchesRegexAndLength() {
    for (int i = 0; i < 500; i++) {
      String code = generator.generateCode();
      assertThat(code).hasSize(6).matches("^[A-HJ-NP-Z2-9]{6}$");
      assertThat(generator.isValidCode(code)).isTrue();
    }
  }

  @Test
  @DisplayName("Mã không bao giờ chứa 0, O, 1, I, L (BR-11)")
  void generatedCodeExcludesConfusingCharacters() {
    for (int i = 0; i < 500; i++) {
      String code = generator.generateCode();
      assertThat(code).doesNotContain("0", "O", "1", "I", "L");
    }
  }

  @Test
  @DisplayName("generateUniqueCode đảm bảo tính duy nhất qua vị từ kiểm tra")
  void generateUniqueCodeEnsuresUniqueness() {
    Set<String> existing = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      String code = generator.generateUniqueCode(c -> !existing.contains(c));
      assertThat(existing.add(code)).isTrue();
    }
  }

  @Test
  @DisplayName("Ném IllegalStateException nếu không tìm được mã duy nhất sau số lần thử")
  void throwsExceptionWhenCannotFindUnique() {
    assertThatThrownBy(() -> generator.generateUniqueCode(c -> false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to generate a unique room code");
  }

  @Test
  @DisplayName("isValidCode từ chối các mã sai định dạng, chứa ký tự cấm hoặc độ dài khác 6")
  void isValidCodeRejectsInvalidCodes() {
    assertThat(generator.isValidCode(null)).isFalse();
    assertThat(generator.isValidCode("")).isFalse();
    assertThat(generator.isValidCode("ABCDE")).isFalse(); // 5 chars
    assertThat(generator.isValidCode("ABCDEFG")).isFalse(); // 7 chars
    assertThat(generator.isValidCode("ABCD0E")).isFalse(); // Chứa '0'
    assertThat(generator.isValidCode("ABCDOE")).isFalse(); // Chứa 'O'
    assertThat(generator.isValidCode("ABCD1E")).isFalse(); // Chứa '1'
    assertThat(generator.isValidCode("ABCDIE")).isFalse(); // Chứa 'I'
    assertThat(generator.isValidCode("ABCDLE")).isFalse(); // Chứa 'L'
    assertThat(generator.isValidCode("abc234")).isFalse(); // Chữ thường
  }
}
