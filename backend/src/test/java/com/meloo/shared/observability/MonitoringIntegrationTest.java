package com.meloo.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.shared.api.ApiException;
import com.meloo.shared.api.ModuleSecurityRules;
import io.sentry.ITransportFactory;
import io.sentry.SentryEnvelope;
import io.sentry.transport.ITransport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@AutoConfigureMockMvc
@Import(MonitoringIntegrationTest.Fixture.class)
@TestPropertySource(
    properties = {
      "sentry.dsn=http://public@localhost:1/1",
      "sentry.release=ghcr.io/thaidevv/meloo-backend:sha-test"
    })
@DirtiesContext
class MonitoringIntegrationTest extends IntegrationTest {
  static final CopyOnWriteArrayList<String> ENVELOPES = new CopyOnWriteArrayList<>();
  @Autowired MockMvc mvc;

  @Test
  void mvcHandledFailureReachesRealSdkWithoutSecretsOrNetwork() throws Exception {
    ENVELOPES.clear();
    var response =
        mvc.perform(get("/test/monitoring?token=synthetic-secret-answer").with(user("operator")))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse();
    assertThat(ENVELOPES)
        .anySatisfy(
            json -> {
              assertThat(json)
                  .contains(
                      "ghcr.io_thaidevv_meloo-backend:sha-test",
                      "IllegalStateException",
                      response.getHeader("X-Correlation-Id"));
              assertThat(json).doesNotContain("synthetic-secret-answer", "operator");
            });
  }

  @Test
  void clientErrorsFromMvcAndSecurityAreNotReportedButAServerErrorStillIs() throws Exception {
    ENVELOPES.clear();
    expect(404, authed(get("/test/monitoring/missing")));
    expect(400, json(post("/test/monitoring/body"), "{\"name\":\"\"}"));
    expect(400, json(post("/test/monitoring/body"), "{"));
    expect(
        415, authed(post("/test/monitoring/body")).contentType(MediaType.TEXT_PLAIN).content("x"));
    expect(400, authed(get("/test/monitoring/number?value=abc")));
    expect(400, authed(get("/test/monitoring/number")));
    expect(405, authed(delete("/test/monitoring")));
    expect(404, authed(get("/test/monitoring/api")));
    expect(409, authed(get("/test/monitoring/status")));
    expect(403, authed(get("/test/monitoring/denied")));
    expect(403, authed(get("/test/monitoring/admin")));
    expect(401, authed(get("/test/monitoring/unauthenticated")));
    expect(401, get("/test/monitoring"));
    // Sending is synchronous through the capture transport, so a final 500 proves ordering.
    expect(500, authed(get("/test/monitoring")));
    assertThat(String.join("\n", ENVELOPES).split("\"type\":\"event\"", -1)).hasSize(2);
    assertThat(ENVELOPES)
        .filteredOn(json -> json.contains("\"type\":\"event\""))
        .singleElement()
        .satisfies(
            json -> assertThat(json).contains("IllegalStateException", "\"level\":\"fatal\""));
  }

  private void expect(int status, RequestBuilder request) throws Exception {
    mvc.perform(request).andExpect(status().is(status));
  }

  private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
    return request.with(user("operator"));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return authed(request).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    FailureController failureController() {
      return new FailureController();
    }

    @Bean
    ITransportFactory captureTransport() {
      return (options, details) -> {
        ITransport transport = mock(ITransport.class);
        try {
          doAnswer(
                  call -> {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    options.getSerializer().serialize(call.<SentryEnvelope>getArgument(0), bytes);
                    ENVELOPES.add(bytes.toString(StandardCharsets.UTF_8));
                    return null;
                  })
              .when(transport)
              .send(any(SentryEnvelope.class), any());
        } catch (java.io.IOException error) {
          throw new IllegalStateException(error);
        }
        return transport;
      };
    }
  }

  @RestController
  static class FailureController implements ModuleSecurityRules {
    @Override
    public List<HttpRule> httpRules() {
      return List.of(HttpRule.authenticated(null, "/test/monitoring", "/test/monitoring/**"));
    }

    @GetMapping("/test/monitoring")
    String fail() {
      throw new IllegalStateException("synthetic-secret-answer");
    }

    @PostMapping("/test/monitoring/body")
    String body(@Valid @RequestBody Body body) {
      return body.name();
    }

    @GetMapping("/test/monitoring/number")
    String number(@RequestParam int value) {
      return Integer.toString(value);
    }

    @GetMapping("/test/monitoring/api")
    String api() {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "synthetic");
    }

    @GetMapping("/test/monitoring/status")
    String responseStatus() {
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @GetMapping("/test/monitoring/denied")
    String denied() {
      throw new AccessDeniedException("synthetic");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/test/monitoring/admin")
    String admin() {
      return "admin";
    }

    @GetMapping("/test/monitoring/unauthenticated")
    String unauthenticated() {
      throw new BadCredentialsException("synthetic");
    }
  }

  record Body(@NotBlank String name) {}
}
