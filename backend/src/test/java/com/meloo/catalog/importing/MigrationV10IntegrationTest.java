package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MigrationV10IntegrationTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ResourceLoader resourceLoader;

  @Test
  @DisplayName(
      "Migration V10 runs on pre-existing data: legacy tracks have null cover_url, new tracks store cover_url")
  void migrationV10_runsOnExistingData() throws Exception {
    UUID artistId = UUID.randomUUID();
    jdbc.update(
        """
        insert into artist (id, name, name_normalized, provider, provider_artist_id, created_at)
        values (?, 'Pre-V10 Artist', 'pre-v10 artist', 'DEEZER', 'art-mig-pre-v10', now())
        """,
        artistId);

    // 1. Simulate state BEFORE Migration V10 by dropping the columns & index
    jdbc.execute("drop index if exists idx_track_health_check");
    jdbc.execute("alter table track drop column if exists last_preview_checked_at");
    jdbc.execute("alter table track drop column if exists cover_url");

    // 2. Insert pre-existing tracks into the pre-V10 schema
    UUID preExistingTrackId = UUID.randomUUID();
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, created_at)
        values (?, 'DEEZER', 'dz-mig-pre-v10', 'Pre-existing Track', 'pre-existing track', ?, now())
        """,
        preExistingTrackId,
        artistId);

    // 3. Apply the ACTUAL Migration V10 SQL script file directly from classpath
    Resource v10Script =
        resourceLoader.getResource("classpath:db/migration/V10__catalog_track_cover_url.sql");
    assertThat(v10Script.exists()).isTrue();
    try (Connection conn = jdbc.getDataSource().getConnection()) {
      ScriptUtils.executeSqlScript(conn, v10Script);
    }

    // 4. Verify columns exist
    Integer coverColumnCount =
        jdbc.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_name = 'track' and column_name = 'cover_url'
            """,
            Integer.class);
    assertThat(coverColumnCount).isEqualTo(1);

    Integer healthColumnCount =
        jdbc.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_name = 'track' and column_name = 'last_preview_checked_at'
            """,
            Integer.class);
    assertThat(healthColumnCount).isEqualTo(1);

    Integer indexCount =
        jdbc.queryForObject(
            """
            select count(*) from pg_indexes
            where tablename = 'track' and indexname = 'idx_track_health_check'
            """,
            Integer.class);
    assertThat(indexCount).isEqualTo(1);

    // 5. Verify pre-existing track has null cover_url and null last_preview_checked_at
    String legacyCover =
        jdbc.queryForObject(
            "select cover_url from track where id = ?", String.class, preExistingTrackId);
    assertThat(legacyCover).isNull();

    java.sql.Timestamp legacyCheckedAt =
        jdbc.queryForObject(
            "select last_preview_checked_at from track where id = ?",
            java.sql.Timestamp.class,
            preExistingTrackId);
    assertThat(legacyCheckedAt).isNull();

    // 6. Verify newly imported track stores cover_url and last_preview_checked_at
    UUID newTrackId = UUID.randomUUID();
    String newCoverUrl = "https://e-cdns-images.dzcdn.net/images/cover/v10/250x250.jpg";
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, cover_url, last_preview_checked_at, created_at)
        values (?, 'DEEZER', 'dz-mig-new', 'Newly Imported Track', 'newly imported track', ?, ?, now(), now())
        """,
        newTrackId,
        artistId,
        newCoverUrl);

    String retrievedCover =
        jdbc.queryForObject("select cover_url from track where id = ?", String.class, newTrackId);
    assertThat(retrievedCover).isEqualTo(newCoverUrl);

    // 7. Verify newly imported track without cover has null cover_url
    UUID trackWithoutCoverId = UUID.randomUUID();
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, cover_url, created_at)
        values (?, 'DEEZER', 'dz-mig-no-cover', 'Track Without Cover', 'track without cover', ?, null, now())
        """,
        trackWithoutCoverId,
        artistId);

    String noCover =
        jdbc.queryForObject(
            "select cover_url from track where id = ?", String.class, trackWithoutCoverId);
    assertThat(noCover).isNull();
  }
}
