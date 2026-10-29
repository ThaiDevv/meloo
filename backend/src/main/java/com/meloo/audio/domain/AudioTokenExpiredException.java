package com.meloo.audio.domain;

import org.springframework.http.HttpStatus;

public class AudioTokenExpiredException extends AudioException {

  public AudioTokenExpiredException() {
    super(
        HttpStatus.UNAUTHORIZED,
        "AUDIO_TOKEN_EXPIRED",
        "Audio token expired",
        "The audio preview token has expired. Request a fresh round state to get a new token.");
  }
}
