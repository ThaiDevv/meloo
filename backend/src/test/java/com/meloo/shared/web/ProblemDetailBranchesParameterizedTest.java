package com.meloo.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.shared.api.ApiException;
import com.meloo.shared.api.ModuleSecurityRules;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import com.meloo.shared.api.ProblemConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(ProblemDetailBranchesParameterizedTest.TestConfig.class)
class ProblemDetailBranchesParameterizedTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  static Stream<Arguments> branchCases() {
    return Stream.of(
        // 1. Business error (ApiException)
        Arguments.of(
            "GET",
            "/test/branches/business",
            null,
            null,
            null,
            Map.of(),
            409,
            "STATE_CONFLICT",
            "Conflict"),
        // 2. Spring built-in: validation 400
        Arguments.of(
            "POST",
            "/test/branches/validation",
            "{\"name\":\"\"}",
            MediaType.APPLICATION_JSON_VALUE,
            null,
            Map.of(),
            400,
            ProblemConstants.CODE_VALIDATION_FAILED,
            ProblemConstants.TITLE_VALIDATION_FAILED),
        // 3. Spring built-in: malformed JSON body 400
        Arguments.of(
            "POST",
            "/test/branches/json-syntax",
            "{\"name\":",
            MediaType.APPLICATION_JSON_VALUE,
            null,
            Map.of(),
            400,
            ProblemConstants.CODE_VALIDATION_FAILED,
            "Invalid request"),
        // 4. Spring built-in: 404 route not found
        Arguments.of(
            "GET",
            "/test/branches/non-existent-route",
            null,
            null,
            null,
            Map.of(),
            404,
            ProblemConstants.CODE_NOT_FOUND,
            "Resource not found"),
        // 5. Spring built-in: 405 Method Not Allowed
        Arguments.of(
            "DELETE",
            "/test/branches/business",
            null,
            null,
            null,
            Map.of(),
            405,
            ProblemConstants.CODE_METHOD_NOT_ALLOWED,
            "Method not allowed"),
        // 6. Security filter chain: 401 unauthenticated (missing token)
        Arguments.of(
            "GET",
            "/test/branches/secured",
            null,
            null,
            null,
            Map.of(),
            401,
            ProblemConstants.CODE_UNAUTHORIZED,
            "Unauthorized"),
        // 7. Security filter chain: 401 invalid bearer token
        Arguments.of(
            "GET",
            "/test/branches/secured",
            null,
            null,
            null,
            Map.of(HttpHeaders.AUTHORIZATION, "Bearer invalid-garbage-token"),
            401,
            ProblemConstants.CODE_UNAUTHORIZED,
            "Unauthorized"),
        // 8. Security filter chain: 403 access denied
        Arguments.of(
            "GET",
            "/test/branches/admin-only",
            null,
            null,
            "USER",
            Map.of(),
            403,
            ProblemConstants.CODE_FORBIDDEN,
            "Forbidden"),
        // 9. CORS rejection: unauthorized origin
        Arguments.of(
            "GET",
            "/test/branches/business",
            null,
            null,
            null,
            Map.of(HttpHeaders.ORIGIN, "https://unauthorized-origin.example"),
            403,
            ProblemConstants.CODE_FORBIDDEN,
            "Forbidden"),
        // 10. CORS rejection: preflight with unauthorized origin
        Arguments.of(
            "OPTIONS",
            "/test/branches/business",
            null,
            null,
            null,
            Map.of(
                HttpHeaders.ORIGIN,
                "https://unauthorized-origin.example",
                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                "GET"),
            403,
            ProblemConstants.CODE_FORBIDDEN,
            "Forbidden"),
        // 11. Unexpected 500 error
        Arguments.of(
            "GET",
            "/test/branches/unexpected",
            null,
            null,
            null,
            Map.of(),
            500,
            ProblemConstants.CODE_INTERNAL_ERROR,
            "Internal server error"));
  }

  @ParameterizedTest(name = "{index}: {0} {1} -> status {6}, code {7}")
  @MethodSource("branchCases")
  @DisplayName(
      "All REST error response branches produce RFC 9457 ProblemDetail with 4 required fields")
  void testProblemDetailBranches(
      String httpMethod,
      String path,
      String requestBody,
      String contentType,
      String userRole,
      Map<String, String> headers,
      int expectedStatus,
      String expectedCode,
      String expectedTitle)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder = request(HttpMethod.valueOf(httpMethod), path);
    if (requestBody != null) {
      requestBuilder.content(requestBody);
    }
    if (contentType != null) {
      requestBuilder.contentType(contentType);
    }
    headers.forEach(requestBuilder::header);
    if ("ADMIN".equals(userRole)) {
      requestBuilder.with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"));
    } else if ("USER".equals(userRole)) {
      requestBuilder.with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER"));
    }

    ResultActions actions = mockMvc.perform(requestBuilder);

    actions
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value(expectedTitle))
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.errorCode").doesNotExist())
        .andExpect(jsonPath("$.detail").isString())
        .andExpect(jsonPath("$.instance").isNotEmpty());

    if (expectedStatus == 401) {
      actions.andExpect(
          header()
              .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")));
    }
    if (expectedStatus == 500) {
      actions
          .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
          .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
  }

  public static class ValidatedPayload {
    @NotBlank private String name;

    public ValidatedPayload() {}

    public ValidatedPayload(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    ModuleSecurityRules branchesSecurityRules() {
      return new ModuleSecurityRules() {
        @Override
        public List<HttpRule> httpRules() {
          return List.of(
              HttpRule.authenticated(HttpMethod.GET, "/test/branches/secured"),
              HttpRule.hasAuthority(HttpMethod.GET, "ROLE_ADMIN", "/test/branches/admin-only"),
              HttpRule.permitAll(null, "/test/branches/**"));
        }
      };
    }

    @Bean
    BranchesTestController branchesTestController() {
      return new BranchesTestController();
    }
  }

  @RestController
  static class BranchesTestController {

    @GetMapping("/test/branches/business")
    String business() {
      throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Business domain conflict");
    }

    @PostMapping("/test/branches/validation")
    String validation(@Valid @RequestBody ValidatedPayload payload) {
      return payload.getName();
    }

    @PostMapping("/test/branches/json-syntax")
    String jsonSyntax(@RequestBody ValidatedPayload payload) {
      return payload.getName();
    }

    @GetMapping("/test/branches/secured")
    String secured() {
      return "secured";
    }

    @GetMapping("/test/branches/admin-only")
    String adminOnly() {
      return "admin";
    }

    @GetMapping("/test/branches/unexpected")
    String unexpected() {
      throw new RuntimeException("secret failure details that must never leak");
    }
  }
}
