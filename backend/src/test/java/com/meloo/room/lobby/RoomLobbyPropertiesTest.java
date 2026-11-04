package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomLobbyPropertiesTest {

  @Test
  @DisplayName("Khởi tạo hợp lệ với các giá trị mặc định chuẩn quy tắc")
  void validDefaultProperties() {
    RoomLobbyProperties properties =
        new RoomLobbyProperties(2, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(30));
    assertThat(properties.minPlayers()).isEqualTo(2);
    assertThat(properties.maxPlayers()).isEqualTo(5);
    assertThat(properties.defaultRoundCount()).isEqualTo(10);
    assertThat(properties.idleTimeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(properties.disconnectTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("Từ chối minPlayers < 2")
  void rejectsInvalidMinPlayers() {
    assertThatThrownBy(
            () -> new RoomLobbyProperties(1, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Từ chối maxPlayers > 5 hoặc < minPlayers")
  void rejectsInvalidMaxPlayers() {
    assertThatThrownBy(
            () -> new RoomLobbyProperties(3, 2, 10, Duration.ofMinutes(10), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RoomLobbyProperties(2, 6, 10, Duration.ofMinutes(10), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Từ chối roundCount khác 5, 10, 15 (BR-09)")
  void rejectsInvalidRoundCount() {
    assertThatThrownBy(
            () -> new RoomLobbyProperties(2, 5, 7, Duration.ofMinutes(10), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Từ chối timeouts null hoặc <= 0")
  void rejectsInvalidTimeouts() {
    assertThatThrownBy(() -> new RoomLobbyProperties(2, 5, 10, null, Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RoomLobbyProperties(2, 5, 10, Duration.ofMinutes(10), Duration.ofSeconds(0)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
