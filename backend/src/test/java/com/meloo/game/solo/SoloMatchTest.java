package com.meloo.game.solo;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.domain.Round;
import com.meloo.game.domain.TargetTrack;
import com.meloo.game.events.MatchEndStatus;
import com.meloo.generated.model.SoloModeDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoloMatchTest {

  @Test
  @DisplayName("Khởi tạo SoloMatch, kiểm tra trạng thái và thời gian hết hạn 30 phút")
  void testInitialStateAndExpiration() {
    UUID matchId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-29T12:00:00Z");

    TrackInfo track =
        new TrackInfo(
            UUID.randomUUID(),
            "Bài Ca Hy Vọng",
            "Ca Sĩ A",
            "DEEZER",
            "1001",
            "http://cover.jpg",
            null,
            null,
            null);

    SoloMatch match =
        new SoloMatch(
            matchId,
            userId,
            "Thái",
            false,
            SoloModeDto.RANKED,
            true,
            5,
            null,
            null,
            null,
            List.of(track),
            now);

    assertThat(match.matchId()).isEqualTo(matchId);
    assertThat(match.userId()).isEqualTo(userId);
    assertThat(match.nickname()).isEqualTo("Thái");
    assertThat(match.isGuest()).isFalse();
    assertThat(match.mode()).isEqualTo(SoloModeDto.RANKED);
    assertThat(match.isRanked()).isTrue();
    assertThat(match.roundCount()).isEqualTo(5);
    assertThat(match.status()).isEqualTo(SoloMatchStatus.IN_PROGRESS);
    assertThat(match.startedAt()).isEqualTo(now);
    assertThat(match.lastActivityAt()).isEqualTo(now);
    assertThat(match.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));

    // Dưới 30 phút chưa hết hạn
    assertThat(match.isExpired(now.plus(Duration.ofMinutes(29)))).isFalse();
    // Đúng 30 phút chưa quá
    assertThat(match.isExpired(now.plus(Duration.ofMinutes(30)))).isFalse();
    // Quá 30 phút -> hết hạn
    assertThat(match.isExpired(now.plus(Duration.ofMinutes(31)))).isTrue();

    // Touch cập nhật lại thời gian
    Instant touchTime = now.plus(Duration.ofMinutes(15));
    match.touch(touchTime);
    assertThat(match.lastActivityAt()).isEqualTo(touchTime);
    assertThat(match.getExpiresAt()).isEqualTo(touchTime.plus(Duration.ofMinutes(30)));
    assertThat(match.isExpired(now.plus(Duration.ofMinutes(31)))).isFalse();
  }

  @Test
  @DisplayName("Tính điểm và số câu đúng chỉ dựa trên các vòng đã hoàn thành")
  void testScoreCalculationAndCompletedRounds() {
    UUID matchId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-29T12:00:00Z");

    TrackInfo track1 =
        new TrackInfo(
            UUID.randomUUID(), "Bài 1", "Ca Sĩ 1", "DEEZER", "101", null, null, null, null);
    TrackInfo track2 =
        new TrackInfo(
            UUID.randomUUID(), "Bài 2", "Ca Sĩ 2", "DEEZER", "102", null, null, null, null);

    SoloMatch match =
        new SoloMatch(
            matchId,
            userId,
            "Thái",
            false,
            SoloModeDto.PRACTICE,
            false,
            5,
            null,
            null,
            null,
            List.of(track1, track2),
            now);

    // Vòng 1 đúng ở mức 2s (level 1)
    Round round1 =
        Round.standardRound(
            1, new TargetTrack(track1.id(), track1.title(), track1.artistName()), false);
    round1.start(now);
    round1.submitGuess(
        track1.title(), track1.artistName(), now.plusSeconds(1), match.comboTracker());

    SoloRoundData data1 = new SoloRoundData(1, UUID.randomUUID(), track1, round1, 0, 0);
    match.addRoundData(data1);

    assertThat(match.calculateTotalScore()).isEqualTo(1000);
    assertThat(match.calculateCorrectCount()).isEqualTo(1);
    assertThat(match.getCompletedRounds()).hasSize(1);

    // Vòng 2 đang dở (IN_PROGRESS)
    Round round2 =
        Round.standardRound(
            2, new TargetTrack(track2.id(), track2.title(), track2.artistName()), false);
    round2.start(now);
    SoloRoundData data2 = new SoloRoundData(2, UUID.randomUUID(), track2, round2, 1000, 1);
    match.addRoundData(data2);

    // Vòng 2 chưa xong không được tính điểm và không nằm trong completed rounds
    assertThat(match.calculateTotalScore()).isEqualTo(1000);
    assertThat(match.calculateCorrectCount()).isEqualTo(1);
    assertThat(match.getCompletedRounds()).hasSize(1);
    assertThat(match.getCompletedRounds().get(0).roundNo()).isEqualTo(1);
  }

  @Test
  @DisplayName("Bỏ dở (abandon) và quá hạn (expire) chuyển sang ABANDONED và bỏ cờ xếp hạng")
  void testAbandonAndExpire() {
    UUID matchId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-29T12:00:00Z");

    SoloMatch match =
        new SoloMatch(
            matchId,
            userId,
            "Thái",
            false,
            SoloModeDto.RANKED,
            true,
            5,
            null,
            null,
            null,
            List.of(),
            now);

    assertThat(match.isRanked()).isTrue();

    Instant abandonTime = now.plusSeconds(300);
    match.abandon(abandonTime);

    assertThat(match.status()).isEqualTo(SoloMatchStatus.ABANDONED);
    assertThat(match.toMatchEndStatus()).isEqualTo(MatchEndStatus.ABANDONED);
    assertThat(match.isRanked()).isFalse();
    assertThat(match.endedAt()).isEqualTo(abandonTime);

    // Expire
    SoloMatch match2 =
        new SoloMatch(
            UUID.randomUUID(),
            userId,
            "Thái",
            false,
            SoloModeDto.RANKED,
            true,
            5,
            null,
            null,
            null,
            List.of(),
            now);
    Instant expireTime = now.plus(Duration.ofMinutes(35));
    match2.expire(expireTime);
    assertThat(match2.status()).isEqualTo(SoloMatchStatus.ABANDONED);
    assertThat(match2.toMatchEndStatus()).isEqualTo(MatchEndStatus.ABANDONED);
    assertThat(match2.isRanked()).isFalse();
    assertThat(match2.endedAt()).isEqualTo(expireTime);
  }
}
