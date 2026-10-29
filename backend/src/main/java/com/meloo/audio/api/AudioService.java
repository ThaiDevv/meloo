package com.meloo.audio.api;

import com.meloo.generated.model.AudioClipDto;
import java.util.UUID;

/**
 * Public audio service exposed to game, room, daily, and admin modules.
 *
 * <p>All audio preview tokens and playback proxy streaming are managed exclusively by this service
 * per ADR-008.
 */
public interface AudioService {

  /**
   * Prepares and verifies round audio by resolving provider preview URL and verifying reachability
   * (AC 6, BR-27).
   *
   * <p><strong>Note:</strong> This method performs blocking network I/O (Deezer API resolution,
   * retry and HEAD probe). <strong>DO NOT call inside the sequential room command queue</strong>.
   *
   * @param roundId unique round ID
   * @param trackId unique internal track ID
   * @param provider external provider name (e.g. "DEEZER" or "FAKE")
   * @param providerTrackId external track ID on provider (e.g. Deezer ID or fake-1)
   * @return preparation result indicating whether the track is playable or failed
   */
  AudioPreparationResult prepareRoundAudio(
      UUID roundId, UUID trackId, String provider, String providerTrackId);

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
   * @return AudioClipDto with opaque URL and expiration timestamp (capped at <= 2 minutes)
   */
  AudioClipDto createAudioClip(UUID roundId, UUID playerId, int level);
}
