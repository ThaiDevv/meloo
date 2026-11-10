package com.meloo.progression.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RankPointsTest {
  @ParameterizedTest
  @CsvSource({
    "2,1,20", "2,2,-15", "3,1,25", "3,2,0", "3,3,-15", "4,1,30", "4,2,10", "4,3,-10", "4,4,-20",
    "5,1,30", "5,2,15", "5,3,0", "5,4,-10", "5,5,-20"
  })
  void followsEveryCellOfTheApprovedTable(int players, int rank, int expected) {
    assertThat(RankPoints.delta(players, rank, false, 100)).isEqualTo(expected);
  }

  @Test
  void floorAndDeparturesUseActualDelta() {
    assertThat(RankPoints.delta(5, 5, false, 5)).isEqualTo(-5);
    assertThat(RankPoints.delta(5, 5, false, 0)).isZero();
    assertThat(RankPoints.delta(5, 1, true, 100)).isEqualTo(-20);
    assertThat(RankPoints.delta(2, 1, true, 5)).isEqualTo(-5);
  }

  @ParameterizedTest
  @CsvSource({"1,1,0", "6,1,0", "2,0,0", "2,3,0", "2,1,-1"})
  void rejectsInvalidInputs(int players, int rank, long current) {
    assertThatThrownBy(() -> RankPoints.delta(players, rank, false, current))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
