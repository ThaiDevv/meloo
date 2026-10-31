package com.meloo.shared.web;

import com.meloo.shared.api.ProblemConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * AccessDeniedHandler producing RFC 9457 ProblemDetail for 403 Forbidden with code: FORBIDDEN.
 *
 * <p>Note: We intentionally do not emit {@code WWW-Authenticate: Bearer error="insufficient_scope"}
 * on 403 because Meloo authorizes based on application roles (e.g., {@code ROLE_ADMIN}) rather than
 * OAuth scopes. RFC 6750 specifies insufficient_scope for OAuth scopes; emitting it here would be
 * misleading. WWW-Authenticate is preserved on 401 via ProblemAuthenticationEntryPoint.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Forbidden");
    problem.setInstance(ProblemInstance.of(request));
    problem.setProperty(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_FORBIDDEN);
    problem.setProperty(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId(request));

    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
