package com.meloo.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerIntegrationTest.TestConfigurationForErrors.class)
class GlobalExceptionHandlerIntegrationTest extends IntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;

  @Test
  void invalidRequestParameterRemainsBadRequestProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test/typed").param("value", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errorCode").value("HTTP_400"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void deniedMethodRemainsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/test/admin")
                .with(SecurityMockMvcRequestPostProcessors.user("member").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedMethodRemainsUnauthorized() throws Exception {
    mockMvc.perform(get("/test/authenticated")).andExpect(status().isUnauthorized());
  }

  @Test
  void unsupportedMethodRemainsMethodNotAllowedProblemDetail() throws Exception {
    mockMvc
        .perform(post("/test/method"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.errorCode").value("HTTP_405"));
  }

  @Test
  void unsupportedMediaTypeRemainsUnsupportedMediaTypeProblemDetail() throws Exception {
    mockMvc
        .perform(post("/test/media").contentType(MediaType.TEXT_PLAIN).content("unsupported"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.errorCode").value("HTTP_415"));
  }

  @Test
  void responseStatusExceptionKeepsItsStatusAndUsesSafeProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("HTTP_409"))
        .andExpect(jsonPath("$.detail").value("The request could not be completed"));
  }

  @Test
  void moduleCanEnableBearerJwtAuthenticationWithoutChangingSharedSecurityConfig()
      throws Exception {
    mockMvc
        .perform(get("/test/bearer").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("test-user"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfigurationForErrors {

    @Bean
    ModuleSecurityRules testHttpRules() {
      return new ModuleSecurityRules() {
        @Override
        public List<HttpRule> httpRules() {
          return List.of(HttpRule.permitAll(null, "/test/**"));
        }

        @Override
        public boolean oauth2ResourceServerEnabled() {
          return true;
        }

        @Override
        public void configureOAuth2ResourceServer(
            OAuth2ResourceServerConfigurer<HttpSecurity> resourceServer) {
          resourceServer.jwt(Customizer.withDefaults());
        }
      };
    }

    @Bean
    JwtDecoder testJwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "test")
              .subject("test-user")
              .claim("scope", "test")
              .build();
    }

    @Bean
    TestController testController() {
      return new TestController();
    }
  }

  @RestController
  static class TestController {

    @GetMapping("/test/typed")
    String typed(@RequestParam int value) {
      return Integer.toString(value);
    }

    @GetMapping("/test/method")
    String getOnly() {
      return "ok";
    }

    @PostMapping(value = "/test/media", consumes = MediaType.APPLICATION_JSON_VALUE)
    String jsonBody(@RequestBody String body) {
      return body;
    }

    @GetMapping("/test/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
      return "ok";
    }

    @GetMapping("/test/authenticated")
    @PreAuthorize("isAuthenticated()")
    String authenticated() {
      return "ok";
    }

    @GetMapping("/test/conflict")
    String conflict() {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "sensitive detail");
    }

    @GetMapping("/test/bearer")
    String bearer(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
      return jwt.getSubject();
    }
  }
}
