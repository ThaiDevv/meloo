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
      "Migration V10 adds cover_url column; existing tracks have null, new tracks can store cover_url")
  void migrationV10_trackCoverUrlColumn() {
    // 1. Verify column exists in track table
    Integer columnCount =
        jdbc.queryForObject(
            """
            select count(*) from information_schema.columns
            where table_name = 'track' and column_name = 'cover_url'
            """,
            Integer.class);
    assertThat(columnCount).isEqualTo(1);

    // 2. Insert test artist and track with null cover_url (simulating legacy data)
    UUID artistId = UUID.randomUUID();
    jdbc.update(
        """
        insert into artist (id, name, name_normalized, provider, provider_artist_id, created_at)
        values (?, 'Migration V10 Artist', 'migration v10 artist', 'DEEZER', 'art-mig-v10', now())
        """,
        artistId);

    UUID trackLegacyId = UUID.randomUUID();
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, created_at)
        values (?, 'DEEZER', 'dz-mig-legacy', 'Legacy Track', 'legacy track', ?, now())
        """,
        trackLegacyId,
        artistId);

    String legacyCover =
        jdbc.queryForObject(
            "select cover_url from track where id = ?", String.class, trackLegacyId);
    assertThat(legacyCover).isNull();

    // 3. Insert new track with cover_url (simulating new imported track)
    UUID trackNewId = UUID.randomUUID();
    String coverUrl = "https://e-cdns-images.dzcdn.net/images/cover/v10/250x250.jpg";
    jdbc.update(
        """
        insert into track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, cover_url, created_at)
        values (?, 'DEEZER', 'dz-mig-new', 'New Track', 'new track', ?, ?, now())
        """,
        trackNewId,
        artistId,
        coverUrl);

    String newCover =
        jdbc.queryForObject("select cover_url from track where id = ?", String.class, trackNewId);
    assertThat(newCover).isEqualTo(coverUrl);
  }
}
