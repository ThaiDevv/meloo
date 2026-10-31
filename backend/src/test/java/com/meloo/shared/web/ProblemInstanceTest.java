package com.meloo.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemInstanceTest {

  private static final URI MASKED_AUDIO_URI = URI.create("/api/v1/audio/%7Btoken%7D");

  static Stream<Arguments> uriSanitizationCases() {
    return Stream.of(
        Arguments.of("/api/v1/audio/valid-opaque-token-12345", MASKED_AUDIO_URI),
        Arguments.of("/api/v1/audio/valid-opaque-token-12345/x", MASKED_AUDIO_URI),
        Arguments.of("/api/v1/audio/valid-opaque-token-12345/extra/path", MASKED_AUDIO_URI),
        Arguments.of("/api/v1/audio/", MASKED_AUDIO_URI),
        Arguments.of("/api/v1/me", URI.create("/api/v1/me")),
        Arguments.of("/api/v1/rooms", URI.create("/api/v1/rooms")),
        Arguments.of("/api/v1/auth/guest", URI.create("/api/v1/auth/guest")),
        Arguments.of("", URI.create("")),
        Arguments.of(null, URI.create("")));
  }

  @ParameterizedTest(name = "{index}: URI \"{0}\" -> instance \"{1}\"")
  @MethodSource("uriSanitizationCases")
  @DisplayName(
      "ProblemInstance.of(String) correctly masks sensitive audio paths while preserving other paths")
  void of_stringUri_sanitizesAccordingToRules(String inputUri, URI expectedUri) {
    URI result = ProblemInstance.of(inputUri);
    assertThat(result).isEqualTo(expectedUri);
  }

  @Test
  @DisplayName("ProblemInstance.of(HttpServletRequest) resolves sanitized URI from request")
  void of_httpServletRequest_sanitizesAudioUri() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/audio/secret-token-xyz");
    URI result = ProblemInstance.of(request);
    assertThat(result).isEqualTo(MASKED_AUDIO_URI);
  }

  @Test
  @DisplayName("ProblemInstance.of(HttpServletRequest) preserves normal endpoint URIs")
  void of_httpServletRequest_preservesNormalEndpoint() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    URI result = ProblemInstance.of(request);
    assertThat(result).isEqualTo(URI.create("/api/v1/me"));
  }

  @Test
  @DisplayName("ProblemInstance.of(HttpServletRequest) handles null request gracefully")
  void of_nullHttpServletRequest_returnsEmptyUriWithoutException() {
    URI result = ProblemInstance.of((HttpServletRequest) null);
    assertThat(result).isEqualTo(URI.create(""));
  }

  @Test
  @DisplayName("ProblemInstance.of(String) handles malformed URI strings gracefully")
  void of_malformedUri_returnsEmptyUriWithoutException() {
    URI result = ProblemInstance.of("http://invalid path with spaces");
    assertThat(result).isEqualTo(URI.create(""));
  }
}
