package com.meloo.game.solo;

import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.ComboTracker;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.domain.RoundStatus;
import com.meloo.game.events.MatchEndStatus;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.generated.model.SoloModeDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Aggregate quản lý trạng thái phiên chơi đơn in-memory (BR-09, BR-23, BR-27). */
public class SoloMatch {

  private static final Duration EXPIRATION_THRESHOLD = Duration.ofMinutes(30);

  private final UUID matchId;
  private final UUID userId;
  private final String nickname;
  private final boolean isGuest;
  private final SoloModeDto mode;
  private boolean ranked;
  private final int roundCount;
  private final @Nullable UUID playlistId;
  private final @Nullable TrackFilter filter;
  private final @Nullable SoloFilterDto filterDto;

  private final Instant startedAt;
  private Instant lastActivityAt;
  private @Nullable Instant endedAt;
  private SoloMatchStatus status;
  private boolean matchFinishedEventPublished = false;

  private final List<SoloRoundData> roundDataList = new ArrayList<>();
  private final List<TrackInfo> candidateTracks = new ArrayList<>();
  private final Set<UUID> excludedTrackIds = new HashSet<>();
  private final ComboTracker comboTracker;

  private int currentRoundIndex = 0;

  public SoloMatch(
      UUID matchId,
      UUID userId,
      String nickname,
      boolean isGuest,
      SoloModeDto mode,
      boolean ranked,
      int roundCount,
      @Nullable UUID playlistId,
      @Nullable TrackFilter filter,
      @Nullable SoloFilterDto filterDto,
      List<TrackInfo> candidateTracks,
      Instant startTime) {
    this.matchId = Objects.requireNonNull(matchId, "matchId must not be null");
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
    this.isGuest = isGuest;
    this.mode = Objects.requireNonNull(mode, "mode must not be null");
    this.ranked = ranked;
    this.roundCount = roundCount;
    this.playlistId = playlistId;
    this.filter = filter;
    this.filterDto = filterDto;
    this.startedAt = Objects.requireNonNull(startTime, "startTime must not be null");
    this.lastActivityAt = startTime;
    this.status = SoloMatchStatus.IN_PROGRESS;
    this.comboTracker = new ComboTracker(true);

    if (candidateTracks != null) {
      this.candidateTracks.addAll(candidateTracks);
      for (TrackInfo t : candidateTracks) {
        this.excludedTrackIds.add(t.id());
      }
    }
  }

  public UUID matchId() {
    return matchId;
  }

  public UUID userId() {
    return userId;
  }

  public String nickname() {
    return nickname;
  }

  public boolean isGuest() {
    return isGuest;
  }

  public SoloModeDto mode() {
    return mode;
  }

  public boolean isRanked() {
    return ranked;
  }

  public int roundCount() {
    return roundCount;
  }

  public @Nullable UUID playlistId() {
    return playlistId;
  }

  public @Nullable TrackFilter filter() {
    return filter;
  }

  public @Nullable SoloFilterDto filterDto() {
    return filterDto;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public synchronized Instant lastActivityAt() {
    return lastActivityAt;
  }

  public synchronized @Nullable Instant endedAt() {
    return endedAt;
  }

  public synchronized SoloMatchStatus status() {
    return status;
  }

  public synchronized MatchEndStatus toMatchEndStatus() {
    return status == SoloMatchStatus.FINISHED ? MatchEndStatus.FINISHED : MatchEndStatus.ABANDONED;
  }

  public synchronized Instant getExpiresAt() {
    return lastActivityAt.plus(EXPIRATION_THRESHOLD);
  }

  public synchronized boolean isExpired(Instant now) {
    if (status != SoloMatchStatus.IN_PROGRESS) {
      return false;
    }
    return Duration.between(lastActivityAt, now).compareTo(EXPIRATION_THRESHOLD) > 0;
  }

  public synchronized void touch(Instant now) {
    this.lastActivityAt = now;
  }

  public synchronized List<SoloRoundData> roundDataList() {
    return Collections.unmodifiableList(new ArrayList<>(roundDataList));
  }

  public synchronized List<TrackInfo> candidateTracks() {
    return Collections.unmodifiableList(new ArrayList<>(candidateTracks));
  }

  public synchronized Set<UUID> excludedTrackIds() {
    return Collections.unmodifiableSet(new HashSet<>(excludedTrackIds));
  }

  public synchronized void addExcludedTrack(UUID trackId) {
    this.excludedTrackIds.add(trackId);
  }

  public ComboTracker comboTracker() {
    return comboTracker;
  }

  public synchronized int currentRoundIndex() {
    return currentRoundIndex;
  }

  public synchronized SoloRoundData getCurrentRoundData() {
    if (currentRoundIndex < 0 || currentRoundIndex >= roundDataList.size()) {
      throw new IllegalStateException("No active round available at index " + currentRoundIndex);
    }
    return roundDataList.get(currentRoundIndex);
  }

  public synchronized void addRoundData(SoloRoundData roundData) {
    this.roundDataList.add(roundData);
  }

  public synchronized void advanceRound() {
    this.currentRoundIndex++;
  }

  public synchronized int calculateTotalScore() {
    int total = 0;
    for (SoloRoundData data : roundDataList) {
      if (isRoundCompleted(data)) {
        total += data.round().scoreDetail().roundScore();
      }
    }
    return total;
  }

  public synchronized int calculateCorrectCount() {
    int count = 0;
    for (SoloRoundData data : roundDataList) {
      if (isRoundCompleted(data) && data.round().outcome() == RoundOutcome.CORRECT) {
        count++;
      }
    }
    return count;
  }

  public synchronized List<SoloRoundData> getCompletedRounds() {
    List<SoloRoundData> completed = new ArrayList<>();
    for (SoloRoundData data : roundDataList) {
      if (isRoundCompleted(data)) {
        completed.add(data);
      }
    }
    return completed;
  }

  private boolean isRoundCompleted(SoloRoundData data) {
    RoundStatus st = data.round().status();
    return st == RoundStatus.CORRECT
        || st == RoundStatus.OUT_OF_ATTEMPTS
        || st == RoundStatus.TIMEOUT;
  }

  public synchronized boolean finish(Instant now) {
    if (this.status != SoloMatchStatus.IN_PROGRESS) {
      return false;
    }
    this.status = SoloMatchStatus.FINISHED;
    this.endedAt = now;
    this.lastActivityAt = now;
    return true;
  }

  public synchronized boolean abandon(Instant now) {
    if (this.status != SoloMatchStatus.IN_PROGRESS) {
      return false;
    }
    this.status = SoloMatchStatus.ABANDONED;
    this.ranked = false;
    this.endedAt = now;
    this.lastActivityAt = now;
    return true;
  }

  public synchronized boolean expire(Instant now) {
    if (this.status != SoloMatchStatus.IN_PROGRESS) {
      return false;
    }
    this.status = SoloMatchStatus.ABANDONED;
    this.ranked = false;
    this.endedAt = getExpiresAt();
    return true;
  }

  public synchronized boolean isMatchFinishedPublished() {
    return matchFinishedEventPublished;
  }

  public synchronized void markMatchFinishedPublished() {
    this.matchFinishedEventPublished = true;
  }
}
