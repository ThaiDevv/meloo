package com.meloo.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.generated.model.CreateSoloMatchRequestDto;
import com.meloo.generated.model.GuessRequestDto;
import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import jakarta.validation.Valid;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(JacksonConfigIntegrationTest.TestEndpointConfig.class)
class JacksonConfigIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class TestEndpointConfig {

    @Bean
    ModuleSecurityRules testJacksonSecurityRules() {
      return new ModuleSecurityRules() {
        @Override
        public List<HttpRule> httpRules() {
          return List.of(HttpRule.permitAll(null, "/test/jackson/**"));
        }
      };
    }

    @Bean
    JacksonTestController jacksonTestController() {
      return new JacksonTestController();
    }
  }

  @RestController
  @RequestMapping("/test/jackson")
  static class JacksonTestController {

    @PostMapping("/guess")
    ResponseEntity<String> submitGuess(@Valid @RequestBody GuessRequestDto request) {
      return ResponseEntity.ok(request.getTrackId().toString());
    }

    @PostMapping("/solo-match")
    ResponseEntity<String> createSoloMatch(@Valid @RequestBody CreateSoloMatchRequestDto request) {
      return ResponseEntity.ok(request.getMode().getValue());
    }
  }

  @Test
  @DisplayName("GuessRequestDto: payload hợp lệ trả về HTTP 200")
  void validGuessRequestReturnsOk() throws Exception {
    String validJson =
        """
        {
          "trackId": "3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21"
        }
        """;

    mockMvc
        .perform(
            post("/test/jackson/guess").contentType(MediaType.APPLICATION_JSON).content(validJson))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GuessRequestDto: field lạ bị từ chối và trả về HTTP 400 ProblemDetail")
  void unknownPropertyInGuessRequestReturnsBadRequest() throws Exception {
    String jsonWithUnknownProperty =
        """
        {
          "trackId": "3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21",
          "unexpectedProperty": "maliciousOrUnknown"
        }
        """;

    mockMvc
        .perform(
            post("/test/jackson/guess")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithUnknownProperty))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errorCode").doesNotExist())
        .andExpect(jsonPath("$.title").value("Invalid request"));
  }

  @Test
  @DisplayName("CreateSoloMatchRequestDto: payload hợp lệ trả về HTTP 200")
  void validCreateSoloMatchRequestReturnsOk() throws Exception {
    String validJson =
        """
        {
          "mode": "PRACTICE",
          "roundCount": 5
        }
        """;

    mockMvc
        .perform(
            post("/test/jackson/solo-match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CreateSoloMatchRequestDto: field lạ bị từ chối và trả về HTTP 400 ProblemDetail")
  void unknownPropertyInCreateSoloMatchRequestReturnsBadRequest() throws Exception {
    String jsonWithUnknownProperty =
        """
        {
          "mode": "PRACTICE",
          "roundCount": 5,
          "hackField": "forbiddenValue"
        }
        """;

    mockMvc
        .perform(
            post("/test/jackson/solo-match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithUnknownProperty))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errorCode").doesNotExist())
        .andExpect(jsonPath("$.title").value("Invalid request"));
  }
}
