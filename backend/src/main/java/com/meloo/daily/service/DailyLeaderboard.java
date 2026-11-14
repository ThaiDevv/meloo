package com.meloo.daily.service;

import com.meloo.daily.persistence.DailyAttemptRepository;
import com.meloo.generated.model.DailyCompletionStatusDto;
import com.meloo.generated.model.DailyLeaderboardEntryDto;
import com.meloo.generated.model.DailyLeaderboardPageDto;
import com.meloo.identity.api.UserAccount;
import com.meloo.identity.api.UserAccounts;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyLeaderboard {
  private static final String RANKED =
      """
      WITH ranked AS (
        SELECT *, row_number() OVER (
          ORDER BY CASE WHEN status = 'SOLVED' THEN 0 ELSE 1 END,
                   correct_level ASC NULLS LAST, elapsed_ms, finished_at, id) AS position
        FROM daily_attempt
        WHERE challenge_date = :date AND is_guest = false AND status <> 'IN_PROGRESS'
          AND user_id = ANY(CAST(:eligible AS uuid[]))
      )
      SELECT * FROM ranked
      """;
  private final DailyAttemptRepository attempts;
  private final UserAccounts users;
  private final NamedParameterJdbcTemplate jdbc;

  public DailyLeaderboard(
      DailyAttemptRepository attempts, UserAccounts users, NamedParameterJdbcTemplate jdbc) {
    this.attempts = attempts;
    this.users = users;
    this.jdbc = jdbc;
  }

  public DailyLeaderboardPageDto page(
      UUID caller, boolean guest, LocalDate date, int page, int size) {
    Map<UUID, UserAccount> profiles = users.findByIdIn(attempts.findLeaderboardUsers(date));
    var result =
        new DailyLeaderboardPageDto()
            .challengeDate(date)
            .page(page)
            .size(size)
            .totalItems((long) profiles.size())
            .totalPages((profiles.size() + size - 1) / size)
            .items(List.of());
    if (profiles.isEmpty()) {
      return result;
    }
    var params =
        new MapSqlParameterSource("date", date)
            .addValue(
                "eligible",
                profiles.keySet().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(",", "{", "}")))
            .addValue("limit", size)
            .addValue("offset", (long) page * size)
            .addValue("caller", caller);
    result.setItems(
        jdbc.query(
            RANKED + " ORDER BY position LIMIT :limit OFFSET :offset",
            params,
            (rs, row) -> entry(rs, profiles)));
    if (!guest && profiles.containsKey(caller)) {
      var own =
          jdbc.query(RANKED + " WHERE user_id = :caller", params, (rs, row) -> entry(rs, profiles));
      result.setMyEntry(own.isEmpty() ? null : own.getFirst());
    }
    return result;
  }

  public @Nullable Integer rank(UUID caller, boolean guest, LocalDate date) {
    if (guest) {
      return null;
    }
    var entry = page(caller, false, date, 0, 1).getMyEntry();
    return entry == null ? null : entry.getRank();
  }

  private DailyLeaderboardEntryDto entry(java.sql.ResultSet rs, Map<UUID, UserAccount> profiles)
      throws java.sql.SQLException {
    UUID user = rs.getObject("user_id", UUID.class);
    Integer level = rs.getObject("correct_level", Integer.class);
    int score =
        level == null
            ? 0
            : com.meloo.game.domain.GameRules.DEFAULT_LEVELS.get(level - 1).baseScore();
    return new DailyLeaderboardEntryDto()
        .rank(rs.getInt("position"))
        .userId(user)
        .nickname(profiles.get(user).nickname())
        .avatarId(
            profiles.get(user).avatarId() == null
                ? null
                : DailyLeaderboardEntryDto.AvatarIdEnum.fromValue(profiles.get(user).avatarId()))
        .status(DailyCompletionStatusDto.fromValue(rs.getString("status")))
        .correctLevel(level)
        .listenSeconds(level == null ? null : DailyMapper.seconds(level))
        .score(DailyLeaderboardEntryDto.ScoreEnum.fromValue(score))
        .elapsedMs(rs.getInt("elapsed_ms"))
        .completedAt(rs.getObject("finished_at", OffsetDateTime.class));
  }
}
