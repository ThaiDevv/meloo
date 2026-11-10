package com.meloo.daily.service;

import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.DailyAttempt;
import com.meloo.game.domain.GameRules;
import com.meloo.generated.model.AnswerDto;
import com.meloo.generated.model.DailyCompletionStatusDto;
import com.meloo.generated.model.DailyLevelStatusDto;
import com.meloo.generated.model.DailyListenSecondsDto;
import com.meloo.generated.model.DailyResultDto;
import com.meloo.generated.model.DailyRoundStateDto;
import com.meloo.generated.model.MusicProviderDto;
import com.meloo.generated.model.RoundOutcomeDto;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DailyMapper {
  public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  public static final int ATTEMPT_SECONDS = 300;
  private final ObjectMapper json;
  private final AudioService audio;

  public DailyMapper(ObjectMapper json, AudioService audio) {
    this.json = json;
    this.audio = audio;
  }

  public String snapshot(TrackInfo track) {
    return json.writeValueAsString(track);
  }

  public TrackInfo track(DailyAttempt attempt) {
    return json.readValue(Objects.requireNonNull(attempt.getTrackSnapshot()), TrackInfo.class);
  }

  public static java.time.OffsetDateTime resetAfter(LocalDate date) {
    return date.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime();
  }

  public static DailyListenSecondsDto seconds(int level) {
    return DailyListenSecondsDto.fromValue(
        GameRules.DEFAULT_LEVELS.get(level - 1).durationSeconds());
  }

  public static int score(DailyAttempt a) {
    return a.getStatus() == AttemptStatus.SOLVED && a.getCorrectLevel() != null
        ? GameRules.DEFAULT_LEVELS.get(a.getCorrectLevel() - 1).baseScore()
        : 0;
  }

  public DailyRoundStateDto round(DailyAttempt a, Instant now) {
    int level = a.getCurrentLevel();
    return new DailyRoundStateDto()
        .challengeDate(a.getChallengeDate())
        .level(level)
        .listenSeconds(seconds(level))
        .canListenMore(level < 5)
        .canSkip(level == 5)
        .wrongCount(a.getWrongCount())
        .audio(audio.createAudioClip(a.getId(), a.getUserId(), level))
        .startedAt(a.getStartedAt().atOffset(ZoneOffset.UTC))
        .expiresAt(a.getStartedAt().plusSeconds(ATTEMPT_SECONDS).atOffset(ZoneOffset.UTC))
        .serverTime(now.atOffset(ZoneOffset.UTC));
  }

  public DailyResultDto result(DailyAttempt a, int current, int longest, @Nullable Integer rank) {
    var levels = new ArrayList<DailyLevelStatusDto>();
    for (int i = 1; i <= 5; i++) {
      levels.add(
          a.getCorrectLevel() != null && a.getCorrectLevel() == i
              ? DailyLevelStatusDto.CORRECT
              : i <= a.getCurrentLevel()
                  ? DailyLevelStatusDto.WRONG
                  : DailyLevelStatusDto.UNOPENED);
    }
    TrackInfo track = track(a);
    var answer =
        new AnswerDto()
            .trackId(track.id())
            .title(track.title())
            .artistName(track.artistName())
            .provider(MusicProviderDto.fromValue(track.provider()))
            .coverUrl(track.coverUrl())
            .listenUrl(URI.create("https://www.deezer.com/track/" + track.providerTrackId()));
    return new DailyResultDto()
        .challengeDate(a.getChallengeDate())
        .status(DailyCompletionStatusDto.fromValue(a.getStatus().name()))
        .outcome(RoundOutcomeDto.fromValue(Objects.requireNonNull(a.getOutcome())))
        .correctLevel(a.getCorrectLevel() == null ? null : a.getCorrectLevel().intValue())
        .correctListenSeconds(a.getCorrectLevel() == null ? null : seconds(a.getCorrectLevel()))
        .levels(levels)
        .score(DailyResultDto.ScoreEnum.fromValue(score(a)))
        .wrongCount(a.getWrongCount())
        .elapsedMs(a.getElapsedMs())
        .rank(rank)
        .currentStreak(current)
        .longestStreak(longest)
        .nextResetAt(resetAfter(a.getChallengeDate()))
        .answer(answer);
  }
}
