package com.meloo.audio.domain;

import org.springframework.http.HttpStatus;

public class AudioRangeNotSatisfiableException extends AudioException {

  private final long totalLength;

  public AudioRangeNotSatisfiableException(long totalLength) {
    super(
        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
        "RANGE_NOT_SATISFIABLE",
        "Range Not Satisfiable",
        "The requested byte range is invalid or extends beyond the audio clip length.");
    this.totalLength = totalLength;
  }

  public long getTotalLength() {
    return totalLength;
  }
}
