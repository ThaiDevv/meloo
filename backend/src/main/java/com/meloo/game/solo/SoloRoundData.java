package com.meloo.game.solo;

import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.Round;
import java.util.Objects;
import java.util.UUID;

/** Dữ liệu của một vòng chơi đơn bao gồm thực thể Round, track được chọn và ID vòng. */
public class SoloRoundData {

  private final int roundNo;
  private final UUID roundId;
  private TrackInfo track;
  private final Round round;
  private final int scoreBeforeRound;
  private final int comboStreakBeforeRound;
  private int comboStreakAfterRound;

  public SoloRoundData(
      int roundNo,
      UUID roundId,
      TrackInfo track,
      Round round,
      int scoreBeforeRound,
      int comboStreakBeforeRound) {
    this.roundNo = roundNo;
    this.roundId = Objects.requireNonNull(roundId, "roundId must not be null");
    this.track = Objects.requireNonNull(track, "track must not be null");
    this.round = Objects.requireNonNull(round, "round must not be null");
    this.scoreBeforeRound = scoreBeforeRound;
    this.comboStreakBeforeRound = comboStreakBeforeRound;
  }

  public int roundNo() {
    return roundNo;
  }

  public UUID roundId() {
    return roundId;
  }

  public TrackInfo track() {
    return track;
  }

  public void replaceTrack(TrackInfo newTrack) {
    this.track = Objects.requireNonNull(newTrack, "newTrack must not be null");
  }

  public Round round() {
    return round;
  }

  public int scoreBeforeRound() {
    return scoreBeforeRound;
  }

  public int comboStreakBeforeRound() {
    return comboStreakBeforeRound;
  }

  public int comboStreakAfterRound() {
    return comboStreakAfterRound;
  }

  public void setComboStreakAfterRound(int comboStreakAfterRound) {
    this.comboStreakAfterRound = comboStreakAfterRound;
  }
}
