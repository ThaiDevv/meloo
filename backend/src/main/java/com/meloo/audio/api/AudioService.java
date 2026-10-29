package com.meloo.audio.api;

import com.meloo.generated.model.AudioClipDto;
import java.time.Instant;
import java.util.UUID;

/** Public audio service exposed to game, room, daily, and admin modules. */
public interface AudioService {

  /**
   * Prepares and verifies round audio by resolving provider preview URL and verifying reachability
   * (AC 6, BR-27).
   *
   * @param roundId unique round ID
   * @param trackId unique internal track ID
   * @param provider external provider name (e.g. "DEEZER")
   * @param providerTrackId external track ID on provider
   * @return preparation result indicating whether the track is playable or failed
   */
  AudioPreparationResult prepareRoundAudio(
      UUID roundId, UUID trackId, String provider, String providerTrackId);

  /**
   * Prepares and verifies round audio with direct preview URL (e.g. for testing or local audio).
   *
   * @param roundId unique round ID
   * @param trackId unique internal track ID
   * @param previewUrl direct preview URL (e.g. classpath:fake-media/... or HTTPS URL)
   * @return preparation result
   */
  AudioPreparationResult prepareRoundAudio(UUID roundId, UUID trackId, String previewUrl);

  /** Returns true if audio for the round has been prepared and is ready to stream. */
  boolean isRoundPlayable(UUID roundId);

  /** Releases in-memory round audio binding when the round finishes. */
  void releaseRoundAudio(UUID roundId);

  /**
   * Creates an audio clip DTO with an opaque HMAC token for the given round, player, and listening
   * level (AC 7).
   *
   * @param roundId round UUID
   * @param playerId player UUID
   * @param level listening level (1 to 5)
   * @return AudioClipDto with opaque URL and expiration timestamp
   */
  AudioClipDto createAudioClip(UUID roundId, UUID playerId, int level);

  /**
   * Creates an audio clip DTO with specified expiration timestamp.
   *
   * @param roundId round UUID
   * @param playerId player UUID
   * @param level listening level (1 to 5)
   * @param expiresAt explicit expiration instant
   * @return AudioClipDto
   */
  AudioClipDto createAudioClip(UUID roundId, UUID playerId, int level, Instant expiresAt);
}
