package com.meloo.game.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MatchFinishedCompatibilityTest {
  @Test
  void canReplayPreviouslyStoredSoloPublicationWithoutNewRoomAndDepartureFields() {
    PlayerSummary player =
        new PlayerSummary(UUID.randomUUID(), "Player", false, 0, 1, 0, 0, List.of());
    Instant now = Instant.parse("2026-11-05T00:00:00Z");
    MatchFinished event =
        new MatchFinished(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MatchType.SOLO,
            "PRACTICE",
            false,
            MatchEndStatus.FINISHED,
            5,
            null,
            null,
            now,
            now,
            List.of(),
            List.of(player));
    var mapper = JsonMapper.builder().build();
    String oldJson =
        mapper
            .writeValueAsString(event)
            .replace(",\"roomId\":null", "")
            .replace(",\"left\":false", "");
    MatchFinished restored = mapper.readValue(oldJson, MatchFinished.class);
    assertThat(restored.roomId()).isNull();
    assertThat(restored.players().getFirst().left()).isFalse();
    assertThat(restored.matchId()).isEqualTo(event.matchId());
  }
}
