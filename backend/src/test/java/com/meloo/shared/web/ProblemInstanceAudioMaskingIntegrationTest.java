package com.meloo.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.generated.model.AudioClipDto;
import com.meloo.identity.api.AccountLockQuery;
import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(ProblemInstanceAudioMaskingIntegrationTest.TestSecurityConfig.class)
class ProblemInstanceAudioMaskingIntegrationTest extends IntegrationTest {

  // Valid token syntax: 16 to 256 characters [A-Za-z0-9_-]
  private static final String SYNTAX_VALID_TOKEN = "valid_opaque_token_syntax_1234567890_abcdef";
  private static final String AUDIO_PATH = "/api/v1/audio/" + SYNTAX_VALID_TOKEN;
  private static final String MASKED_INSTANCE = "/api/v1/audio/%7Btoken%7D";

  @Autowired private MockMvc mockMvc;
  @Autowired private AudioService audioService;
  @MockitoBean private AccountLockQuery accountLockQuery;

  @Test
  @DisplayName(
      "(1) Invalid Authorization: Bearer triggers ProblemAuthenticationEntryPoint -> 401 with masked instance")
  void getAudio_withInvalidBearer_returns401WithMaskedInstanceAndNoTokenLeak(CapturedOutput output)
      throws Exception {
    mockMvc
        .perform(get(AUDIO_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt-token-xyz"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName(
      "(1b) Percent-encoded audio path with invalid Bearer triggers ProblemAuthenticationEntryPoint -> 401 with masked instance")
  void getAudio_withPercentEncodedPath_returns401WithMaskedInstanceAndNoTokenLeak(
      CapturedOutput output) throws Exception {
    String encodedPath = "/api/v1/%61udio/" + SYNTAX_VALID_TOKEN;
    mockMvc
        .perform(
            get(URI.create(encodedPath))
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt-token-xyz"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName("(2) Untrusted Origin triggers ProblemCorsProcessor -> 403 with masked instance")
  void getAudio_withRejectedOrigin_returns403WithMaskedInstanceAndNoTokenLeak(CapturedOutput output)
      throws Exception {
    mockMvc
        .perform(get(AUDIO_PATH).header(HttpHeaders.ORIGIN, "https://untrusted-attacker.com"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName(
      "(3a) Unsupported POST method triggers GlobalExceptionHandler.createResponseEntity -> 405 with masked instance")
  void postAudio_unsupportedMethod_returns405WithMaskedInstanceAndNoTokenLeak(CapturedOutput output)
      throws Exception {
    mockMvc
        .perform(post(AUDIO_PATH))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Method not allowed"))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName(
      "(3b) Unsupported PUT method triggers GlobalExceptionHandler.createResponseEntity -> 405 with masked instance")
  void putAudio_unsupportedMethod_returns405WithMaskedInstanceAndNoTokenLeak(CapturedOutput output)
      throws Exception {
    mockMvc
        .perform(put(AUDIO_PATH))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Method not allowed"))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName(
      "(4a) Access denied for authenticated caller triggers ProblemAccessDeniedHandler -> 403 with masked instance")
  void deleteAudio_accessDenied_returns403WithMaskedInstanceAndNoTokenLeak(CapturedOutput output)
      throws Exception {
    mockMvc
        .perform(delete(AUDIO_PATH).with(user("test-user").roles("USER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain(SYNTAX_VALID_TOKEN));

    assertThat(output.getAll()).doesNotContain(SYNTAX_VALID_TOKEN);
  }

  @Test
  @DisplayName(
      "(4b) Unexpected exception in controller triggers GlobalExceptionHandler.handleUnexpected -> 500 with masked instance")
  void getAudio_unexpectedException_returns500WithMaskedInstanceAndNoTokenLeak(
      CapturedOutput output) throws Exception {
    UUID roundId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    AudioPreparationResult prep =
        audioService.prepareRoundAudio(roundId, trackId, "FAKE", "fake-1");
    assertThat(prep.playable()).isTrue();

    AudioClipDto clip = audioService.createAudioClip(roundId, playerId, 1);
    String realAudioUrl = clip.getUrl();
    String rawToken = realAudioUrl.substring("/api/v1/audio/".length());

    when(accountLockQuery.isLocked(any()))
        .thenThrow(new RuntimeException("Simulated unexpected database failure"));

    mockMvc
        .perform(get(realAudioUrl))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.instance").value(MASKED_INSTANCE))
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).doesNotContain(rawToken);
              assertThat(body).doesNotContain("Simulated unexpected database failure");
            });

    assertThat(output.getAll()).doesNotContain(rawToken);
    assertThat(output.getAll()).doesNotContain("Simulated unexpected database failure");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestSecurityConfig {
    @Bean
    ModuleSecurityRules testAudioMethodRules() {
      return new ModuleSecurityRules() {
        @Override
        public List<HttpRule> httpRules() {
          return List.of(
              HttpRule.hasAuthority(HttpMethod.DELETE, "ROLE_ADMIN", "/api/v1/audio/*"),
              HttpRule.permitAll(HttpMethod.POST, "/api/v1/audio/*"),
              HttpRule.permitAll(HttpMethod.PUT, "/api/v1/audio/*"));
        }
      };
    }
  }
}
