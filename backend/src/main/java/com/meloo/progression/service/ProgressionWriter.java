package com.meloo.progression.service;

import com.meloo.game.events.MatchEndStatus;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchType;
import com.meloo.game.events.PlayerSummary;
import com.meloo.game.events.RoundPlayerResult;
import com.meloo.identity.api.DeletedAccounts;
import com.meloo.identity.events.GuestMerged;
import com.meloo.progression.domain.RankPoints;
import com.meloo.progression.events.RankPointsApplied;
import com.meloo.room.api.RoomRankPublisher.Change;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProgressionWriter {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final DeletedAccounts deletedAccounts;

  public ProgressionWriter(
      JdbcTemplate jdbc,
      ApplicationEventPublisher events,
      Clock clock,
      DeletedAccounts deletedAccounts) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
    this.events = events;
    this.clock = clock;
    this.deletedAccounts = deletedAccounts;
  }

  public void record(MatchFinished event) {
    // Match writes can run concurrently; guest ownership changes take the exclusive lock.
    jdbc.execute("select pg_advisory_xact_lock_shared(68068)");
    jdbc.queryForList(
        "select pg_advisory_xact_lock(hashtextextended(?, 68))", event.matchId().toString());
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from match where id = ?)", Boolean.class, event.matchId()))) {
      return;
    }
    List<UUID> participantIds =
        event.players().stream()
            .map(PlayerSummary::userId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, UUID> owners = new HashMap<>();
    if (!participantIds.isEmpty()) {
      named.query(
          "select guest_id, user_id from progression_guest_merge where guest_id in (:ids)",
          Map.of("ids", participantIds),
          rs -> {
            owners.put(rs.getObject("guest_id", UUID.class), rs.getObject("user_id", UUID.class));
          });
    }
    participantIds.stream()
        .map(id -> owners.getOrDefault(id, id))
        .distinct()
        .sorted()
        .forEach(
            id ->
                jdbc.queryForList(
                    "select pg_advisory_xact_lock(hashtextextended(?, 69))", id.toString()));

    var checkedIds = new java.util.HashSet<UUID>(participantIds);
    checkedIds.addAll(owners.values());
    var deleted = deletedAccounts.findDeleted(checkedIds);

    boolean ranked =
        event.ranked()
            && event.type() == MatchType.QUICK_MATCH
            && event.status() == MatchEndStatus.FINISHED;
    jdbc.update(
        """
        insert into match(id,type,mode,ranked,status,round_count,playlist_id,filter,started_at,ended_at)
        values (?,?,?,?,?,?,?,cast(? as jsonb),?,?)
        """,
        event.matchId(),
        event.type().name(),
        event.mode(),
        ranked,
        event.status().name(),
        event.roundCount(),
        event.playlistId(),
        event.filter(),
        Timestamp.from(event.startedAt()),
        Timestamp.from(event.endedAt()));

    Map<Integer, UUID> rounds = new HashMap<>();
    List<Object[]> roundRows = new ArrayList<>();
    for (var round : event.rounds()) {
      UUID id = UUID.randomUUID();
      rounds.put(round.roundNo(), id);
      roundRows.add(
          new Object[] {
            id,
            event.matchId(),
            round.roundNo(),
            round.trackId(),
            round.trackTitle(),
            round.artistName(),
            round.provider(),
            round.providerTrackId()
          });
    }
    jdbc.batchUpdate(
        """
        insert into round(id,match_id,round_no,track_id,track_title,artist_name,provider,provider_track_id)
        values (?,?,?,?,?,?,?,?)
        """,
        roundRows);

    List<Change> changes = new ArrayList<>();
    List<Object[]> resultRows = new ArrayList<>();
    for (PlayerSummary player : event.players()) {
      UUID owner =
          player.userId() == null ? null : owners.getOrDefault(player.userId(), player.userId());
      boolean erased =
          (player.userId() != null && deleted.contains(player.userId()))
              || (owner != null && deleted.contains(owner));
      if (erased) owner = null;
      boolean guest = player.isGuest() && !owners.containsKey(player.userId());
      boolean eligible = ranked && !player.isGuest() && owner != null;
      Integer delta = null;
      Long points = null;
      if (owner != null) {
        jdbc.update(
            "insert into player_stats(user_id,is_guest) values (?,?) on conflict do nothing",
            owner,
            guest);
        if (eligible) {
          long previous =
              jdbc.queryForObject(
                  "select ranked_score from player_stats where user_id = ?", Long.class, owner);
          delta = RankPoints.delta(event.players().size(), player.rank(), player.left(), previous);
          points = previous + delta;
          // A zero delta does not change when the player first attained the current balance.
          jdbc.update(
              """
              update player_stats set ranked_score = ?, ranked_matches = ranked_matches + 1,
              ranked_reached_at = case when ranked_reached_at is null or ? <> 0 then ?
                  else ranked_reached_at end where user_id = ?
              """,
              points,
              delta,
              Timestamp.from(clock.instant()),
              owner);
          changes.add(new Change(owner, delta, Math.toIntExact(points)));
        }
        Totals totals = totals(player.results());
        jdbc.update(
            """
            update player_stats set is_guest = ?, xp = xp + ?, matches_played = matches_played + 1,
            matches_won = matches_won + ?, rounds_played = rounds_played + ?,
            rounds_correct = rounds_correct + ?, best_combo = greatest(best_combo, ?),
            correct_level_total = correct_level_total + ?,
            listen_seconds_total = listen_seconds_total + ? where user_id = ?
            """,
            guest,
            player.totalScore(),
            event.status() == MatchEndStatus.FINISHED && player.rank() == 1 && !player.left()
                ? 1
                : 0,
            player.results().size(),
            totals.correct(),
            totals.bestCombo(),
            totals.levels(),
            totals.seconds(),
            owner);
      }
      UUID playerId = UUID.randomUUID();
      jdbc.update(
          """
          insert into match_player(id,match_id,user_id,owner_id,nickname,is_guest,total_score,rank,
          correct_count,correct_time_ms,counts_for_leaderboard,ended_at,rank_delta,rank_points)
          values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,
          playerId,
          event.matchId(),
          erased ? null : player.userId(),
          owner,
          erased ? "Deleted user" : player.nickname(),
          player.isGuest(),
          player.totalScore(),
          player.left() && ranked ? event.players().size() : player.rank(),
          player.correctCount(),
          player.correctTimeMs(),
          eligible,
          Timestamp.from(event.endedAt()),
          delta,
          points);
      for (var result : player.results()) {
        UUID roundId = rounds.get(result.roundNo());
        if (roundId == null) {
          throw new IllegalArgumentException("Player result has no matching round snapshot");
        }
        resultRows.add(
            new Object[] {
              UUID.randomUUID(),
              roundId,
              playerId,
              result.outcome().name(),
              result.correctLevel(),
              result.baseScore(),
              result.comboPercent(),
              result.speedBonus(),
              result.roundScore(),
              result.wrongCount(),
              result.answerTimeMs()
            });
      }
    }
    jdbc.batchUpdate(
        """
        insert into round_result(id,round_id,match_player_id,outcome,correct_level,base_score,
        combo_percent,speed_bonus,round_score,wrong_count,answer_time_ms) values (?,?,?,?,?,?,?,?,?,?,?)
        """,
        resultRows);
    if (ranked && event.roomId() != null && !changes.isEmpty()) {
      events.publishEvent(
          new RankPointsApplied(event.roomId(), event.matchId(), changes, clock.instant()));
    }
  }

  public void merge(GuestMerged event) {
    jdbc.execute("select pg_advisory_xact_lock(68068)");
    if (!deletedAccounts.findDeleted(List.of(event.guestId(), event.userId())).isEmpty()) return;
    int inserted =
        jdbc.update(
            """
        insert into progression_guest_merge(guest_id,user_id) values (?,?) on conflict do nothing
        """,
            event.guestId(),
            event.userId());
    if (inserted == 0) {
      return;
    }
    jdbc.update(
        "update match_player set owner_id = ? where owner_id = ?", event.userId(), event.guestId());
    // Only non-ranked metrics transfer. Guests cannot earn rank (BR-20/21/24).
    jdbc.update(
        """
        insert into player_stats(user_id,is_guest,xp,matches_played,matches_won,rounds_correct,
            best_combo,rounds_played,correct_level_total,listen_seconds_total)
        select ?,false,xp,matches_played,matches_won,rounds_correct,best_combo,rounds_played,
            correct_level_total,listen_seconds_total from player_stats where user_id = ?
        on conflict(user_id) do update set
            xp = player_stats.xp + excluded.xp,
            matches_played = player_stats.matches_played + excluded.matches_played,
            matches_won = player_stats.matches_won + excluded.matches_won,
            rounds_correct = player_stats.rounds_correct + excluded.rounds_correct,
            best_combo = greatest(player_stats.best_combo, excluded.best_combo),
            rounds_played = player_stats.rounds_played + excluded.rounds_played,
            correct_level_total = player_stats.correct_level_total + excluded.correct_level_total,
            listen_seconds_total = player_stats.listen_seconds_total + excluded.listen_seconds_total,
            is_guest = false
        """,
        event.userId(),
        event.guestId());
    jdbc.update("delete from player_stats where user_id = ?", event.guestId());
    jdbc.update(
        """
        insert into user_achievement(user_id,achievement_code,unlocked_at)
        select ?,achievement_code,unlocked_at from user_achievement where user_id = ?
        on conflict(user_id,achievement_code) do update set
            unlocked_at = least(user_achievement.unlocked_at, excluded.unlocked_at)
        """,
        event.userId(),
        event.guestId());
    jdbc.update("delete from user_achievement where user_id = ?", event.guestId());
  }

  private static Totals totals(List<RoundPlayerResult> results) {
    int correct = 0, levels = 0, seconds = 0, combo = 0, best = 0, previousRound = 0;
    for (var result :
        results.stream().sorted(Comparator.comparingInt(RoundPlayerResult::roundNo)).toList()) {
      boolean solved =
          result.outcome() == com.meloo.game.domain.RoundOutcome.CORRECT
              && result.correctLevel() != null;
      if (solved) {
        correct++;
        levels += result.correctLevel();
        seconds += listenSeconds(result.correctLevel());
      }
      combo =
          solved && result.correctLevel() <= 2
              ? (result.roundNo() == previousRound + 1 ? combo + 1 : 1)
              : 0;
      best = Math.max(best, combo);
      previousRound = result.roundNo();
    }
    return new Totals(correct, levels, seconds, best);
  }

  public static int listenSeconds(int level) {
    return switch (level) {
      case 1 -> 2;
      case 2 -> 5;
      case 3 -> 10;
      case 4 -> 20;
      case 5 -> 30;
      default -> throw new IllegalArgumentException("Invalid listening level");
    };
  }

  private record Totals(int correct, int levels, int seconds, int bestCombo) {}
}
