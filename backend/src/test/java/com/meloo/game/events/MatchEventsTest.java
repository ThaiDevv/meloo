package com.meloo.game.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.game.domain.RoundOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchEventsTest {

  @Test
  @DisplayName("AC-8: Khởi tạo và kiểm tra payload sự kiện MatchStarted")
  void testMatchStartedPayload() {
    UUID eventId = UUID.randomUUID();
    UUID matchId = UUID.randomUUID();
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    Instant now = Instant.now();

    MatchStarted event =
        new MatchStarted(eventId, matchId, MatchType.QUICK_MATCH, true, 10, now, List.of(p1, p2));

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.matchId()).isEqualTo(matchId);
    assertThat(event.type()).isEqualTo(MatchType.QUICK_MATCH);
    assertThat(event.ranked()).isTrue();
    assertThat(event.roundCount()).isEqualTo(10);
    assertThat(event.startedAt()).isEqualTo(now);
    assertThat(event.playerIds()).containsExactly(p1, p2);

    // Validation
    assertThatThrownBy(
            () -> new MatchStarted(null, matchId, MatchType.SOLO, false, 10, now, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("AC-8: Khởi tạo và kiểm tra payload sự kiện MatchFinished đúng mục 2 tài liệu 04")
  void testMatchFinishedPayload() {
    UUID eventId = UUID.randomUUID();
    UUID matchId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID trackId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant start = Instant.parse("2026-10-26T12:00:00Z");
    Instant end = Instant.parse("2026-10-26T12:10:00Z");

    RoundSummary roundSummary =
        new RoundSummary(1, trackId, "Chúng Ta Của Tương Lai", "Sơn Tùng M-TP");

    RoundPlayerResult roundResult =
        new RoundPlayerResult(1, RoundOutcome.CORRECT, 2, 800, 10, 133, 1013, 1, 15_000L);

    PlayerSummary playerSummary =
        new PlayerSummary(userId, "ProPlayer", false, 1013, 1, 1, 15_000L, List.of(roundResult));

    MatchFinished event =
        new MatchFinished(
            eventId,
            matchId,
            MatchType.SOLO,
            true,
            MatchEndStatus.FINISHED,
            10,
            playlistId,
            "vpop",
            start,
            end,
            List.of(roundSummary),
            List.of(playerSummary));

    assertThat(event.eventId()).isEqualTo(eventId);
    assertThat(event.matchId()).isEqualTo(matchId);
    assertThat(event.type()).isEqualTo(MatchType.SOLO);
    assertThat(event.ranked()).isTrue();
    assertThat(event.status()).isEqualTo(MatchEndStatus.FINISHED);
    assertThat(event.roundCount()).isEqualTo(10);
    assertThat(event.playlistId()).isEqualTo(playlistId);
    assertThat(event.filter()).isEqualTo("vpop");
    assertThat(event.startedAt()).isEqualTo(start);
    assertThat(event.endedAt()).isEqualTo(end);

    assertThat(event.rounds()).hasSize(1);
    assertThat(event.rounds().get(0).trackTitle()).isEqualTo("Chúng Ta Của Tương Lai");

    assertThat(event.players()).hasSize(1);
    PlayerSummary player = event.players().get(0);
    assertThat(player.nickname()).isEqualTo("ProPlayer");
    assertThat(player.totalScore()).isEqualTo(1013);
    assertThat(player.results()).hasSize(1);
    assertThat(player.results().get(0).roundScore()).isEqualTo(1013);
    assertThat(player.results().get(0).outcome()).isEqualTo(RoundOutcome.CORRECT);

    // Danh sách không thể bị mutate từ bên ngoài
    assertThatThrownBy(() -> event.rounds().add(roundSummary))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> event.players().add(playerSummary))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
