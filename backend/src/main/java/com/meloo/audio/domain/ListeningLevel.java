package com.meloo.audio.domain;

/**
 * Audio listening levels (BR-01, ADR-008). Server-side byte limitation based on CBR 128 kbps (~16
 * KB/s of Deezer preview). Level 1: 2s -> 32 KB (32,768 bytes) Level 2: 5s -> 80 KB (81,920 bytes)
 * Level 3: 10s -> 160 KB (163,840 bytes) Level 4: 20s -> 320 KB (327,680 bytes) Level 5: 30s -> 480
 * KB (491,520 bytes)
 */
public enum ListeningLevel {
  LEVEL_1(1, 2, 32L * 1024L),
  LEVEL_2(2, 5, 80L * 1024L),
  LEVEL_3(3, 10, 160L * 1024L),
  LEVEL_4(4, 20, 320L * 1024L),
  LEVEL_5(5, 30, 480L * 1024L);

  private final int level;
  private final int durationSeconds;
  private final long maxBytes;

  ListeningLevel(int level, int durationSeconds, long maxBytes) {
    this.level = level;
    this.durationSeconds = durationSeconds;
    this.maxBytes = maxBytes;
  }

  public int getLevel() {
    return level;
  }

  public int getDurationSeconds() {
    return durationSeconds;
  }

  public long getMaxBytes() {
    return maxBytes;
  }

  public static ListeningLevel fromLevel(int level) {
    for (ListeningLevel ll : values()) {
      if (ll.level == level) {
        return ll;
      }
    }
    throw new IllegalArgumentException(
        "Invalid listening level: " + level + ". Must be between 1 and 5.");
  }
}
