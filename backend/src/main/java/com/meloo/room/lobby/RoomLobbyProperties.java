package com.meloo.room.lobby;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Cấu hình cho sảnh chờ và vòng đời phòng nhiều người chơi (BR-11, BR-12, BR-14, BR-15). */
@ConfigurationProperties(prefix = "meloo.room.lobby")
@Validated
public record RoomLobbyProperties(
    @Min(2) int minPlayers,
    @Min(2) @Max(5) int maxPlayers,
    @Min(5) @Max(15) int defaultRoundCount,
    @NotNull Duration idleTimeout,
    @NotNull Duration disconnectTimeout) {

  public RoomLobbyProperties(
      @DefaultValue("2") int minPlayers,
      @DefaultValue("5") int maxPlayers,
      @DefaultValue("10") int defaultRoundCount,
      @DefaultValue("10m") Duration idleTimeout,
      @DefaultValue("30s") Duration disconnectTimeout) {
    if (minPlayers < 2) {
      throw new IllegalArgumentException("minPlayers must be at least 2");
    }
    if (maxPlayers < minPlayers || maxPlayers > 5) {
      throw new IllegalArgumentException("maxPlayers must be between minPlayers and 5");
    }
    if (defaultRoundCount != 5 && defaultRoundCount != 10 && defaultRoundCount != 15) {
      throw new IllegalArgumentException("defaultRoundCount must be 5, 10, or 15");
    }
    if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
      throw new IllegalArgumentException("idleTimeout must be positive");
    }
    if (disconnectTimeout == null || disconnectTimeout.isNegative() || disconnectTimeout.isZero()) {
      throw new IllegalArgumentException("disconnectTimeout must be positive");
    }
    this.minPlayers = minPlayers;
    this.maxPlayers = maxPlayers;
    this.defaultRoundCount = defaultRoundCount;
    this.idleTimeout = idleTimeout;
    this.disconnectTimeout = disconnectTimeout;
  }
}
