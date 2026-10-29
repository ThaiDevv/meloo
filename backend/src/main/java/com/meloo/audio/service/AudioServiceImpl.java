package com.meloo.audio.service;

import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.AudioTokenCodec;
import com.meloo.generated.model.AudioClipDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AudioServiceImpl implements AudioService {

  private final AudioStreamService audioStreamService;
  private final AudioTokenCodec audioTokenCodec;
  private final AudioProperties properties;
  private final Clock clock;

  public AudioServiceImpl(
      AudioStreamService audioStreamService,
      AudioTokenCodec audioTokenCodec,
      AudioProperties properties,
      ObjectProvider<Clock> clockProvider) {
    this.audioStreamService = audioStreamService;
    this.audioTokenCodec = audioTokenCodec;
    this.properties = properties;
    this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
  }

  @Override
  public AudioPreparationResult prepareRoundAudio(
      UUID roundId, UUID trackId, String provider, String providerTrackId) {
    return audioStreamService.prepareRoundAudio(roundId, trackId, provider, providerTrackId);
  }

  @Override
  public AudioPreparationResult prepareRoundAudio(UUID roundId, UUID trackId, String previewUrl) {
    return audioStreamService.prepareRoundAudio(roundId, trackId, previewUrl);
  }

  @Override
  public boolean isRoundPlayable(UUID roundId) {
    return audioStreamService.isRoundPlayable(roundId);
  }

  @Override
  public void releaseRoundAudio(UUID roundId) {
    audioStreamService.releaseRoundAudio(roundId);
  }

  @Override
  public AudioClipDto createAudioClip(UUID roundId, UUID playerId, int level) {
    Instant expiresAt = clock.instant().plus(properties.tokenTtl());
    return createAudioClip(roundId, playerId, level, expiresAt);
  }

  @Override
  public AudioClipDto createAudioClip(UUID roundId, UUID playerId, int level, Instant expiresAt) {
    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    String rawToken = audioTokenCodec.encode(token);

    AudioClipDto dto = new AudioClipDto();
    dto.setUrl("/api/v1/audio/" + rawToken);
    dto.setExpiresAt(expiresAt.atOffset(ZoneOffset.UTC));
    return dto;
  }
}
