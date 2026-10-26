package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainModelTest {

  @Test
  @DisplayName("ListeningLevel: validation constructor và getters")
  void testListeningLevelValidation() {
    ListeningLevel level = new ListeningLevel(1, 2, 1000);
    assertThat(level.levelIndex()).isEqualTo(1);
    assertThat(level.durationSeconds()).isEqualTo(2);
    assertThat(level.baseScore()).isEqualTo(1000);

    assertThatThrownBy(() -> new ListeningLevel(0, 2, 1000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListeningLevel(1, 0, 1000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListeningLevel(1, 2, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("GameRules: validation constructor, getters, và invalid index")
  void testGameRulesValidation() {
    GameRules rules = GameRules.defaultRules();
    assertThat(rules.maxLevel()).isEqualTo(5);
    assertThat(rules.roundTimeLimitMs()).isEqualTo(45_000L);
    assertThat(rules.timeoutCutoffMs()).isEqualTo(45_000L);
    assertThat(rules.maxSpeedBonus()).isEqualTo(200);

    assertThatThrownBy(() -> rules.getLevel(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rules.getLevel(6)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new GameRules(null, 45000, 45000, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameRules(List.of(), 45000, 45000, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameRules(GameRules.DEFAULT_LEVELS, 0, 45000, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameRules(GameRules.DEFAULT_LEVELS, 45000, 0, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameRules(GameRules.DEFAULT_LEVELS, 45000, 45000, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("RoundStatus: isTerminal logic")
  void testRoundStatusIsTerminal() {
    assertThat(RoundStatus.WAITING.isTerminal()).isFalse();
    assertThat(RoundStatus.IN_PROGRESS.isTerminal()).isFalse();
    assertThat(RoundStatus.CORRECT.isTerminal()).isTrue();
    assertThat(RoundStatus.OUT_OF_ATTEMPTS.isTerminal()).isTrue();
    assertThat(RoundStatus.TIMEOUT.isTerminal()).isTrue();
  }

  @Test
  @DisplayName("TargetTrack: validation và getters")
  void testTargetTrackValidation() {
    UUID id = UUID.randomUUID();
    TargetTrack track = new TargetTrack(id, "Title", "Artist");
    assertThat(track.trackId()).isEqualTo(id);
    assertThat(track.title()).isEqualTo("Title");
    assertThat(track.artistName()).isEqualTo("Artist");

    assertThatThrownBy(() -> new TargetTrack(null, "Title", "Artist"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TargetTrack(id, null, "Artist"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TargetTrack(id, "  ", "Artist"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TargetTrack(id, "Title", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TargetTrack(id, "Title", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("RoundScoreDetail: validation và zero factory")
  void testRoundScoreDetailValidation() {
    RoundScoreDetail zero = RoundScoreDetail.zero();
    assertThat(zero.baseScore()).isZero();
    assertThat(zero.comboPercent()).isZero();
    assertThat(zero.speedBonus()).isZero();
    assertThat(zero.roundScore()).isZero();

    assertThatThrownBy(() -> new RoundScoreDetail(-1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RoundScoreDetail(0, -1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RoundScoreDetail(0, 0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RoundScoreDetail(0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("GuessEvaluation: validation")
  void testGuessEvaluationValidation() {
    assertThatThrownBy(
            () ->
                new GuessEvaluation(
                    false, false, false, null, null, null, 0, RoundScoreDetail.zero()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GuessEvaluation(
                    false, false, false, RoundStatus.IN_PROGRESS, null, null, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GuessEvaluation(
                    false,
                    false,
                    false,
                    RoundStatus.IN_PROGRESS,
                    null,
                    null,
                    -1,
                    RoundScoreDetail.zero()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("MatchRanking: PlayerScore và RankedPlayer validation")
  void testMatchRankingValidation() {
    assertThat(MatchRanking.rank(null)).isEmpty();
    assertThat(MatchRanking.rank(List.of())).isEmpty();

    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> new MatchRanking.PlayerScore(id, null, false, 100, 100, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MatchRanking.PlayerScore(id, " ", false, 100, 100, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MatchRanking.PlayerScore(id, "Player", false, -1, 100, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MatchRanking.PlayerScore(id, "Player", false, 100, -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MatchRanking.PlayerScore(id, "Player", false, 100, 100, -1))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new MatchRanking.RankedPlayer(id, "Player", false, 100, 100, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("TrackValidator: null list và empty handling")
  void testTrackValidatorEdgeCases() {
    assertThat(TrackValidator.hasDuplicateTracks(null)).isFalse();
    assertThat(TrackValidator.hasDuplicateTracks(List.of())).isFalse();

    TrackValidator.ValidationResult rNull = TrackValidator.validate(null, 5);
    assertThat(rNull.valid()).isFalse();
    assertThat(rNull.missingTracksCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("Round constructor validation và getters")
  void testRoundConstructorValidationAndGetters() {
    TargetTrack track = new TargetTrack(UUID.randomUUID(), "Title", "Artist");
    GameRules rules = GameRules.defaultRules();

    assertThatThrownBy(() -> new Round(0, track, rules, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Round(1, null, rules, true))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Round(1, track, null, true))
        .isInstanceOf(NullPointerException.class);

    Round round = new Round(1, track, rules, true);
    assertThat(round.roundNo()).isEqualTo(1);
    assertThat(round.targetTrack()).isEqualTo(track);
    assertThat(round.gameRules()).isEqualTo(rules);
    assertThat(round.hasTimeLimit()).isTrue();
    assertThat(round.outcome()).isNull();
    assertThat(round.startedAt()).isNull();
    assertThat(round.endedAt()).isNull();
  }
}
