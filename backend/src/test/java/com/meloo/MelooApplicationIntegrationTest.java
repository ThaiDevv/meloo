package com.meloo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventPublicationRegistry;

class MelooApplicationIntegrationTest extends IntegrationTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void contextStartsWithJpaEventPublicationRegistry() {
    assertThat(applicationContext.getBean(EventPublicationRegistry.class)).isNotNull();
  }

  @Test
  void flywayCreatesBaselineEventPublicationTable() {
    Integer tableCount =
        jdbcTemplate.queryForObject(
            """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public' and table_name = 'event_publication'
                        """,
            Integer.class);

    assertThat(tableCount).isEqualTo(1);
  }
}
