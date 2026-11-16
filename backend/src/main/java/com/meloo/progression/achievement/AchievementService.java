package com.meloo.progression.achievement;

import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.events.MatchEndStatus;
import com.meloo.game.events.MatchFinished;
import com.meloo.identity.events.GuestMerged;
import com.meloo.progression.events.AchievementUnlocked;
import com.meloo.progression.service.ProgressionWriter;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AchievementService {
  private final JdbcTemplate jdbc;
  private final ProgressionWriter writer;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public AchievementService(
      JdbcTemplate jdbc, ProgressionWriter writer, ApplicationEventPublisher events, Clock clock) {
    this.jdbc = jdbc;
    this.writer = writer;
    this.events = events;
    this.clock = clock;
  }

  public void matchFinished(MatchFinished event) {
    jdbc.execute("select pg_advisory_xact_lock_shared(68068)");
    // Same ordering as ProgressionWriter, including when two former identities share an owner.
    List<UUID> owners =
        event.players().stream()
            .map(p -> p.userId())
            .filter(Objects::nonNull)
            .map(this::owner)
            .distinct()
            .sorted()
            .toList();
    owners.forEach(this::lock);
    for (var player : event.players()) {
      if (player.userId() == null) continue;
      long correct =
          player.results().stream().filter(r -> r.outcome() == RoundOutcome.CORRECT).count();
      long fast =
          player.results().stream()
              .filter(
                  r ->
                      r.outcome() == RoundOutcome.CORRECT
                          && Integer.valueOf(1).equals(r.correctLevel()))
              .count();
      jdbc.update(
          """
          insert into achievement_fact(kind,source_id,participant_id,owner_id,ended_at,completed,
              won,correct,fast_correct,best_combo) values ('MATCH',?,?,?,?,?,?,?,?,?)
          on conflict do nothing
          """,
          event.matchId(),
          player.userId(),
          owner(player.userId()),
          Timestamp.from(event.endedAt()),
          event.status() == MatchEndStatus.FINISHED,
          event.status() == MatchEndStatus.FINISHED && player.rank() == 1 && !player.left(),
          correct,
          fast,
          AchievementMetrics.combo(player.results()));
    }
    owners.forEach(this::evaluate);
  }

  public void dailyFinished(DailyAttemptFinished event) {
    jdbc.execute("select pg_advisory_xact_lock_shared(68068)");
    UUID user = owner(event.userId());
    lock(user);
    jdbc.update(
        """
        insert into achievement_fact(kind,source_id,participant_id,owner_id,ended_at,
            challenge_date,completed,won) values ('DAILY',?,?,?,?,?,true,?) on conflict do nothing
        """,
        event.attemptId(),
        event.userId(),
        user,
        Timestamp.from(event.finishedAt()),
        event.challengeDate(),
        "SOLVED".equals(event.status()) && "CORRECT".equals(event.outcome()));
    evaluate(user);
  }

  public void guestMerged(GuestMerged event) {
    // The existing writer transfers unlocks with LEAST and persists the guest alias. Calling it
    // here makes either listener order safe; both use the same exclusive merge lock.
    writer.merge(event);
    jdbc.update(
        "update achievement_fact set owner_id = ? where owner_id = ?",
        event.userId(),
        event.guestId());
    evaluate(event.userId());
  }

  private UUID owner(UUID participant) {
    var rows =
        jdbc.queryForList(
            "select user_id from progression_guest_merge where guest_id = ?",
            UUID.class,
            participant);
    return rows.isEmpty() ? participant : rows.getFirst();
  }

  private void lock(UUID user) {
    jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 69))", user.toString());
  }

  private void evaluate(UUID user) {
    // Account attempts win daily collisions; this mirrors BR-21. Merged participants in the
    // same match count once, with the account participant taking precedence, as in history.
    var facts =
        jdbc.query(
            """
        select * from (
          select distinct on (kind, case when kind = 'DAILY' then challenge_date::text
              else source_id::text end) * from achievement_fact where owner_id = ?
          order by kind, case when kind = 'DAILY' then challenge_date::text else source_id::text end,
              (participant_id = owner_id) desc, ended_at, source_id, participant_id
        ) chosen order by ended_at, source_id
        """,
            (rs, row) ->
                new AchievementMetrics.Fact(
                    rs.getString("kind"),
                    rs.getBoolean("completed"),
                    rs.getBoolean("won"),
                    rs.getInt("correct"),
                    rs.getInt("fast_correct"),
                    rs.getInt("best_combo"),
                    rs.getObject("challenge_date", java.time.LocalDate.class),
                    rs.getTimestamp("ended_at").toInstant()),
            user);
    var metrics = AchievementMetrics.calculate(facts);
    jdbc.query(
        "select code, metric, threshold from achievement order by sort_order, code",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              if (metrics.getOrDefault(rs.getString("metric"), 0L) < rs.getLong("threshold"))
                return;
              String code = rs.getString("code");
              var at = clock.instant();
              if (jdbc.update(
                      """
              insert into user_achievement(user_id,achievement_code,unlocked_at) values (?,?,?)
              on conflict do nothing
              """,
                      user,
                      code,
                      Timestamp.from(at))
                  == 1) {
                events.publishEvent(new AchievementUnlocked(UUID.randomUUID(), user, code, at));
              }
            });
  }
}
