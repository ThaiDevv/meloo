package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.shared.api.ApiException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class NicknameValidatorTest {

  private NicknameValidator validator;

  @BeforeEach
  void setUp() {
    IdentityProperties.JwtProperties jwt =
        new IdentityProperties.JwtProperties(
            "0123456789012345678901234567890123456789012345678901234567890123",
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            Duration.ofSeconds(10));
    IdentityProperties properties =
        new IdentityProperties(
            jwt, null, null, List.of("đụ má", "thằng chó", "vcl", "ngu", "lồn", "dm", "vl", "đm"));
    validator = new NicknameValidator(properties);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Meloman",
        "player123",
        "Người Chơi",
        "Tùng_99",
        "abc",
        "Nguyễn Văn A",
        "Nguyen",
        "admin",
        "Vlad",
        "Nguyễn"
      })
  @DisplayName("Valid nicknames pass validation (including common names with banned sub-strings)")
  void validNicknamesPass(String nickname) {
    assertThatCode(() -> validator.validate(nickname)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "ab", "a"})
  @DisplayName("Too short nicknames throw VALIDATION_FAILED (400)")
  void tooShortNicknamesFail(String nickname) {
    assertThatThrownBy(() -> validator.validate(nickname))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              org.assertj.core.api.Assertions.assertThat(apiEx.status())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
              org.assertj.core.api.Assertions.assertThat(apiEx.code())
                  .isEqualTo("VALIDATION_FAILED");
            });
  }

  @Test
  @DisplayName("Too long nickname (> 20 characters) throws VALIDATION_FAILED (400)")
  void tooLongNicknameFails() {
    String longNickname = "123456789012345678901"; // 21 chars
    assertThatThrownBy(() -> validator.validate(longNickname))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              org.assertj.core.api.Assertions.assertThat(apiEx.status())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
              org.assertj.core.api.Assertions.assertThat(apiEx.code())
                  .isEqualTo("VALIDATION_FAILED");
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "đụ má",
        "ĐỤ MÁ",
        "ngu_qua",
        "thằng chó con",
        "VcL",
        "cái lồn",
        "đồ ngu",
        "vl bạn ơi",
        "dm luôn"
      })
  @DisplayName("Nicknames containing banned words throw VALIDATION_FAILED (400)")
  void bannedWordsFail(String nickname) {
    assertThatThrownBy(() -> validator.validate(nickname))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              org.assertj.core.api.Assertions.assertThat(apiEx.status())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
              org.assertj.core.api.Assertions.assertThat(apiEx.code())
                  .isEqualTo("VALIDATION_FAILED");
            });
  }
}
