package com.meloo.audio.service;

import java.util.Optional;
import java.util.UUID;

/** Resolves external music provider preview URLs for given track parameters. */
public interface AudioSourceResolver {

  Optional<String> resolvePreviewUrl(UUID trackId, String provider, String providerTrackId);
}
