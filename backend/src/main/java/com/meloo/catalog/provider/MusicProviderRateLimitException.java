package com.meloo.catalog.provider;

public class MusicProviderRateLimitException extends MusicProviderException {

  public MusicProviderRateLimitException(String message) {
    super(message);
  }

  public MusicProviderRateLimitException(String message, Throwable cause) {
    super(message, cause);
  }
}
