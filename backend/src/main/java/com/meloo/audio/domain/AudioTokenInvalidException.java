package com.meloo.audio.domain;

import org.springframework.http.HttpStatus;

public class AudioTokenInvalidException extends AudioException {

  public static AudioTokenInvalidException malformed() {
    return new AudioTokenInvalidException(
        HttpStatus.BAD_REQUEST,
        "Malformed audio token",
        "The audio preview token format is malformed or exceeds maximum length.");
  }

  public static AudioTokenInvalidException invalidSignature() {
    return new AudioTokenInvalidException(
        HttpStatus.UNAUTHORIZED,
        "Invalid audio token",
        "The audio preview token signature is invalid or HMAC verification failed.");
  }

  public AudioTokenInvalidException(HttpStatus status, String title, String detail) {
    super(status, "AUDIO_TOKEN_INVALID", title, detail);
  }
}
