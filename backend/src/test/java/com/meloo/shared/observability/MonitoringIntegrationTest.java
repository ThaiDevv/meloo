package com.meloo.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.shared.api.ModuleSecurityRules;
import io.sentry.ITransportFactory;
import io.sentry.SentryEnvelope;
import io.sentry.transport.ITransport;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
      return List.of(HttpRule.authenticated(null, "/test/monitoring"));
    }

    @GetMapping("/test/monitoring")
    String fail() {
      throw new IllegalStateException("synthetic-secret-answer");
    }
  }
}
