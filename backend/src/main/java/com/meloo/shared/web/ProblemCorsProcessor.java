package com.meloo.shared.web;

import com.meloo.shared.api.ProblemConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * CORS processor that emits an RFC 9457 ProblemDetail 403 response with code: FORBIDDEN when a CORS
 * request or preflight request is rejected.
 */
public class ProblemCorsProcessor extends DefaultCorsProcessor {

  private final ObjectMapper objectMapper;
  private final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

  public ProblemCorsProcessor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean processRequest(
      @Nullable CorsConfiguration config, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    currentRequest.set(request);
    try {
      return super.processRequest(config, request, response);
    } finally {
      currentRequest.remove();
    }
  }

  @Override
  protected void rejectRequest(ServerHttpResponse response) throws IOException {
    response.setStatusCode(HttpStatus.FORBIDDEN);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    HttpServletRequest request = currentRequest.get();
    String instance = request != null ? request.getRequestURI() : "";
    String correlationId = correlationId(request);

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "CORS request rejected");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Forbidden");
    problem.setInstance(URI.create(instance));
    problem.setProperty(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_FORBIDDEN);
    problem.setProperty(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId);

    byte[] bytes = objectMapper.writeValueAsBytes(problem);
    response.getBody().write(bytes);
    response.flush();
  }

  private static String correlationId(@Nullable HttpServletRequest request) {
    if (request == null) {
      return "unavailable";
    }
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
