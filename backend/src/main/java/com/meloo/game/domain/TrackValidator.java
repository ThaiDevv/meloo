package com.meloo.game.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BR-09: Kiểm tra tính hợp lệ của danh sách bài hát cho trận đấu. - Trận 5, 10, hoặc 15 vòng. -
 * Không lặp bài hát (trackId duy nhất). - Nếu không đủ bài hợp lệ cho số vòng thì không cho bắt đầu
 * và báo rõ còn thiếu bao nhiêu bài.
 */
public final class TrackValidator {

  public static final Set<Integer> ALLOWED_ROUND_COUNTS = Set.of(5, 10, 15);
  public static final int DEFAULT_ROUND_COUNT = 10;

  private TrackValidator() {}

  public record ValidationResult(boolean valid, int missingTracksCount, String message) {
    public static ValidationResult ok() {
      return new ValidationResult(true, 0, "OK");
    }

    public static ValidationResult invalid(int missingTracksCount, String message) {
      return new ValidationResult(false, missingTracksCount, message);
    }
  }

  /** Kiểm tra danh sách bài hát cho số vòng yêu cầu. */
  public static ValidationResult validate(List<TargetTrack> tracks, int roundCount) {
    if (!ALLOWED_ROUND_COUNTS.contains(roundCount)) {
      return ValidationResult.invalid(
          0, "Số vòng không hợp lệ: " + roundCount + ". Chỉ chấp nhận 5, 10 hoặc 15 vòng.");
    }

    if (tracks == null || tracks.isEmpty()) {
      return ValidationResult.invalid(
          roundCount, "Không có bài hát nào trong danh sách. Còn thiếu " + roundCount + " bài.");
    }

    Set<UUID> uniqueIds = new HashSet<>();
    for (TargetTrack track : tracks) {
      if (track == null) {
        continue;
      }
      uniqueIds.add(track.trackId());
    }

    if (uniqueIds.size() < roundCount) {
      int missing = roundCount - uniqueIds.size();
      return ValidationResult.invalid(
          missing,
          "Bộ nhạc không đủ bài hợp lệ (chỉ có "
              + uniqueIds.size()
              + " bài duy nhất cho "
              + roundCount
              + " vòng). Còn thiếu "
              + missing
              + " bài.");
    }

    return ValidationResult.ok();
  }

  /** Kiểm tra danh sách tracks có bài trùng lặp không. */
  public static boolean hasDuplicateTracks(List<TargetTrack> tracks) {
    if (tracks == null || tracks.isEmpty()) {
      return false;
    }
    Set<UUID> seen = new HashSet<>();
    for (TargetTrack track : tracks) {
      if (track != null && !seen.add(track.trackId())) {
        return true;
      }
    }
    return false;
  }
}
