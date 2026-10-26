package com.meloo.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CatalogSeedJsonQualityTest {

  private static final Pattern NON_ORIGINAL_PATTERN =
      Pattern.compile(
          "(?i)\\b(remix|live|version|acoustic|edit|mix|cover|instrumental|piano|slowed|sped\\s*up|karaoke|orchestral|orchestra|demo|tribute)\\b");

  private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d+$");

  @Test
  @DisplayName("catalog-seed.json satisfies all quality and schema requirements")
  void catalogSeedJson_meetsSchemaAndQualityRequirements() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream("seed/catalog-seed.json")) {
      assertThat(is).as("catalog-seed.json must exist in classpath").isNotNull();

      CatalogSeedService.SeedCatalogDto dto =
          mapper.readValue(is, CatalogSeedService.SeedCatalogDto.class);

      assertThat(dto.genres()).isNotEmpty().hasSizeGreaterThanOrEqualTo(8);
      assertThat(dto.playlists()).isNotEmpty().hasSizeBetween(5, 10);
      assertThat(dto.tracks()).isNotEmpty().hasSizeGreaterThanOrEqualTo(1000);

      Set<String> artistTitlePairs = new HashSet<>();

      for (CatalogSeedService.SeedTrackDto track : dto.tracks()) {
        assertThat(track.providerTrackId())
            .as("Track must have providerTrackId")
            .isNotBlank()
            .matches(NUMERIC_ID_PATTERN);

        assertThat(track.providerArtistId())
            .as("Track %s must have numeric providerArtistId", track.title())
            .isNotNull()
            .matches(NUMERIC_ID_PATTERN);

        assertThat(track.artistName()).as("Track must have artistName").isNotBlank();

        assertThat(track.title())
            .as("Track title must not be blank")
            .isNotBlank()
            .as("Track %s must be original version (no remixes/lives/etc)", track.title())
            .doesNotMatch(NON_ORIGINAL_PATTERN);

        assertThat(track.releaseYear())
            .as("Track %s must have releaseYear", track.title())
            .isNotNull()
            .isBetween((short) 1950, (short) 2026);

        assertThat(track.popularityRank())
            .as("Track %s must have popularityRank >= 1", track.title())
            .isNotNull()
            .isGreaterThanOrEqualTo(1);

        assertThat(track.previewUrl())
            .as("Track %s must have previewUrl", track.title())
            .isNotBlank()
            .startsWith("http");

        String normalizedTitle = TitleNormalizer.normalizeTitle(track.title());
        String artistId = track.providerArtistId();
        String deduplicationKey = artistId + "::" + normalizedTitle;

        assertThat(artistTitlePairs.add(deduplicationKey))
            .as(
                "Artist %s (%s) has duplicate song title '%s' (normalized: '%s')",
                track.artistName(), artistId, track.title(), normalizedTitle)
            .isTrue();
      }
    }
  }
}
