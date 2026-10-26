package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.game.domain.MatchRanking.PlayerScore;
import com.meloo.game.domain.MatchRanking.RankedPlayer;
import com.meloo.game.domain.TrackValidator.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchRankingTest {

  @Test
  @DisplayName(
      "BR-10: Xếp hạng theo tổng điểm giảm dần, phân định hòa theo thời gian trả lời đúng tăng dần")
  void testTieBreakerAndRanking() {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    UUID p3 = UUID.randomUUID();
    UUID p4 = UUID.randomUUID();

    List<PlayerScore> scores =
        List.of(
            // p1: 5000 điểm, trả lời đúng mất 12.000 ms
            new PlayerScore(p1, "Player1", false, 5000, 12_000L, 5),
            // p2: 5000 điểm, trả lời đúng mất 10.000 ms -> thắng p1 nhờ nhanh hơn
            new PlayerScore(p2, "Player2", false, 5000, 10_000L, 5),
            // p3: 6000 điểm -> hạng 1 tuyệt đối
            new PlayerScore(p3, "Player3", false, 6000, 15_000L, 6),
            // p4: 3000 điểm -> hạng 4
            new PlayerScore(p4, "Player4", false, 3000, 8_000L, 3));

    List<RankedPlayer> ranked = MatchRanking.rank(scores);

    assertThat(ranked).hasSize(4);
    // Hạng 1: p3 (6000 điểm)
    assertThat(ranked.get(0).userId()).isEqualTo(p3);
    assertThat(ranked.get(0).rank()).isEqualTo(1);

    // Hạng 2: p2 (5000 điểm, 10.000 ms)
    assertThat(ranked.get(1).userId()).isEqualTo(p2);
    assertThat(ranked.get(1).rank()).isEqualTo(2);

    // Hạng 3: p1 (5000 điểm, 12.000 ms)
    assertThat(ranked.get(2).userId()).isEqualTo(p1);
    assertThat(ranked.get(2).rank()).isEqualTo(3);

    // Hạng 4: p4 (3000 điểm)
    assertThat(ranked.get(3).userId()).isEqualTo(p4);
    assertThat(ranked.get(3).rank()).isEqualTo(4);
  }

  @Test
  @DisplayName("BR-10: Hòa cả điểm lẫn thời gian thì đồng hạng")
  void testFullTieSharedRank() {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();

    List<PlayerScore> scores =
        List.of(
            new PlayerScore(p1, "Player1", false, 5000, 10_000L, 5),
            new PlayerScore(p2, "Player2", false, 5000, 10_000L, 5));

    List<RankedPlayer> ranked = MatchRanking.rank(scores);
    assertThat(ranked.get(0).rank()).isEqualTo(1);
    assertThat(ranked.get(1).rank()).isEqualTo(1);
  }

  @Test
  @DisplayName("BR-09: Kiểm tra tính hợp lệ số vòng (5, 10, 15) và không lặp bài")
  void testTrackValidator() {
    List<TargetTrack> tenTracks = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      tenTracks.add(new TargetTrack(UUID.randomUUID(), "Bài " + i, "Ca sĩ " + i));
    }

    // 10 bài hợp lệ cho 10 vòng
    ValidationResult r1 = TrackValidator.validate(tenTracks, 10);
    assertThat(r1.valid()).isTrue();
    assertThat(r1.missingTracksCount()).isZero();

    // Số vòng không hợp lệ (ví dụ 7 vòng)
    ValidationResult rInvalidRounds = TrackValidator.validate(tenTracks, 7);
    assertThat(rInvalidRounds.valid()).isFalse();

    // Thiếu bài: chỉ có 3 bài nhưng chọn 5 vòng -> thiếu 2 bài
    List<TargetTrack> threeTracks = tenTracks.subList(0, 3);
    ValidationResult rMissing = TrackValidator.validate(threeTracks, 5);
    assertThat(rMissing.valid()).isFalse();
    assertThat(rMissing.missingTracksCount()).isEqualTo(2);
    assertThat(rMissing.message()).contains("Còn thiếu 2 bài");

    // Lặp bài trong danh sách
    UUID dupId = UUID.randomUUID();
    List<TargetTrack> duplicateTracks =
        List.of(
            new TargetTrack(dupId, "Bài A", "Ca sĩ A"),
            new TargetTrack(dupId, "Bài A", "Ca sĩ A"),
            new TargetTrack(UUID.randomUUID(), "Bài B", "Ca sĩ B"),
            new TargetTrack(UUID.randomUUID(), "Bài C", "Ca sĩ C"),
            new TargetTrack(UUID.randomUUID(), "Bài D", "Ca sĩ D"));

    assertThat(TrackValidator.hasDuplicateTracks(duplicateTracks)).isTrue();
    // 5 phần tử nhưng chỉ có 4 bài duy nhất, yêu cầu 5 vòng -> thiếu 1 bài
    ValidationResult rDup = TrackValidator.validate(duplicateTracks, 5);
    assertThat(rDup.valid()).isFalse();
    assertThat(rDup.missingTracksCount()).isEqualTo(1);
  }
}
