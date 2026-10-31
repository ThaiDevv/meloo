package com.meloo.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sanitizes request URIs before assigning them to RFC 9457 ProblemDetail instance fields.
 *
 * <p>Endpoints handling sensitive path parameters (e.g. opaque audio tokens per
 * contracts/openapi.yaml:566) must never echo raw secret parameters in error instance URIs.
 */
public final class ProblemInstance {

  private record MaskRule(String prefix, URI maskedUri) {}

  /**
   * Sensitive path rules evaluated in declaration order. Adding a new sensitive prefix is a
   * one-line addition to this list.
   */
  private static final List<MaskRule> MASK_RULES =
      List.of(new MaskRule("/api/v1/audio/", URI.create("/api/v1/audio/%7Btoken%7D")));

  private static final URI EMPTY_URI = URI.create("");

  private ProblemInstance() {}

  /** Resolves a sanitized instance URI from the incoming HTTP request. */
  public static URI of(@Nullable HttpServletRequest request) {
    if (request == null) {
      return EMPTY_URI;
    }
    return of(request.getRequestURI());
  }

  /** Resolves a sanitized instance URI from a raw URI string. */
  public static URI of(@Nullable String uri) {
    if (uri == null || uri.isEmpty()) {
      return EMPTY_URI;
    }
    for (MaskRule rule : MASK_RULES) {
      if (uri.startsWith(rule.prefix())) {
        return rule.maskedUri();
      }
    }
    try {
      return URI.create(uri);
    } catch (IllegalArgumentException e) {
      return EMPTY_URI;
    }
  }
}
