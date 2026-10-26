package com.meloo.game.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * BR-01 -> BR-08: Thực thể aggregate điều phối một vòng chơi. Quản lý máy trạng thái vòng: CHỜ
 * (WAITING) -> ĐANG_CHƠI (IN_PROGRESS) -> ĐÚNG (CORRECT) / HẾT_LƯỢT (OUT_OF_ATTEMPTS) / HẾT_GIỜ
 * (TIMEOUT). Không phụ thuộc Spring hay JPA, đồng hồ được tiêm vào qua Instant.
 */
public class Round {

  private final int roundNo;
  private final TargetTrack targetTrack;
  private final GameRules gameRules;
  private final boolean hasTimeLimit;

  private RoundStatus status;
  private @Nullable RoundOutcome outcome;
  private int currentLevel;
  private int wrongCount;
  private RoundScoreDetail scoreDetail;

  private @Nullable Instant startedAt;
  private @Nullable Instant endedAt;

  public Round(int roundNo, TargetTrack targetTrack, GameRules gameRules, boolean hasTimeLimit) {
    if (roundNo < 1) {
      throw new IllegalArgumentException("roundNo must be >= 1");
    }
    this.roundNo = roundNo;
    this.targetTrack = Objects.requireNonNull(targetTrack, "targetTrack must not be null");
    this.gameRules = Objects.requireNonNull(gameRules, "gameRules must not be null");
    this.hasTimeLimit = hasTimeLimit;

    this.status = RoundStatus.WAITING;
    this.outcome = null;
    this.currentLevel = 1;
    this.wrongCount = 0;
    this.scoreDetail = RoundScoreDetail.zero();
    this.startedAt = null;
    this.endedAt = null;
  }

  public static Round standardRound(int roundNo, TargetTrack targetTrack, boolean hasTimeLimit) {
    return new Round(roundNo, targetTrack, GameRules.defaultRules(), hasTimeLimit);
  }

  public int roundNo() {
    return roundNo;
  }

  public TargetTrack targetTrack() {
    return targetTrack;
  }

  public GameRules gameRules() {
    return gameRules;
  }

  public boolean hasTimeLimit() {
    return hasTimeLimit;
  }

  public RoundStatus status() {
    return status;
  }

  public @Nullable RoundOutcome outcome() {
    return outcome;
  }

  public int currentLevel() {
    return currentLevel;
  }

  public int wrongCount() {
    return wrongCount;
  }

  public RoundScoreDetail scoreDetail() {
    return scoreDetail;
  }

  public @Nullable Instant startedAt() {
    return startedAt;
  }

  public @Nullable Instant endedAt() {
    return endedAt;
  }

  /** Bắt đầu vòng: chuyển từ CHỜ -> ĐANG_CHƠI. */
  public void start(Instant startTime) {
    Objects.requireNonNull(startTime, "startTime must not be null");
    if (status != RoundStatus.WAITING) {
      throw new InvalidRoundStateException("Cannot start round when in state: " + status);
    }
    this.status = RoundStatus.IN_PROGRESS;
    this.startedAt = startTime;
  }

  /** BR-04: Nghe thêm: mở mức kế tiếp. Ở mức cuối không được nghe thêm. */
  public void listenMore() {
    if (status != RoundStatus.IN_PROGRESS) {
      throw new InvalidRoundStateException("Cannot listen more when in state: " + status);
    }
    if (currentLevel >= gameRules.maxLevel()) {
      throw new InvalidRoundStateException(
          "Already at maximum listening level (" + currentLevel + "). Must guess or give up.");
    }
    currentLevel++;
  }

  /** BR-04: Bỏ qua ở mức 30 giây: vòng được 0 điểm, chuyển sang HẾT_LƯỢT. */
  public RoundScoreDetail skip() {
    if (status != RoundStatus.IN_PROGRESS) {
      throw new InvalidRoundStateException("Cannot skip round when in state: " + status);
    }
    this.status = RoundStatus.OUT_OF_ATTEMPTS;
    this.outcome = RoundOutcome.GAVE_UP;
    this.scoreDetail = RoundScoreDetail.zero();
    return this.scoreDetail;
  }

