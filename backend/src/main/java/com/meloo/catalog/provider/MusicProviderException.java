package com.meloo.catalog.provider;

public class MusicProviderException extends RuntimeException {

  public MusicProviderException(String message) {
    super(message);
  }

  public MusicProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
