package com.meloo.game.web;

import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.Round;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.solo.SoloMatch;
import com.meloo.game.solo.SoloRoundData;
import com.meloo.generated.model.AnswerDto;
import com.meloo.generated.model.AudioClipDto;
import com.meloo.generated.model.MatchResultDto;
import com.meloo.generated.model.MatchStatusDto;
import com.meloo.generated.model.MusicProviderDto;
import com.meloo.generated.model.RoundEndDto;
import com.meloo.generated.model.RoundOutcomeDto;
import com.meloo.generated.model.RoundResultDto;
import com.meloo.generated.model.RoundStateDto;
import com.meloo.generated.model.SoloMatchDto;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Chuyển đổi dữ liệu giữa domain model và DTO hợp đồng OpenAPI v1. */
public final class SoloDtoMapper {

  private SoloDtoMapper() {}

  public static SoloMatchDto toSoloMatchDto(SoloMatch match, RoundStateDto currentRound) {
    SoloMatchDto dto = new SoloMatchDto();
    dto.setMatchId(match.matchId());
    dto.setMode(match.mode());
    dto.setRanked(match.isRanked());
    dto.setRoundCount(SoloMatchDto.RoundCountEnum.fromValue(match.roundCount()));
    dto.setFilter(match.filterDto());
    dto.setTotalScore(match.calculateTotalScore());
    dto.setComboStreak(match.comboTracker().currentStreak());
    dto.setStartedAt(match.startedAt().atOffset(ZoneOffset.UTC));
    dto.setExpiresAt(match.getExpiresAt().atOffset(ZoneOffset.UTC));
    dto.setCurrentRound(currentRound);
    return dto;
  }

  public static RoundStateDto toRoundStateDto(
      SoloRoundData roundData, SoloMatch match, AudioClipDto clip, Instant now) {
    int level = roundData.round().currentLevel();
    RoundStateDto dto = new RoundStateDto();
    dto.setMatchId(match.matchId());
    dto.setRoundNo(roundData.roundNo());
    dto.setRoundCount(RoundStateDto.RoundCountEnum.fromValue(match.roundCount()));
    dto.setLevel(level);
    dto.setListenSeconds(toListenSecondsEnum(level));
    dto.setCanListenMore(level < 5);
    dto.setCanSkip(level == 5);
    dto.setAudio(clip);
    dto.setServerTime(now.atOffset(ZoneOffset.UTC));
    return dto;
  }

  public static RoundResultDto toRoundResultDto(SoloRoundData roundData, SoloMatch match) {
    Round r = roundData.round();
    RoundOutcome outcome = r.outcome() != null ? r.outcome() : RoundOutcome.GAVE_UP;
    RoundResultDto dto = new RoundResultDto();
    dto.setRoundNo(roundData.roundNo());
    dto.setOutcome(RoundOutcomeDto.fromValue(outcome.name()));
    dto.setCorrectLevel(outcome == RoundOutcome.CORRECT ? r.currentLevel() : null);
    dto.setBaseScore(RoundResultDto.BaseScoreEnum.fromValue(r.scoreDetail().baseScore()));
    dto.setComboPercent(RoundResultDto.ComboPercentEnum.fromValue(r.scoreDetail().comboPercent()));
    dto.setSpeedBonus(r.scoreDetail().speedBonus());
    dto.setRoundScore(r.scoreDetail().roundScore());
    dto.setWrongCount(r.wrongCount());
    dto.setComboStreak(roundData.comboStreakAfterRound());
    dto.setTotalScore(roundData.scoreBeforeRound() + r.scoreDetail().roundScore());
    dto.setAnswer(toAnswerDto(roundData.track()));
    return dto;
  }

  public static RoundEndDto toRoundEndDto(
      SoloRoundData current,
      SoloMatch match,
      boolean matchFinished,
      @Nullable RoundStateDto nextRound) {
    RoundEndDto dto = new RoundEndDto();
    dto.setResult(toRoundResultDto(current, match));
    dto.setMatchFinished(matchFinished);
    dto.setNextRound(nextRound);
    return dto;
  }

  public static MatchResultDto toMatchResultDto(SoloMatch match) {
    MatchResultDto dto = new MatchResultDto();
    dto.setMatchId(match.matchId());
    dto.setMode(match.mode());
    dto.setRanked(match.isRanked());
    dto.setStatus(MatchStatusDto.fromValue(match.status().name()));
    dto.setRoundCount(MatchResultDto.RoundCountEnum.fromValue(match.roundCount()));
    dto.setTotalScore(match.calculateTotalScore());
    dto.setCorrectCount(match.calculateCorrectCount());

    List<RoundResultDto> rounds = new ArrayList<>();
    for (SoloRoundData data : match.getCompletedRounds()) {
      rounds.add(toRoundResultDto(data, match));
    }
    dto.setRounds(rounds);

    dto.setStartedAt(match.startedAt().atOffset(ZoneOffset.UTC));
    dto.setEndedAt(
        match.endedAt() != null
            ? match.endedAt().atOffset(ZoneOffset.UTC)
            : match.lastActivityAt().atOffset(ZoneOffset.UTC));
    return dto;
  }

  public static AnswerDto toAnswerDto(TrackInfo track) {
    AnswerDto dto = new AnswerDto();
    dto.setTrackId(track.id());
    dto.setTitle(track.title());
    dto.setArtistName(track.artistName());

    MusicProviderDto providerDto;
    try {
      providerDto = MusicProviderDto.fromValue(track.provider());
    } catch (Exception e) {
      providerDto = MusicProviderDto.DEEZER;
    }
    dto.setProvider(providerDto);

    // BR-29: Link nghe đầy đủ trên Deezer (hoặc provider gốc)
    String listenUrlStr = "https://www.deezer.com/track/" + track.providerTrackId();
    dto.setListenUrl(URI.create(listenUrlStr));
    dto.setCoverUrl(track.coverUrl());
    return dto;
  }

  private static RoundStateDto.ListenSecondsEnum toListenSecondsEnum(int level) {
    return switch (level) {
      case 1 -> RoundStateDto.ListenSecondsEnum.NUMBER_2;
      case 2 -> RoundStateDto.ListenSecondsEnum.NUMBER_5;
      case 3 -> RoundStateDto.ListenSecondsEnum.NUMBER_10;
      case 4 -> RoundStateDto.ListenSecondsEnum.NUMBER_20;
      case 5 -> RoundStateDto.ListenSecondsEnum.NUMBER_30;
      default -> throw new IllegalArgumentException("Invalid listening level: " + level);
    };
  }
}
