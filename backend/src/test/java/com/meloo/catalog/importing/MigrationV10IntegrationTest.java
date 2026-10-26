package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationV10IntegrationTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "Migration V10 runs on pre-existing data: legacy tracks have null cover_url, new tracks store cover_url")
  void migrationV10_runsOnExistingData() {
    UUID artistId = UUID.randomUUID();
    jdbc.update(
        """
        insert into artist (id, name, name_normalized, provider, provider_artist_id, created_at)
        values (?, 'Pre-V10 Artist', 'pre-v10 artist', 'DEEZER', 'art-mig-pre-v10', now())
        """,
        artistId);

    // 1. Simulate state BEFORE Migration V10 by dropping the column
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

    // 3. Apply Migration V10 on the database containing pre-existing data
    jdbc.execute("alter table track add column cover_url varchar(500)");

    // 4. Verify column exists
    Integer columnCount =
        jdbc.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_name = 'track' and column_name = 'cover_url'
            """,
            Integer.class);
    assertThat(columnCount).isEqualTo(1);

    // 5. Verify pre-existing track has null cover_url
    String legacyCover =
        jdbc.queryForObject(
            "select cover_url from track where id = ?", String.class, preExistingTrackId);
    assertThat(legacyCover).isNull();

    // 6. Verify newly imported track stores cover_url
    UUID newTrackId = UUID.randomUUID();
    String newCoverUrl = "https://e-cdns-images.dzcdn.net/images/cover/v10/250x250.jpg";
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, cover_url, created_at)
        values (?, 'DEEZER', 'dz-mig-new', 'Newly Imported Track', 'newly imported track', ?, ?, now())
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
