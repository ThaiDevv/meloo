package com.meloo.shared.web;

import com.meloo.shared.api.ProblemConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * AuthenticationEntryPoint that produces RFC 9457 ProblemDetail with code: UNAUTHORIZED while
 * preserving the WWW-Authenticate response header.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final BearerTokenAuthenticationEntryPoint bearerDelegate =
      new BearerTokenAuthenticationEntryPoint();
  private final ObjectMapper objectMapper;

  public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    bearerDelegate.commence(request, response, authException);
    if (!response.containsHeader(HttpHeaders.WWW_AUTHENTICATE)) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "The access token is missing or expired.");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Unauthorized");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_UNAUTHORIZED);
    problem.setProperty(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId(request));

    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
