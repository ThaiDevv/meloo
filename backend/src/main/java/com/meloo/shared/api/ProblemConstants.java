package com.meloo.shared.api;

/**
 * Standard problem detail properties and common error codes according to RFC 9457 and Meloo
 * contract.
 */
public final class ProblemConstants {

  public static final String PROPERTY_CODE = "code";
  public static final String PROPERTY_CORRELATION_ID = "correlationId";
  public static final String PROPERTY_ERRORS = "errors";

  public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";
  public static final String CODE_FORBIDDEN = "FORBIDDEN";
  public static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String CODE_NOT_FOUND = "NOT_FOUND";
  public static final String CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
  public static final String CODE_UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
  public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

  private ProblemConstants() {}
}
