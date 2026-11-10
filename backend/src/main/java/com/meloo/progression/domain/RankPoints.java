package com.meloo.progression.domain;

/** BR-24: fixed placement table, departures lose last-place points, and zero is the floor. */
public final class RankPoints {
  private static final int[][] DELTAS = {
    {20, -15}, {25, 0, -15}, {30, 10, -10, -20}, {30, 15, 0, -10, -20}
  };

  private RankPoints() {}

  public static int delta(int players, int rank, boolean left, long current) {
    if (players < 2 || players > 5 || rank < 1 || rank > players || current < 0) {
      throw new IllegalArgumentException("Invalid ranked match placement or balance");
    }
    int nominal = DELTAS[players - 2][left ? players - 1 : rank - 1];
    return (int) Math.max(-current, nominal);
  }
}
