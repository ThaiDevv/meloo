package com.meloo.game.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * BR-10: Xếp hạng và phân định hòa: Thắng = tổng điểm cao nhất; hòa thì ai có tổng thời gian trả
 * lời đúng ít hơn thắng (tính bằng mili giây theo đồng hồ server).
 */
public final class MatchRanking {

  private MatchRanking() {}

  public record PlayerScore(
      @Nullable UUID userId,
      String nickname,
      boolean isGuest,
      int totalScore,
      long correctAnswerTimeMs,
      int correctCount) {

    public PlayerScore {
      if (nickname == null || nickname.isBlank()) {
        throw new IllegalArgumentException("nickname must not be blank");
      }
      if (totalScore < 0) {
        throw new IllegalArgumentException("totalScore must be >= 0");
      }
      if (correctAnswerTimeMs < 0) {
        throw new IllegalArgumentException("correctAnswerTimeMs must be >= 0");
      }
      if (correctCount < 0) {
        throw new IllegalArgumentException("correctCount must be >= 0");
      }
    }
  }

  public record RankedPlayer(
      @Nullable UUID userId,
      String nickname,
      boolean isGuest,
      int totalScore,
      long correctAnswerTimeMs,
      int correctCount,
      int rank) {

    public RankedPlayer {
      if (rank < 1) {
        throw new IllegalArgumentException("rank must be >= 1");
      }
    }
  }

  public static final Comparator<PlayerScore> SCORE_COMPARATOR =
      Comparator.comparingInt(PlayerScore::totalScore)
          .reversed()
          .thenComparingLong(PlayerScore::correctAnswerTimeMs);

  /** Xếp hạng danh sách người chơi theo BR-10. */
  public static List<RankedPlayer> rank(List<PlayerScore> players) {
    if (players == null || players.isEmpty()) {
      return List.of();
    }

    List<PlayerScore> sorted = new ArrayList<>(players);
    sorted.sort(SCORE_COMPARATOR);

    List<RankedPlayer> ranked = new ArrayList<>(sorted.size());
    int currentRank = 1;

    for (int i = 0; i < sorted.size(); i++) {
      PlayerScore current = sorted.get(i);
      if (i > 0) {
        PlayerScore prev = sorted.get(i - 1);
        if (current.totalScore() == prev.totalScore()
            && current.correctAnswerTimeMs() == prev.correctAnswerTimeMs()) {
          // Hòa điểm và hòa thời gian -> cùng thứ hạng
          ranked.add(
              new RankedPlayer(
                  current.userId(),
                  current.nickname(),
                  current.isGuest(),
                  current.totalScore(),
                  current.correctAnswerTimeMs(),
                  current.correctCount(),
                  ranked.get(i - 1).rank()));
          continue;
        } else {
          currentRank = i + 1;
        }
      }

      ranked.add(
          new RankedPlayer(
              current.userId(),
              current.nickname(),
              current.isGuest(),
              current.totalScore(),
              current.correctAnswerTimeMs(),
              current.correctCount(),
              currentRank));
    }

    return List.copyOf(ranked);
  }
}
