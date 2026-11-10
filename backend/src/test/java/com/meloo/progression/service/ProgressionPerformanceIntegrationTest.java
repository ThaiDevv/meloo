package com.meloo.progression.service;

import static org.assertj.core.api.Assertions.*;

import com.meloo.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProgressionPerformanceIntegrationTest extends IntegrationTest {
  private static final Logger log =
      LoggerFactory.getLogger(ProgressionPerformanceIntegrationTest.class);
  @Autowired JdbcTemplate jdbc;
  @Autowired ProgressionQueries queries;
  @Autowired EntityManagerFactory emf;

  @Test
  void indexedQueriesStayBelow200msP95With100000MatchResultsAndConstantIdentityQueries() {
    jdbc.update("delete from match");
    jdbc.update("delete from player_stats");
    jdbc.execute(
        """
        insert into app_user(id,kind,nickname)
        select md5('perf-user-' || n)::uuid,'REGISTERED','PerfPlayer' from generate_series(1,1000) n
        """);
    jdbc.execute(
        """
        insert into player_stats(user_id,is_guest,ranked_score,ranked_reached_at,ranked_matches,matches_played)
        select md5('perf-user-' || n)::uuid,false,n,now(),100,100 from generate_series(1,1000) n
        """);
    jdbc.execute(
        """
        insert into match(id,type,mode,ranked,status,round_count,started_at,ended_at)
        select md5('perf-match-' || n)::uuid,'QUICK_MATCH','RANKED',true,'FINISHED',5,
            now() - n * interval '1 second' - interval '5 minutes', now() - n * interval '1 second'
        from generate_series(1,20000) n
        """);
    jdbc.execute(
        """
        insert into match_player(id,match_id,user_id,owner_id,nickname,is_guest,total_score,rank,
            correct_count,correct_time_ms,counts_for_leaderboard,ended_at,rank_delta,rank_points)
        select md5('perf-player-' || m || '-' || p)::uuid, md5('perf-match-' || m)::uuid,
            md5('perf-user-' || (((m-1)*5+p-1)%1000+1))::uuid,
            md5('perf-user-' || (((m-1)*5+p-1)%1000+1))::uuid,'PerfPlayer',false,1000,p,1,100,true,
            now() - m * interval '1 second',0,100
        from generate_series(1,20000) m cross join generate_series(1,5) p
        """);
    jdbc.execute(
        """
        insert into round(id,match_id,round_no,track_id,track_title,artist_name,provider,provider_track_id)
        select id,id,1,md5('perf-track')::uuid,'Perf title','Perf artist','DEEZER','123' from match
        """);
    jdbc.execute(
        """
        insert into round_result(id,round_id,match_player_id,outcome,correct_level,base_score,round_score)
        select mp.id,mp.match_id,mp.id,'CORRECT',1,1000,1000 from match_player mp
        """);
    jdbc.execute("analyze match_player");
    jdbc.execute("analyze player_stats");
    jdbc.execute("analyze match");
    assertThat(jdbc.queryForObject("select count(*) from match_player", Integer.class))
        .isEqualTo(100000);
    UUID user = jdbc.queryForObject("select md5('perf-user-1')::uuid", UUID.class);
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    long before = statistics.getPrepareStatementCount();
    assertThat(queries.leaderboard(user, 0, 50).getTotalItems()).isEqualTo(1000);
    assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(2);
    long history = p95(() -> queries.history(user, 0, 50));
    long stats = p95(() -> queries.stats(user));
    long board = p95(() -> queries.leaderboard(user, 0, 50));
    log.info(
        "MEL-68 PostgreSQL17: 100000 match results / 1000 ranked users; p95 ms history={}, stats={}, leaderboard={}",
        history / 1_000_000d,
        stats / 1_000_000d,
        board / 1_000_000d);
    assertThat(history).isLessThan(200_000_000L);
    assertThat(stats).isLessThan(200_000_000L);
    assertThat(board).isLessThan(200_000_000L);
    String plan =
        String.join(
            "\n",
            jdbc.queryForList(
                """
        explain select * from match_player where owner_id = ? order by ended_at desc limit 50
        """,
                String.class,
                user));
    assertThat(plan).contains("idx_match_player_owner_history");
  }

  private long p95(Supplier<?> query) {
    for (int i = 0; i < 5; i++) {
      query.get();
    }
    long[] times = new long[30];
    for (int i = 0; i < times.length; i++) {
      long start = System.nanoTime();
      query.get();
      times[i] = System.nanoTime() - start;
    }
    Arrays.sort(times);
    return times[(int) Math.ceil(times.length * 0.95) - 1];
  }
}
