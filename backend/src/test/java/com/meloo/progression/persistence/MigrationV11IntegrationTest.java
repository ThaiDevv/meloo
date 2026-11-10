package com.meloo.progression.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationV11IntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Test
  void migratesV10WithExistingHistoryAndStatsWithoutLosingDataOrInventingSources() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).target("10").load().migrate();
    UUID match = UUID.randomUUID(), user = UUID.randomUUID(), player = UUID.randomUUID();
    UUID round = UUID.randomUUID(), track = UUID.randomUUID();
    jdbc.update(
        """
        insert into match(id,type,ranked,status,round_count,started_at,ended_at)
        values (?,'SOLO',false,'FINISHED',5,now(),now())
        """,
        match);
    jdbc.update(
        """
        insert into match_player(id,match_id,user_id,nickname,is_guest,total_score,rank,
        correct_count,correct_time_ms,counts_for_leaderboard,ended_at)
        values (?,?,?,'Legacy',false,1000,1,1,100,false,now())
        """,
        player,
        match,
        user);
    jdbc.update(
        """
        insert into round(id,match_id,round_no,track_id,track_title,artist_name)
        values (?,?,1,?,'Original title','Original artist')
        """,
        round,
        match,
        track);
    jdbc.update(
        """
        insert into round_result(id,round_id,match_player_id,outcome,correct_level,base_score,round_score)
        values (?,?,?,'CORRECT',1,1000,1000)
        """,
        UUID.randomUUID(),
        round,
        player);
    jdbc.update(
        "insert into player_stats(user_id,is_guest,xp,rounds_correct) values (?,false,1000,1)",
        user);
    assertThat(
            Flyway.configure().dataSource(source).target("11").load().migrate().migrationsExecuted)
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select mode from match where id=?", String.class, match))
        .isEqualTo("PRACTICE");
    assertThat(
            jdbc.queryForObject("select owner_id from match_player where id=?", UUID.class, player))
        .isEqualTo(user);
    assertThat(jdbc.queryForObject("select track_title from round where id=?", String.class, round))
        .isEqualTo("Original title");
    assertThat(jdbc.queryForMap("select provider,provider_track_id from round where id=?", round))
        .containsEntry("provider", null)
        .containsEntry("provider_track_id", null);
    assertThat(
            jdbc.queryForMap(
                "select xp,rounds_played,correct_level_total,listen_seconds_total from player_stats where user_id=?",
                user))
        .containsEntry("xp", 1000)
        .containsEntry("rounds_played", 1)
        .containsEntry("correct_level_total", 1L)
        .containsEntry("listen_seconds_total", 2L);
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where schemaname='public'", String.class))
        .contains(
            "idx_match_player_owner_history",
            "idx_player_stats_ranked",
            "idx_progression_guest_merge_user");
  }
}
