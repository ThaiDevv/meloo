package com.meloo.game.solo;

import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.PlaylistInfo;
import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.GuessEvaluation;
import com.meloo.game.domain.Round;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.domain.RoundStatus;
import com.meloo.game.domain.TargetTrack;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchStarted;
import com.meloo.game.events.MatchType;
import com.meloo.game.events.PlayerSummary;
import com.meloo.game.events.RoundPlayerResult;
import com.meloo.game.events.RoundSummary;
import com.meloo.generated.model.AudioClipDto;
import com.meloo.generated.model.CreateSoloMatchRequestDto;
import com.meloo.generated.model.GuessRequestDto;
import com.meloo.generated.model.GuessResponseDto;
import com.meloo.generated.model.MatchResultDto;
import com.meloo.generated.model.RoundEndDto;
import com.meloo.generated.model.RoundResultDto;
import com.meloo.generated.model.RoundStateDto;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.generated.model.SoloMatchDto;
import com.meloo.generated.model.SoloModeDto;
import com.meloo.shared.api.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Dịch vụ điều phối nghiệp vụ chơi đơn (BR-01..BR-09, BR-23, BR-27, BR-29). */
@Service
public class SoloGameService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SoloGameService.class);
  private static final String STANDARD_PLAYLIST_CODE = "standard";
  private static final int DEFAULT_ROUND_COUNT = 10;
  private static final int MAX_REPLACEMENT_ATTEMPTS = 5;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final CatalogService catalogService;
  private final AudioService audioService;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final SoloMatchManager matchManager;

  private final ScheduledExecutorService cleanupScheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "solo-match-cleaner");
            t.setDaemon(true);
            return t;
          });

  public SoloGameService(
      CatalogService catalogService,
      AudioService audioService,
      ApplicationEventPublisher eventPublisher,
      PlatformTransactionManager transactionManager,
      ObjectProvider<Clock> clockProvider,
      SoloMatchManager matchManager) {
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
    this.audioService = Objects.requireNonNull(audioService, "audioService must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    this.transactionTemplate =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    this.matchManager = Objects.requireNonNull(matchManager, "matchManager must not be null");
  }

  @PostConstruct
  public void initScheduler() {
    cleanupScheduler.scheduleWithFixedDelay(this::expireInactiveMatches, 1, 1, TimeUnit.MINUTES);
  }

  @PreDestroy
  public void shutdownScheduler() {
    cleanupScheduler.shutdown();
  }

  public SoloMatchDto createSoloMatch(
      UUID userId, String nickname, boolean isGuest, CreateSoloMatchRequestDto request) {
    Instant now = clock.instant();

    SoloModeDto mode = request.getMode() != null ? request.getMode() : SoloModeDto.PRACTICE;
    boolean ranked = (mode == SoloModeDto.RANKED);
    if (ranked && request.getFilter() != null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Filter is only allowed for PRACTICE mode");
    }
    if (ranked && request.getPlaylistId() != null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Playlist is only allowed for PRACTICE mode");
    }
    if (!ranked && request.getFilter() != null && request.getPlaylistId() != null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "filter and playlistId are mutually exclusive");
    }

    UUID playlistId = request.getPlaylistId();
    if (!ranked && playlistId != null) {
      catalogService
          .findPlaylistById(playlistId)
          .filter(PlaylistInfo::enabled)
          .orElseThrow(
              () ->
                  new ApiException(
                      HttpStatus.NOT_FOUND,
                      "PLAYLIST_NOT_FOUND",
                      "The playlist was not found or has been disabled."));
    }

    // 1. Kiểm tra nhanh trong khóa xem người dùng đã có trận đang chơi chưa (BR-23)
    synchronized (matchManager.getUserLock(userId)) {
      Optional<SoloMatch> activeOpt = matchManager.getActiveMatchByUserId(userId);
      if (activeOpt.isPresent()) {
        SoloMatch existing = activeOpt.get();
        if (existing.isExpired(now)) {
          expireMatch(existing, now);
        } else {
          throw new MatchInProgressException(existing.matchId());
        }
      }
    }

    int roundCount =
        request.getRoundCount() != null ? request.getRoundCount().getValue() : DEFAULT_ROUND_COUNT;

    TrackFilter trackFilter = toTrackFilter(request.getFilter());

    // 2. Chọn bài từ catalog ngoài khóa (tránh nghẽn striped lock của người dùng khác)
    List<TrackInfo> candidateTracks;
    if (ranked) {
      candidateTracks =
          catalogService.selectRandomTracksByPlaylistCode(
              STANDARD_PLAYLIST_CODE, roundCount, Set.of());
    } else if (playlistId != null) {
      candidateTracks =
          catalogService.selectRandomTracksByPlaylist(playlistId, roundCount, Set.of());
    } else {
      // PRACTICE: nếu bộ lọc rỗng thì chọn từ toàn bộ kho nhạc
      candidateTracks =
          catalogService.selectRandomTracks(
              trackFilter != null && !trackFilter.isEmpty() ? trackFilter : null,
              roundCount,
              Set.of());
    }

    if (candidateTracks.size() < roundCount) {
      int missing = roundCount - candidateTracks.size();
      throw new NotEnoughTracksException(missing);
    }

    UUID matchId = UUID.randomUUID();
    SoloMatch match =
        new SoloMatch(
            matchId,
            userId,
            nickname,
            isGuest,
            mode,
            ranked,
            roundCount,
            playlistId,
            trackFilter,
            request.getFilter(),
            candidateTracks,
            now);

    // 3. Chuẩn bị vòng đầu tiên ngoài khóa
    SoloRoundData firstRound = prepareNextRound(match, 0, now);

    // 4. Kiểm tra lại và đăng ký trận trong khóa
    synchronized (matchManager.getUserLock(userId)) {
      Optional<SoloMatch> activeOpt = matchManager.getActiveMatchByUserId(userId);
      if (activeOpt.isPresent()) {
        SoloMatch existing = activeOpt.get();
        if (!existing.isExpired(now)) {
          audioService.releaseRoundAudio(firstRound.roundId());
          throw new MatchInProgressException(existing.matchId());
        }
        expireMatch(existing, now);
      }

      match.addRoundData(firstRound);
      matchManager.registerMatch(match);
    }

    // 5. Phát MatchStarted trong transaction
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new MatchStarted(
                    UUID.randomUUID(),
                    match.matchId(),
                    MatchType.SOLO,
                    match.isRanked(),
                    match.roundCount(),
                    match.startedAt(),
                    List.of(match.userId()))));

    return toSoloMatchDto(match, userId);
  }

  public SoloMatchDto getActiveSoloMatch(UUID userId) {
    Instant now = clock.instant();

    Optional<SoloMatch> activeOpt = matchManager.getActiveMatchByUserId(userId);
    if (activeOpt.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_MATCH", "No active match found");
    }

    SoloMatch match = activeOpt.get();
    if (match.isExpired(now)) {
      expireMatch(match, now);
      throw new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_MATCH", "No active match found");
    }

    ensureNextRoundReady(match, now);

    match.touch(now);
    return toSoloMatchDto(match, userId);
  }

  public RoundStateDto getCurrentRound(UUID userId, UUID matchId) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    ensureNextRoundReady(match, now);

    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
      }

      SoloRoundData current = match.getCurrentRoundData();
      if (current.round().status() != RoundStatus.IN_PROGRESS) {
        throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
      }

      match.touch(now);
      return toRoundStateDto(current, match, userId);
    }
  }

  public RoundStateDto listenMore(
      UUID userId, UUID matchId, int roundNo, @Nullable Integer expectedLevel) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    if (roundNo > match.currentRoundIndex() + 1) {
      ensureNextRoundReady(match, now);
    }

    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
      }

      if (roundNo < 1 || roundNo > match.roundCount()) {
        throw new ApiException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Round not found");
      }

      SoloRoundData current = match.getCurrentRoundData();
      if (current.roundNo() != roundNo || current.round().status() != RoundStatus.IN_PROGRESS) {
        throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
      }

      // Thứ tự kiểm tra: mức tối đa trước, rồi mới tới mismatch
      if (current.round().currentLevel() >= 5) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "LISTEN_LEVEL_MAX",
            "Already at maximum listening level (30 seconds)");
      }

      int nextLevel = current.round().currentLevel() + 1;
      if (expectedLevel != null && expectedLevel != nextLevel) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "LISTEN_LEVEL_MISMATCH",
            "Expected level (" + expectedLevel + ") does not match next level (" + nextLevel + ")");
      }

      current.round().listenMore();
      match.touch(now);
      return toRoundStateDto(current, match, userId);
    }
  }

  public RoundEndDto skipRound(UUID userId, UUID matchId, int roundNo) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    if (roundNo > match.currentRoundIndex() + 1) {
      ensureNextRoundReady(match, now);
    }

    SoloRoundData current;
    boolean matchFinished;
    boolean alreadyEnded = false;
    int nextIndex;

    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
      }

      if (roundNo < 1 || roundNo > match.roundCount()) {
        throw new ApiException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Round not found");
      }

      current = match.getCurrentRoundData();
      if (current.roundNo() == roundNo && current.round().status() != RoundStatus.IN_PROGRESS) {
        if (roundNo >= match.roundCount()) {
          throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
        }
        alreadyEnded = true;
      } else {
        if (current.roundNo() != roundNo || current.round().status() != RoundStatus.IN_PROGRESS) {
          throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
        }

        // BR-04: Chỉ được bỏ qua khi đã ở mức 30s (level 5)
        if (current.round().currentLevel() < 5) {
          throw new ApiException(
              HttpStatus.CONFLICT, "SKIP_NOT_ALLOWED", "Cannot skip round before 30-second level");
        }

        current.round().skip(now, match.comboTracker());
        current.setComboStreakAfterRound(match.comboTracker().currentStreak());
        audioService.releaseRoundAudio(current.roundId());
        match.touch(now);
      }

      matchFinished = (roundNo >= match.roundCount());
      if (matchFinished && !alreadyEnded) {
        if (match.finish(now)) {
          try {
            publishMatchFinishedEvent(match);
          } catch (Exception e) {
            LOGGER.error(
                "Failed to publish MatchFinished event immediately for match {}",
                match.matchId(),
                e);
          }
        }
      }
      nextIndex = match.currentRoundIndex() + 1;
    }

    if (matchFinished) {
      try {
        return SoloDtoMapper.toRoundEndDto(current, match, true, null);
      } finally {
        matchManager.clearActiveMatch(userId, matchId);
      }
    } else {
      SoloRoundData nextRoundData;
      synchronized (match) {
        if (match.roundDataList().size() > nextIndex) {
          nextRoundData = match.roundDataList().get(nextIndex);
        } else {
          nextRoundData = null;
        }
      }

      if (nextRoundData == null) {
        SoloRoundData prepared = prepareNextRound(match, nextIndex, now);
        nextRoundData = commitNextRound(match, nextIndex, prepared);
      }

      RoundStateDto nextRoundDto = toRoundStateDto(nextRoundData, match, userId);
      return SoloDtoMapper.toRoundEndDto(current, match, false, nextRoundDto);
    }
  }

  public GuessResponseDto submitGuess(
      UUID userId, UUID matchId, int roundNo, GuessRequestDto request) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    if (roundNo > match.currentRoundIndex() + 1) {
      ensureNextRoundReady(match, now);
    }

    Optional<TrackInfo> guessTrackOpt = catalogService.getTrackById(request.getTrackId());
    if (guessTrackOpt.isEmpty()) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "TRACK_NOT_FOUND",
          "The selected track does not exist in the catalog.");
    }

    TrackInfo guessTrack = guessTrackOpt.get();
    SoloRoundData current;
    GuessEvaluation eval;
    boolean matchFinished = false;
    boolean alreadyEnded = false;
    int nextIndex;

    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
      }

      if (roundNo < 1 || roundNo > match.roundCount()) {
        throw new ApiException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Round not found");
      }

      current = match.getCurrentRoundData();
      if (current.roundNo() == roundNo && current.round().status() != RoundStatus.IN_PROGRESS) {
        if (roundNo >= match.roundCount()) {
          throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
        }
        alreadyEnded = true;
        boolean wasCorrect = current.round().outcome() == RoundOutcome.CORRECT;
        eval =
            new GuessEvaluation(
                wasCorrect,
                current.lastGuessRightArtist(),
                true,
                current.round().status(),
                current.round().outcome(),
                current.round().currentLevel(),
                current.round().wrongCount(),
                current.round().scoreDetail());
      } else {
        if (current.roundNo() != roundNo || current.round().status() != RoundStatus.IN_PROGRESS) {
          throw new ApiException(HttpStatus.CONFLICT, "ROUND_NOT_ACTIVE", "Round is not active");
        }

        eval =
            current
                .round()
                .submitGuess(
                    guessTrack.title(), guessTrack.artistName(), now, match.comboTracker());
        match.touch(now);

        boolean rightArtist = eval.rightArtist();

        if (eval.roundEnded()) {
          current.setComboStreakAfterRound(match.comboTracker().currentStreak());
          current.setLastGuessRightArtist(rightArtist);
          audioService.releaseRoundAudio(current.roundId());
          matchFinished = (roundNo >= match.roundCount());

          if (matchFinished) {
            if (match.finish(now)) {
              try {
                publishMatchFinishedEvent(match);
              } catch (Exception e) {
                LOGGER.error(
                    "Failed to publish MatchFinished event immediately for match {}",
                    match.matchId(),
                    e);
              }
            }
          }
        }
      }
      nextIndex = match.currentRoundIndex() + 1;
    }

    GuessResponseDto response = new GuessResponseDto();
    response.setCorrect(eval.correct());
    response.setRightArtist(eval.rightArtist());

    if (!eval.roundEnded()) {
      response.setRoundEnded(false);
      response.setRound(toRoundStateDto(current, match, userId));
      response.setEnd(null);
      return response;
    }

    if (matchFinished) {
      try {
        response.setRoundEnded(true);
        response.setRound(null);
        response.setEnd(SoloDtoMapper.toRoundEndDto(current, match, true, null));
        return response;
      } finally {
        matchManager.clearActiveMatch(userId, matchId);
      }
    } else {
      SoloRoundData nextRoundData;
      synchronized (match) {
        if (match.roundDataList().size() > nextIndex) {
          nextRoundData = match.roundDataList().get(nextIndex);
        } else {
          nextRoundData = null;
        }
      }

      if (nextRoundData == null) {
        SoloRoundData prepared = prepareNextRound(match, nextIndex, now);
        nextRoundData = commitNextRound(match, nextIndex, prepared);
      }

      RoundStateDto nextRoundDto = toRoundStateDto(nextRoundData, match, userId);

      response.setRoundEnded(true);
      response.setRound(null);
      response.setEnd(SoloDtoMapper.toRoundEndDto(current, match, false, nextRoundDto));
      return response;
    }
  }

  public RoundResultDto getRoundResult(UUID userId, UUID matchId, int roundNo) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    synchronized (match) {
      if (roundNo < 1 || roundNo > match.roundCount()) {
        throw new ApiException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Round not found");
      }

      if (roundNo > match.roundDataList().size()) {
        throw new ApiException(
            HttpStatus.CONFLICT, "ROUND_NOT_FINISHED", "Round is not finished yet");
      }

      SoloRoundData roundData = match.roundDataList().get(roundNo - 1);
      RoundStatus st = roundData.round().status();
      if (st == RoundStatus.WAITING || st == RoundStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "ROUND_NOT_FINISHED", "Round is not finished yet");
      }

      return SoloDtoMapper.toRoundResultDto(roundData, match);
    }
  }

  public MatchResultDto getMatchResult(UUID userId, UUID matchId) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    synchronized (match) {
      if (match.status() == SoloMatchStatus.IN_PROGRESS) {
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_FINISHED", "Match is still in progress");
      }

      return SoloDtoMapper.toMatchResultDto(match);
    }
  }

  public MatchResultDto abandonSoloMatch(UUID userId, UUID matchId) {
    Instant now = clock.instant();
    SoloMatch match = getAndValidateMatch(userId, matchId, now);

    if (match.mode() == SoloModeDto.RANKED) {
      throw new ApiException(
          HttpStatus.CONFLICT, "RANKED_MATCH_CANNOT_ABANDON", "Ranked matches cannot be abandoned");
    }

    try {
      synchronized (match) {
        if (match.status() != SoloMatchStatus.IN_PROGRESS) {
          throw new ApiException(
              HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
        }

        if (match.currentRoundIndex() < match.roundDataList().size()) {
          audioService.releaseRoundAudio(match.getCurrentRoundData().roundId());
        }

        boolean abandoned = match.abandon(now);
        if (abandoned) {
          publishMatchFinishedEvent(match);
        }
      }
      return SoloDtoMapper.toMatchResultDto(match);
    } finally {
      matchManager.clearActiveMatch(userId, matchId);
    }
  }

  public void expireInactiveMatches() {
    try {
      Instant now = clock.instant();
      for (SoloMatch match : matchManager.getAllMatches()) {
        try {
          if (match.status() == SoloMatchStatus.IN_PROGRESS && match.isExpired(now)) {
            expireMatch(match, now);
          } else if (match.status() != SoloMatchStatus.IN_PROGRESS
              && !match.isMatchFinishedPublished()) {
            publishMatchFinishedEvent(match);
          }
        } catch (Exception e) {
          LOGGER.error("Error expiring/publishing match {}", match.matchId(), e);
        }
      }
    } catch (Throwable t) {
      LOGGER.error("Unexpected error in expireInactiveMatches", t);
    } finally {
      try {
        Instant now = clock.instant();
        matchManager.purgeOldMatches(now.minus(Duration.ofMinutes(30)));
      } catch (Throwable t) {
        LOGGER.error("Unexpected error in purgeOldMatches", t);
      }
    }
  }

  private void expireMatch(SoloMatch match, Instant now) {
    try {
      boolean expired;
      synchronized (match) {
        if (match.status() != SoloMatchStatus.IN_PROGRESS) {
          return;
        }
        if (match.currentRoundIndex() < match.roundDataList().size()) {
          audioService.releaseRoundAudio(match.getCurrentRoundData().roundId());
        }
        expired = match.expire(now);
      }
      if (expired) {
        publishMatchFinishedEvent(match);
      }
    } finally {
      matchManager.clearActiveMatch(match.userId(), match.matchId());
    }
  }

  private SoloMatch getAndValidateMatch(UUID userId, UUID matchId, Instant now) {
    SoloMatch match =
        matchManager
            .getMatchById(matchId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Match not found"));

    if (!match.userId().equals(userId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Match not found");
    }

    if (match.isExpired(now)) {
      expireMatch(match, now);
    }

    return match;
  }

  private SoloRoundData prepareNextRound(SoloMatch match, int roundIndex, Instant now) {
    int roundNo = roundIndex + 1;
    UUID roundId = UUID.randomUUID();

    TrackInfo track = match.candidateTracks().get(roundIndex);
    AudioPreparationResult prep =
        audioService.prepareRoundAudio(
            roundId, track.id(), track.provider(), track.providerTrackId());

    // BR-27: audio không phát được thì đổi bài khác cùng bộ (tối đa 5 lần)
    int attempts = 0;
    while (!prep.playable()) {
      if (++attempts > MAX_REPLACEMENT_ATTEMPTS) {
        LOGGER.warn(
            "Exceeded max replacement attempts ({}) for round {}",
            MAX_REPLACEMENT_ATTEMPTS,
            roundNo);
        throw new NotEnoughTracksException(1);
      }

      LOGGER.warn(
          "Audio preparation failed for trackId={}, reason={}. Selecting replacement track (BR-27, attempt {}).",
          track.id(),
          prep.failureReason(),
          attempts);

      TrackInfo replacement = fetchReplacementTrack(match);
      if (replacement == null) {
        throw new NotEnoughTracksException(1);
      }

      match.addExcludedTrack(replacement.id());
      track = replacement;
      prep =
          audioService.prepareRoundAudio(
              roundId, track.id(), track.provider(), track.providerTrackId());
    }

    TargetTrack targetTrack = new TargetTrack(track.id(), track.title(), track.artistName());
    Round round = Round.standardRound(roundNo, targetTrack, false);
    round.start(now);

    int scoreBefore = match.calculateTotalScore();
    int comboBefore = match.comboTracker().currentStreak();

    return new SoloRoundData(roundNo, roundId, track, round, scoreBefore, comboBefore);
  }

  private @Nullable TrackInfo fetchReplacementTrack(SoloMatch match) {
    List<TrackInfo> list;
    if (match.mode() == SoloModeDto.RANKED) {
      list =
          catalogService.selectRandomTracksByPlaylistCode(
              STANDARD_PLAYLIST_CODE, 1, match.excludedTrackIds());
    } else if (match.playlistId() != null) {
      list =
          catalogService.selectRandomTracksByPlaylist(
              match.playlistId(), 1, match.excludedTrackIds());
    } else {
      list =
          catalogService.selectRandomTracks(
              match.filter() != null && !match.filter().isEmpty() ? match.filter() : null,
              1,
              match.excludedTrackIds());
    }
    return list.isEmpty() ? null : list.get(0);
  }

  private void publishMatchFinishedEvent(SoloMatch match) {
    synchronized (match) {
      if (!match.tryClaimMatchFinishedPublishing()) {
        return;
      }
    }

    try {
      List<RoundSummary> roundSummaries = new ArrayList<>();
      List<RoundPlayerResult> playerResults = new ArrayList<>();
      long totalCorrectTimeMs = 0L;

      // Chỉ đưa các vòng đã xong vào rounds (BR-23)
      for (SoloRoundData data : match.getCompletedRounds()) {
        TrackInfo track = data.track();
        roundSummaries.add(
            new RoundSummary(
                data.roundNo(),
                track.id(),
                track.title(),
                track.artistName(),
                track.provider(),
                track.providerTrackId()));

        Round r = data.round();
        RoundOutcome outcome = r.outcome() != null ? r.outcome() : RoundOutcome.GAVE_UP;
        Integer correctLevel = (outcome == RoundOutcome.CORRECT) ? r.currentLevel() : null;
        int baseScore = r.scoreDetail().baseScore();
        int comboPercent = r.scoreDetail().comboPercent();
        int speedBonus = r.scoreDetail().speedBonus();
        int roundScore = r.scoreDetail().roundScore();
        int wrongCount = r.wrongCount();

        Long answerTimeMs = null;
        if (r.startedAt() != null && r.endedAt() != null && outcome == RoundOutcome.CORRECT) {
          answerTimeMs = Duration.between(r.startedAt(), r.endedAt()).toMillis();
          totalCorrectTimeMs += answerTimeMs;
        }

        playerResults.add(
            new RoundPlayerResult(
                data.roundNo(),
                outcome,
                correctLevel,
                baseScore,
                comboPercent,
                speedBonus,
                roundScore,
                wrongCount,
                answerTimeMs));
      }

      PlayerSummary playerSummary =
          new PlayerSummary(
              match.userId(),
              match.nickname(),
              match.isGuest(),
              match.calculateTotalScore(),
              1, // solo: rank 1
              match.calculateCorrectCount(),
              totalCorrectTimeMs,
              playerResults);

      UUID playlistId =
          match.mode() == SoloModeDto.RANKED
              ? catalogService
                  .findPlaylistByCode(STANDARD_PLAYLIST_CODE)
                  .map(PlaylistInfo::id)
                  .orElse(null)
              : match.playlistId();

      MatchFinished event =
          new MatchFinished(
              UUID.randomUUID(),
              match.matchId(),
              MatchType.SOLO,
              match.mode().getValue(),
              match.isRanked(),
              match.toMatchEndStatus(),
              match.roundCount(),
              playlistId,
              serializeFilter(match.filterDto()),
              match.startedAt(),
              match.endedAt() != null ? match.endedAt() : clock.instant(),
              roundSummaries,
              List.of(playerSummary));

      transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

      synchronized (match) {
        match.markMatchFinishedPublished();
      }
    } catch (Throwable t) {
      synchronized (match) {
        match.releaseMatchFinishedPublishing();
      }
      throw t;
    }
  }

  private SoloRoundData commitNextRound(SoloMatch match, int nextIndex, SoloRoundData data) {
    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        audioService.releaseRoundAudio(data.roundId());
        throw new ApiException(
            HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
      }

      if (match.getCurrentRoundData().round().status() != RoundStatus.IN_PROGRESS
          && match.currentRoundIndex() + 1 == nextIndex) {
        match.addRoundData(data);
        match.advanceRound();
        return data;
      } else {
        audioService.releaseRoundAudio(data.roundId());
        if (match.roundDataList().size() > nextIndex) {
          return match.roundDataList().get(nextIndex);
        }
        return match.getCurrentRoundData();
      }
    }
  }

  private void ensureNextRoundReady(SoloMatch match, Instant now) {
    int nextIndex;
    synchronized (match) {
      if (match.status() != SoloMatchStatus.IN_PROGRESS) {
        return;
      }
      SoloRoundData current = match.getCurrentRoundData();
      if (current.round().status() == RoundStatus.IN_PROGRESS) {
        return;
      }
      if (current.roundNo() >= match.roundCount()) {
        return;
      }
      nextIndex = match.currentRoundIndex() + 1;
      if (match.roundDataList().size() > nextIndex) {
        return;
      }
    }

    SoloRoundData nextRoundData = prepareNextRound(match, nextIndex, now);
    commitNextRound(match, nextIndex, nextRoundData);
  }

  private @Nullable String serializeFilter(@Nullable SoloFilterDto filterDto) {
    if (filterDto == null || isEmptyFilter(filterDto)) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(filterDto);
    } catch (Exception e) {
      LOGGER.warn("Failed to serialize SoloFilterDto for match", e);
      return null;
    }
  }

  private boolean isEmptyFilter(SoloFilterDto filter) {
    boolean hasGenres = filter.getGenreCodes() != null && !filter.getGenreCodes().isEmpty();
    boolean hasCountries = filter.getCountryCodes() != null && !filter.getCountryCodes().isEmpty();
    boolean hasDecades = filter.getDecades() != null && !filter.getDecades().isEmpty();
    boolean hasArtists = filter.getArtistIds() != null && !filter.getArtistIds().isEmpty();
    return !hasGenres && !hasCountries && !hasDecades && !hasArtists;
  }

  private SoloMatchDto toSoloMatchDto(SoloMatch match, UUID userId) {
    SoloRoundData current = match.getCurrentRoundData();
    RoundStateDto currentRoundDto = toRoundStateDto(current, match, userId);
    return SoloDtoMapper.toSoloMatchDto(match, currentRoundDto);
  }

  private RoundStateDto toRoundStateDto(SoloRoundData roundData, SoloMatch match, UUID userId) {
    boolean inProgress;
    synchronized (match) {
      inProgress = roundData.round().status() == RoundStatus.IN_PROGRESS;
    }

    if (inProgress && !audioService.isRoundPlayable(roundData.roundId())) {
      TrackInfo track = roundData.track();
      AudioPreparationResult prep =
          audioService.prepareRoundAudio(
              roundData.roundId(), track.id(), track.provider(), track.providerTrackId());

      int attempts = 0;
      while (!prep.playable()) {
        if (++attempts > MAX_REPLACEMENT_ATTEMPTS) {
          LOGGER.warn(
              "Exceeded max replacement attempts ({}) for round {}",
              MAX_REPLACEMENT_ATTEMPTS,
              roundData.roundNo());
          throw new NotEnoughTracksException(1);
        }

        LOGGER.warn(
            "Audio preparation failed for trackId={}, reason={}. Selecting replacement track (BR-27, attempt {}).",
            track.id(),
            prep.failureReason(),
            attempts);

        TrackInfo replacement = fetchReplacementTrack(match);
        if (replacement == null) {
          throw new NotEnoughTracksException(1);
        }

        match.addExcludedTrack(replacement.id());
        track = replacement;
        prep =
            audioService.prepareRoundAudio(
                roundData.roundId(), track.id(), track.provider(), track.providerTrackId());
      }

      if (track != roundData.track()) {
        synchronized (match) {
          if (roundData.round().status() == RoundStatus.IN_PROGRESS) {
            int currentLevel = roundData.round().currentLevel();
            TargetTrack targetTrack =
                new TargetTrack(track.id(), track.title(), track.artistName());
            Round newRound = Round.standardRound(roundData.roundNo(), targetTrack, false);
            newRound.start(clock.instant());
            for (int lvl = 1; lvl < currentLevel; lvl++) {
              newRound.listenMore();
            }
            roundData.replaceTrackAndRound(track, newRound);
          } else {
            audioService.releaseRoundAudio(roundData.roundId());
          }
        }
      }
    }

    int level;
    synchronized (match) {
      level = roundData.round().currentLevel();
    }
    AudioClipDto clip = audioService.createAudioClip(roundData.roundId(), userId, level);
    return SoloDtoMapper.toRoundStateDto(roundData, match, clip, clock.instant());
  }

  private @Nullable TrackFilter toTrackFilter(@Nullable SoloFilterDto dto) {
    if (dto == null) {
      return null;
    }
    return new TrackFilter(
        dto.getGenreCodes(), dto.getCountryCodes(), dto.getDecades(), dto.getArtistIds());
  }
}
