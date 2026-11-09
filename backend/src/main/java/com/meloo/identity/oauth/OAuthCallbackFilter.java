package com.meloo.identity.oauth;

import com.meloo.shared.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** The link redirect must leave the authorization code unused until the authenticated POST. */
public final class OAuthCallbackFilter extends OncePerRequestFilter {
  private final OAuthLoginSupport support;
  private final OAuthStateRepository states;
  private final ObjectMapper mapper;

  public OAuthCallbackFilter(
      OAuthLoginSupport support, OAuthStateRepository states, ObjectMapper mapper) {
    this.support = support;
    this.states = states;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    if (request.getMethod().equals("GET")
        && request.getServletPath().equals(OAuthLoginSupport.BASE + "/google/callback")) {
      var authorization = states.loadAuthorizationRequest(request);
      String code = request.getParameter("code");
      if (authorization == null
          || request.getParameter("error") != null
          || code == null
          || code.isBlank()
          || code.length() > 2048) {
        support.failure(response, "OAUTH_STATE_INVALID");
        return;
      }
      if ("link".equals(authorization.getAttribute("mode"))) {
        response.sendRedirect(
            support.linkRedirect(code, request.getParameter("state"), authorization));
        return;
      }
    }
    try {
      if (request.getMethod().equals("GET")
          && request
              .getRequestURI()
              .equals(request.getContextPath() + OAuthLoginSupport.BASE + "/google")) {
        support.resolve(
            request); // Validate before the redirect filter translates resolver failures into 500.
      }
      chain.doFilter(request, response);
    } catch (ApiException e) {
      response.setStatus(e.status().value());
      response.setContentType("application/problem+json");
      var problem =
          org.springframework.http.ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
      problem.setType(java.net.URI.create("about:blank"));
      problem.setProperty("code", e.code());
      problem.setInstance(java.net.URI.create(request.getRequestURI()));
      mapper.writeValue(response.getOutputStream(), problem);
    }
  }
}
