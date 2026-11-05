package com.meloo.game.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Sự kiện MatchFinished phát khi trận đấu kết thúc hoặc bị hủy quá 30 phút. Module sở hữu: game.
 * Định nghĩa ở BE-GAME-01; chơi đơn phát ở BE-SOLO-01, phòng phát ở BE-ROOM-02. Ai nghe:
 * progression (ghi lịch sử, thống kê, bảng), admin. Payload chuẩn theo mục 2 tài liệu
 * docs/architecture/data/04-su-kien-va-api-module.md.
 */
public record MatchFinished(
    UUID eventId,
    UUID matchId,
    MatchType type,
    String mode,
    boolean ranked,
    MatchEndStatus status,
    int roundCount,
    @Nullable UUID playlistId,
    @Nullable String filter,
    Instant startedAt,
    Instant endedAt,
    List<RoundSummary> rounds,
    List<PlayerSummary> players,
    @Nullable UUID roomId) {

  /** Compatibility for solo publishers, which have no room. */
  public MatchFinished(
      UUID eventId,
      UUID matchId,
      MatchType type,
      String mode,
      boolean ranked,
      MatchEndStatus status,
      int roundCount,
      @Nullable UUID playlistId,
      @Nullable String filter,
      Instant startedAt,
      Instant endedAt,
      List<RoundSummary> rounds,
      List<PlayerSummary> players) {
    this(
        eventId,
        matchId,
        type,
        mode,
        ranked,
        status,
        roundCount,
        playlistId,
        filter,
        startedAt,
        endedAt,
        rounds,
        players,
        null);
  }

  public MatchFinished {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(startedAt, "startedAt must not be null");
    Objects.requireNonNull(endedAt, "endedAt must not be null");
    if (roundCount < 1) {
      throw new IllegalArgumentException("roundCount must be >= 1");
    }
    rounds = rounds == null ? List.of() : List.copyOf(rounds);
    players = players == null ? List.of() : List.copyOf(players);
  }
}