  /**
   * Đánh giá lượt đoán của người chơi.
   *
   * @param guessTitle tên bài hát người chơi đoán
   * @param guessArtist tên ca sĩ người chơi đoán (để kiểm tra gợi ý BR-06)
   * @param receivedAt thời điểm server nhận lượt đoán
   * @param comboTracker tracker theo dõi chuỗi combo
   * @return kết quả đánh giá lượt đoán
   */
  public GuessEvaluation submitGuess(
      String guessTitle,
      @Nullable String guessArtist,
      Instant receivedAt,
      ComboTracker comboTracker) {
    Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    Objects.requireNonNull(comboTracker, "comboTracker must not be null");

    if (status != RoundStatus.IN_PROGRESS) {
      throw new InvalidRoundStateException("Cannot submit guess when in state: " + status);
    }

    // BR-07: Lượt đoán nhận từ mốc 45.000 ms trở đi bị từ chối
    if (hasTimeLimit) {
      if (startedAt == null) {
        throw new InvalidRoundStateException(
            "Round has time limit but was not started with startedAt");
      }
      long elapsedMs = Duration.between(startedAt, receivedAt).toMillis();
      if (elapsedMs >= gameRules.timeoutCutoffMs()) {
        this.status = RoundStatus.TIMEOUT;
        this.outcome = RoundOutcome.TIMEOUT;
        this.endedAt = receivedAt;
        this.scoreDetail = RoundScoreDetail.zero();
        comboTracker.recordRound(RoundOutcome.TIMEOUT, 0);
        throw new GuessRejectedException(
            "Guess received at or after timeout cutoff ("
                + elapsedMs
                + " ms >= "
                + gameRules.timeoutCutoffMs()
                + " ms)");
      }
    }

    boolean correct = TitleNormalizer.matches(guessTitle, targetTrack.title());
    if (correct) {
      this.status = RoundStatus.CORRECT;
      this.outcome = RoundOutcome.CORRECT;
      this.endedAt = receivedAt;

      int baseScore = gameRules.getLevel(currentLevel).baseScore();
      int comboPercent = comboTracker.recordRound(RoundOutcome.CORRECT, currentLevel);
      int speedBonus = 0;
      if (hasTimeLimit && startedAt != null) {
        long elapsedMs = Math.max(0, Duration.between(startedAt, receivedAt).toMillis());
        speedBonus =
            ScoreCalculator.calculateSpeedBonus(
                elapsedMs, gameRules.roundTimeLimitMs(), gameRules.maxSpeedBonus());
      }
      int roundScore = ScoreCalculator.calculateRoundScore(baseScore, comboPercent, speedBonus);
      this.scoreDetail = new RoundScoreDetail(baseScore, comboPercent, speedBonus, roundScore);

      return new GuessEvaluation(
          true, false, true, status, outcome, currentLevel, wrongCount, scoreDetail);
    } else {
      wrongCount++;
      boolean rightArtist =
          guessArtist != null
              && TitleNormalizer.matchesArtist(guessArtist, targetTrack.artistName());

      if (currentLevel < gameRules.maxLevel()) {
        currentLevel++;
        return new GuessEvaluation(
            false, rightArtist, false, status, null, null, wrongCount, RoundScoreDetail.zero());
      } else {
        // Hết lượt ở mức cao nhất (30s)
        this.status = RoundStatus.OUT_OF_ATTEMPTS;
        this.outcome = RoundOutcome.WRONG_OUT;
        this.endedAt = receivedAt;
        this.scoreDetail = RoundScoreDetail.zero();
        comboTracker.recordRound(RoundOutcome.WRONG_OUT, 0);

        return new GuessEvaluation(
            false, rightArtist, true, status, outcome, null, wrongCount, scoreDetail);
      }
    }
  }

  /** Hết giờ vòng chơi: chuyển sang HẾT_GIỜ (TIMEOUT), 0 điểm, đứt combo. */
  public RoundScoreDetail timeout(Instant timedOutAt, ComboTracker comboTracker) {
    Objects.requireNonNull(timedOutAt, "timedOutAt must not be null");
    Objects.requireNonNull(comboTracker, "comboTracker must not be null");

    if (status != RoundStatus.IN_PROGRESS) {
      throw new InvalidRoundStateException("Cannot timeout round when in state: " + status);
    }
    this.status = RoundStatus.TIMEOUT;
    this.outcome = RoundOutcome.TIMEOUT;
    this.endedAt = timedOutAt;
    this.scoreDetail = RoundScoreDetail.zero();
    comboTracker.recordRound(RoundOutcome.TIMEOUT, 0);
    return this.scoreDetail;
  }
}
