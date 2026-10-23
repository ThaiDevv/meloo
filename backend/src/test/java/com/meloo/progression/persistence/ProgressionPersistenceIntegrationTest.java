package com.meloo.progression.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProgressionPersistenceIntegrationTest extends IntegrationTest {

  @Autowired private MatchRepository matchRepository;
  @Autowired private MatchPlayerRepository matchPlayerRepository;
  @Autowired private RoundRepository roundRepository;
  @Autowired private RoundResultRepository roundResultRepository;

  @Test
  @DisplayName("MatchEntity round-trip with jsonb filter and RoundResult with defaults")
  void matchAndRoundResultRoundTrip() {
    UUID matchId = UUID.randomUUID();
    String filterJson = "{\"genre\": \"vpop\", \"era\": \"2020s\"}";
    MatchEntity match =
        MatchEntity.builder()
            .id(matchId)
            .type(MatchType.SOLO)
            .ranked(true)
            .status(MatchStatus.FINISHED)
            .roundCount((short) 10)
            .filter(filterJson)
            .startedAt(Instant.now().minusSeconds(300))
            .endedAt(Instant.now())
            .build();

    MatchEntity savedMatch = matchRepository.saveAndFlush(match);
    assertThat(savedMatch.getCreatedAt()).isNotNull();

    MatchEntity loadedMatch = matchRepository.findById(matchId).orElseThrow();
    assertThat(loadedMatch.getFilter()).contains("vpop");

    UUID playerId = UUID.randomUUID();
    MatchPlayer player =
        MatchPlayer.builder()
            .id(playerId)
            .matchId(matchId)
            .nickname("player1")
            .guest(false)
            .totalScore(1500)
            .rank((short) 1)
            .correctCount((short) 8)
            .correctTimeMs(12000L)
            .countsForLeaderboard(true)
            .endedAt(Instant.now())
            .build();
    matchPlayerRepository.saveAndFlush(player);

    UUID roundId = UUID.randomUUID();
    Round round =
        Round.builder()
            .id(roundId)
            .matchId(matchId)
            .roundNo((short) 1)
            .trackId(UUID.randomUUID())
            .trackTitle("Song Title")
            .artistName("Artist")
            .build();
    roundRepository.saveAndFlush(round);

    UUID resultId = UUID.randomUUID();
    RoundResult result =
        RoundResult.builder()
            .id(resultId)
            .roundId(roundId)
            .matchPlayerId(playerId)
            .outcome(RoundOutcome.CORRECT)
            .correctLevel((short) 1)
            .baseScore(1000)
            .roundScore(1000)
            .build();
    RoundResult savedResult = roundResultRepository.saveAndFlush(result);
    assertThat(savedResult.getComboPercent()).isZero();
    assertThat(savedResult.getSpeedBonus()).isZero();
    assertThat(savedResult.getWrongCount()).isZero();
    assertThat(savedResult.getCreatedAt()).isNotNull();

    RoundResult loadedResult = roundResultRepository.findById(resultId).orElseThrow();
    assertThat(loadedResult.getOutcome()).isEqualTo(RoundOutcome.CORRECT);
    assertThat(loadedResult.getBaseScore()).isEqualTo(1000);
  }
}
