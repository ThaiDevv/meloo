package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundStateMachineTest {

  private TargetTrack sampleTrack;
  private Instant startInstant;
  private ComboTracker comboTracker;

  @BeforeEach
  void setUp() {
    sampleTrack = new TargetTrack(UUID.randomUUID(), "Chúng Ta Của Tương Lai", "Sơn Tùng M-TP");
    startInstant = Instant.parse("2026-10-26T12:00:00Z");
    comboTracker = new ComboTracker(true);
  }

  @Test
  @DisplayName("AC-6: Trạng thái ban đầu là WAITING, chỉ start mới sang IN_PROGRESS")
  void testInitialStateAndStart() {
    Round round = Round.standardRound(1, sampleTrack, true);
    assertThat(round.status()).isEqualTo(RoundStatus.WAITING);
    assertThat(round.currentLevel()).isEqualTo(1);
    assertThat(round.wrongCount()).isZero();

    // Không thể đoán, nghe thêm hay bỏ qua khi chưa start
    assertThatThrownBy(() -> round.listenMore())
        .isInstanceOf(InvalidRoundStateException.class)
        .hasMessageContaining("WAITING");
    assertThatThrownBy(() -> round.skip()).isInstanceOf(InvalidRoundStateException.class);
    assertThatThrownBy(
            () ->
                round.submitGuess(
                    "Bài Nào Đó", "Ai Đó", startInstant.plusMillis(1000), comboTracker))
        .isInstanceOf(InvalidRoundStateException.class);

    // Bắt đầu vòng
    round.start(startInstant);
    assertThat(round.status()).isEqualTo(RoundStatus.IN_PROGRESS);
    assertThat(round.startedAt()).isEqualTo(startInstant);

    // Không thể start lại khi đang chơi
    assertThatThrownBy(() -> round.start(startInstant))
        .isInstanceOf(InvalidRoundStateException.class);
  }

  @Test
  @DisplayName("AC-2, AC-6: Nghe thêm mở mức kế tiếp; ở mức 5 (30s) không được nghe thêm nữa")
  void testListenMoreProgression() {
    Round round = Round.standardRound(1, sampleTrack, true);
    round.start(startInstant);

    assertThat(round.currentLevel()).isEqualTo(1);
    round.listenMore();
    assertThat(round.currentLevel()).isEqualTo(2);
    round.listenMore();
    assertThat(round.currentLevel()).isEqualTo(3);
    round.listenMore();
    assertThat(round.currentLevel()).isEqualTo(4);
    round.listenMore();
    assertThat(round.currentLevel()).isEqualTo(5);

    // Tại mức 5 (30s), không được nghe thêm nữa
    assertThatThrownBy(round::listenMore)
        .isInstanceOf(InvalidRoundStateException.class)
        .hasMessageContaining("maximum listening level");
  }

  @Test
  @DisplayName("AC-2, AC-6: Bỏ qua chuyển sang HẾT_LƯỢT (GAVE_UP) với 0 điểm")
  void testSkipTransitionsToOutOfAttempts() {
    Round round = Round.standardRound(1, sampleTrack, true);
    round.start(startInstant);

    RoundScoreDetail score = round.skip();
    assertThat(round.status()).isEqualTo(RoundStatus.OUT_OF_ATTEMPTS);
    assertThat(round.outcome()).isEqualTo(RoundOutcome.GAVE_UP);
    assertThat(score.roundScore()).isZero();

    // Đã kết thúc vòng thì không thể thao tác tiếp
    assertThatThrownBy(round::skip).isInstanceOf(InvalidRoundStateException.class);
    assertThatThrownBy(round::listenMore).isInstanceOf(InvalidRoundStateException.class);
  }

  @Test
  @DisplayName("AC-2, BR-06: Đoán sai tăng mức; đúng ca sĩ nhưng sai bài báo gợi ý 'Đúng ca sĩ!'")
  void testGuessWrongAndRightArtistHint() {
    Round round = Round.standardRound(1, sampleTrack, false);
    round.start(startInstant);

    // Đoán sai ca sĩ lẫn bài ở mức 1
    GuessEvaluation eval1 =
        round.submitGuess(
            "Em Của Ngày Hôm Qua", "Đen Vâu", startInstant.plusSeconds(2), comboTracker);
    assertThat(eval1.correct()).isFalse();
    assertThat(eval1.rightArtist()).isFalse();
    assertThat(eval1.roundEnded()).isFalse();
    assertThat(round.currentLevel()).isEqualTo(2);
    assertThat(round.wrongCount()).isEqualTo(1);
    assertThat(round.status()).isEqualTo(RoundStatus.IN_PROGRESS);

    // Đoán đúng ca sĩ nhưng sai tên bài ở mức 2 (BR-06)
    GuessEvaluation eval2 =
        round.submitGuess(
            "Cơn Mưa Ngang Qua", "Sơn Tùng M-TP", startInstant.plusSeconds(5), comboTracker);
    assertThat(eval2.correct()).isFalse();
    assertThat(eval2.rightArtist()).isTrue(); // Báo gợi ý đúng ca sĩ!
    assertThat(eval2.roundEnded()).isFalse();
    assertThat(round.currentLevel()).isEqualTo(3);
    assertThat(round.wrongCount()).isEqualTo(2);
    assertThat(round.status()).isEqualTo(RoundStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("AC-2: Đoán sai ở mức 5 (30s) chuyển sang HẾT_LƯỢT (WRONG_OUT) với 0 điểm")
  void testGuessWrongAtMaxLevel() {
    Round round = Round.standardRound(1, sampleTrack, false);
    round.start(startInstant);

    // Xuống mức 5
    round.listenMore(); // 2
    round.listenMore(); // 3
    round.listenMore(); // 4
    round.listenMore(); // 5
    assertThat(round.currentLevel()).isEqualTo(5);

    // Đoán sai ở mức 5
    GuessEvaluation eval =
        round.submitGuess("Sai Bai Hat", "Son Tung", startInstant.plusSeconds(25), comboTracker);
    assertThat(eval.correct()).isFalse();
    assertThat(eval.roundEnded()).isTrue();
    assertThat(eval.status()).isEqualTo(RoundStatus.OUT_OF_ATTEMPTS);
    assertThat(eval.outcome()).isEqualTo(RoundOutcome.WRONG_OUT);
    assertThat(eval.scoreDetail().roundScore()).isZero();
    assertThat(round.status()).isEqualTo(RoundStatus.OUT_OF_ATTEMPTS);
    assertThat(round.outcome()).isEqualTo(RoundOutcome.WRONG_OUT);
  }

  @Test
  @DisplayName(
      "AC-4: Đoán đúng ở mức 5s, vòng 2 của combo, còn 30.000 ms -> 1.013 điểm (BR ví dụ 3.2)")
  void testExample3Point2ScoreCalculation() {
    Round round = Round.standardRound(2, sampleTrack, true);
    round.start(startInstant);

    // Người chơi đã đúng ở mức 2s ở vòng trước (streak = 1)
    comboTracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(comboTracker.currentStreak()).isEqualTo(1);

    // Mở sang mức 2 (5s)
    round.listenMore();
    assertThat(round.currentLevel()).isEqualTo(2);

    // Đoán đúng khi server nhận lúc còn 30.000 ms (elapsed = 15.000 ms)
    Instant guessTime = startInstant.plusMillis(15_000);
    GuessEvaluation eval =
        round.submitGuess("Chúng Ta Của Tương Lai (Remix)", "Sơn Tùng", guessTime, comboTracker);

    assertThat(eval.correct()).isTrue();
    assertThat(eval.roundEnded()).isTrue();
    assertThat(eval.status()).isEqualTo(RoundStatus.CORRECT);
    assertThat(eval.outcome()).isEqualTo(RoundOutcome.CORRECT);
    assertThat(eval.correctLevel()).isEqualTo(2);

    // Kiểm tra chi tiết điểm:
    // Cơ bản: 800
    // Combo: 10% (800 * 0.1 = 80 -> 880)
    // Tốc độ: ⌊200 * 30.000 / 45.000⌋ = 133
    // Tổng: 880 + 133 = 1013
    RoundScoreDetail score = eval.scoreDetail();
    assertThat(score.baseScore()).isEqualTo(800);
    assertThat(score.comboPercent()).isEqualTo(10);
    assertThat(score.speedBonus()).isEqualTo(133);
    assertThat(score.roundScore()).isEqualTo(1013);

    assertThat(comboTracker.currentStreak()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "AC-4: Lượt đoán nhận từ mốc 45.000 ms trở đi bị từ chối và vòng chuyển sang TIMEOUT")
  void testGuessAtOrAfter45000msCutoffRejected() {
    Round round = Round.standardRound(1, sampleTrack, true);
    round.start(startInstant);

    // Đoán đúng mốc 45.000 ms
    Instant exact45s = startInstant.plusMillis(45_000);
    assertThatThrownBy(
            () -> round.submitGuess("Chúng Ta Của Tương Lai", "Sơn Tùng", exact45s, comboTracker))
        .isInstanceOf(GuessRejectedException.class)
        .hasMessageContaining("45000 ms >= 45000 ms");

    assertThat(round.status()).isEqualTo(RoundStatus.TIMEOUT);
    assertThat(round.outcome()).isEqualTo(RoundOutcome.TIMEOUT);
    assertThat(round.scoreDetail().roundScore()).isZero();

    // Đoán sau 45.000 ms (ví dụ 45.500 ms)
    Round round2 = Round.standardRound(1, sampleTrack, true);
    round2.start(startInstant);
    Instant after45s = startInstant.plusMillis(45_500);
    assertThatThrownBy(
            () -> round2.submitGuess("Chúng Ta Của Tương Lai", "Sơn Tùng", after45s, comboTracker))
        .isInstanceOf(GuessRejectedException.class);

    assertThat(round2.status()).isEqualTo(RoundStatus.TIMEOUT);
  }

  @Test
  @DisplayName("AC-4: Đoán sát mốc 45.000 ms (còn 1 ms - elapsed 44.999 ms) được chấp nhận")
  void testGuessJustBeforeCutoffAccepted() {
    Round round = Round.standardRound(1, sampleTrack, true);
    round.start(startInstant);

    Instant justBefore45s = startInstant.plusMillis(44_999);
    GuessEvaluation eval =
        round.submitGuess("Chúng Ta Của Tương Lai", "Sơn Tùng", justBefore45s, comboTracker);

    assertThat(eval.correct()).isTrue();
    assertThat(round.status()).isEqualTo(RoundStatus.CORRECT);
    assertThat(eval.scoreDetail().baseScore()).isEqualTo(1000);
    assertThat(eval.scoreDetail().speedBonus()).isZero(); // ⌊200 * 1 / 45.000⌋ = 0
    assertThat(eval.scoreDetail().roundScore()).isEqualTo(1000);
  }

  @Test
  @DisplayName("AC-6: Gọi timeout chuyển sang HẾT_GIỜ (TIMEOUT)")
  void testTimeoutMethod() {
    Round round = Round.standardRound(1, sampleTrack, true);
    round.start(startInstant);

    RoundScoreDetail score = round.timeout(startInstant.plusSeconds(45), comboTracker);
    assertThat(round.status()).isEqualTo(RoundStatus.TIMEOUT);
    assertThat(round.outcome()).isEqualTo(RoundOutcome.TIMEOUT);
    assertThat(score.roundScore()).isZero();

    // Sau khi timeout không thể thao tác tiếp
    assertThatThrownBy(() -> round.timeout(startInstant.plusSeconds(46), comboTracker))
        .isInstanceOf(InvalidRoundStateException.class);
  }
}
