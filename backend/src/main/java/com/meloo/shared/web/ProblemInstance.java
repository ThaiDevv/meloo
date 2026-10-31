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

  /**
   * Resolves a sanitized instance URI from a raw URI string.
   *
   * <p>Normalizes path traversals and percent-encoded characters (e.g. {@code
   * /api/v1/%61udio/<token>}) prior to matching against sensitive path rules to prevent raw
   * parameter leakage.
   */
  public static URI of(@Nullable String uri) {
    if (uri == null || uri.isEmpty()) {
      return EMPTY_URI;
    }
    URI parsed = null;
    String normalizedPath = null;
    try {
      parsed = URI.create(uri).normalize();
      normalizedPath = parsed.getPath();
    } catch (IllegalArgumentException ignored) {
    }

    String candidatePath = normalizedPath != null ? normalizedPath : uri;
    for (MaskRule rule : MASK_RULES) {
      if (candidatePath.startsWith(rule.prefix()) || uri.startsWith(rule.prefix())) {
        return rule.maskedUri();
      }
    }
    return parsed != null ? parsed : EMPTY_URI;
  }
}
