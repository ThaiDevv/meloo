package com.meloo.audio.service;

import java.util.Optional;
import java.util.UUID;

public class FakeAudioSourceResolver implements AudioSourceResolver {

  private final String mediaLocation;

  public FakeAudioSourceResolver(String mediaLocation) {
    this.mediaLocation = mediaLocation != null ? mediaLocation : "classpath:fake-media/";
  }

  @Override
  public Optional<String> resolvePreviewUrl(UUID trackId, String provider, String providerTrackId) {
    if (providerTrackId == null
        || providerTrackId.isBlank()
        || providerTrackId.contains("missing")
        || providerTrackId.contains("broken")
        || providerTrackId.contains("fail")) {
      return Optional.empty();
    }

    String sampleFile;
    if ("fake-2".equalsIgnoreCase(providerTrackId) || providerTrackId.endsWith("2")) {
      sampleFile = "sample2.mp3";
    } else if ("fake-3".equalsIgnoreCase(providerTrackId) || providerTrackId.endsWith("3")) {
      sampleFile = "sample3.mp3";
    } else {
      sampleFile = "sample1.mp3";
    }

    String base = mediaLocation.endsWith("/") ? mediaLocation : mediaLocation + "/";
    return Optional.of(base + sampleFile);
  }
}
