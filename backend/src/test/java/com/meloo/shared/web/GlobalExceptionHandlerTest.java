package com.meloo.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.shared.api.ApiException;
import com.meloo.shared.api.ProblemConstants;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void expectedApiErrorUsesProblemDetailWithoutStackTrace() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    String correlationId = UUID.randomUUID().toString();
    request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, correlationId);

    ProblemDetail detail =
        handler.handleApiException(
            new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Invalid state"), request);

    assertThat(detail.getType()).isEqualTo(URI.create("about:blank"));
    assertThat(detail.getTitle()).isEqualTo("Conflict");
    assertThat(detail.getStatus()).isEqualTo(409);
    assertThat(detail.getDetail()).isEqualTo("Invalid state");
    assertThat(detail.getProperties())
        .containsEntry(ProblemConstants.PROPERTY_CODE, "STATE_CONFLICT")
        .containsEntry(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId)
        .doesNotContainKey("errorCode")
        .doesNotContainKey("stackTrace");
  }

  @Test
  void unexpectedErrorReturnsGenericDetailWithoutLoggingSensitiveMessage(CapturedOutput output) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

    ProblemDetail detail = handler.handleUnexpected(new RuntimeException("secret"), request);

    assertThat(detail.getType()).isEqualTo(URI.create("about:blank"));
    assertThat(detail.getTitle()).isEqualTo("Internal server error");
    assertThat(detail.getStatus()).isEqualTo(500);
    assertThat(detail.getDetail()).isEqualTo("An unexpected error occurred");
    assertThat(detail.getDetail()).doesNotContain("secret");
    assertThat(detail.getProperties())
        .containsEntry(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_INTERNAL_ERROR)
        .doesNotContainKey("errorCode")
        .doesNotContainKey("stackTrace");
    assertThat(output).doesNotContain("secret");
  }
}
