package com.meloo.catalog.admin;

import com.meloo.catalog.api.AdminChange;
import com.meloo.catalog.api.AdminPage;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.catalog.api.CatalogAdminException.Reason;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.catalog.search.TrackSearchService;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Track part of the catalog admin API. Every method runs in its own transaction. */
@Component
@Transactional
class AdminTrackOperations {

  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_REASON_LENGTH = 200;

  private static final String SELECT_TRACK =
      """
      SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
             t.genre_id, g.code AS genre_code, t.country_code, t.release_year,
             t.enabled, t.disabled_reason, t.cover_url
      FROM track t
      JOIN artist a ON a.id = t.primary_artist_id
      LEFT JOIN genre g ON g.id = t.genre_id
      """;

  private static final RowMapper<AdminTrackView> TRACK_MAPPER =
      (rs, i) -> {
        short year = rs.getShort("release_year");
        Short releaseYear = rs.wasNull() ? null : year;
        return new AdminTrackView(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("artist_name"),
            rs.getString("provider"),
            rs.getString("provider_track_id"),
            rs.getObject("genre_id", UUID.class),
            rs.getString("genre_code"),
            rs.getString("country_code"),
            releaseYear,
            rs.getBoolean("enabled"),
            rs.getString("disabled_reason"),
            rs.getString("cover_url"));
      };

  private final NamedParameterJdbcTemplate jdbc;

  AdminTrackOperations(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  AdminPage<AdminTrackView> search(AdminTrackQuery query) {
    int page = Math.max(0, query.page());
    int size = Math.max(1, Math.min(query.size(), MAX_PAGE_SIZE));

    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    MapSqlParameterSource params = new MapSqlParameterSource();
    String text = TrackSearchService.normalizeSearchQuery(query.text());
    if (!text.isEmpty()) {
      where.append(" AND (t.title_normalized LIKE :text OR a.name_normalized LIKE :text)");
      params.addValue("text", "%" + text + "%");
    }
    if (query.enabled() != null) {
      where.append(" AND t.enabled = :enabled");
      params.addValue("enabled", query.enabled());
    }
    if (query.genreCode() != null && !query.genreCode().isBlank()) {
      where.append(" AND g.code = :genreCode");
      params.addValue("genreCode", query.genreCode());
    }
    if (query.countryCode() != null && !query.countryCode().isBlank()) {
      where.append(" AND t.country_code = :countryCode");
      params.addValue("countryCode", query.countryCode().toUpperCase(Locale.ROOT));
    }
    if (query.decade() != null) {
      where.append(" AND t.release_year >= :decadeFrom AND t.release_year < :decadeTo");
      params.addValue("decadeFrom", query.decade());
      params.addValue("decadeTo", query.decade() + 10);
    }

    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM track t JOIN artist a ON a.id = t.primary_artist_id"
                + " LEFT JOIN genre g ON g.id = t.genre_id"
                + where,
            params,
            Long.class);
    params.addValue("limit", size);
    params.addValue("offset", (long) page * size);
    List<AdminTrackView> items =
        jdbc.query(
            SELECT_TRACK + where + " ORDER BY t.created_at DESC, t.id LIMIT :limit OFFSET :offset",
            params,
            TRACK_MAPPER);
    return new AdminPage<>(items, page, size, total == null ? 0 : total);
  }

  AdminChange<AdminTrackView> setEnabled(UUID trackId, boolean enabled, @Nullable String reason) {
    AdminTrackView before = lockTrack(trackId);
    String storedReason = null;
    if (!enabled && reason != null && !reason.isBlank()) {
      storedReason = reason.strip();
      if (storedReason.length() > MAX_REASON_LENGTH) {
        throw new CatalogAdminException(
            Reason.INVALID_ARGUMENT, "reason must be at most " + MAX_REASON_LENGTH + " characters");
      }
    }
    jdbc.update(
        "UPDATE track SET enabled = :enabled, disabled_reason = :reason WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("enabled", enabled)
            .addValue("reason", storedReason)
            .addValue("id", trackId));
    return new AdminChange<>(before, getView(trackId));
  }

  AdminChange<AdminTrackView> updateMetadata(UUID trackId, TrackMetadataUpdate update) {
    if (update == null) {
      throw new CatalogAdminException(Reason.INVALID_ARGUMENT, "update must not be null");
    }
    AdminTrackView before = lockTrack(trackId);

    requireGenre(update.genreId());
    String country = update.countryCode();
    if (country != null) {
      country = country.strip().toUpperCase(Locale.ROOT);
      if (!country.matches("[A-Z]{2}")) {
        throw new CatalogAdminException(
            Reason.INVALID_ARGUMENT, "countryCode must be an ISO 3166-1 alpha-2 code");
      }
    }
    Short year = update.releaseYear();
    if (year != null && (year < 1900 || year > Year.now().getValue() + 1)) {
      throw new CatalogAdminException(Reason.INVALID_ARGUMENT, "releaseYear is out of range");
    }

    jdbc.update(
        """
        UPDATE track SET genre_id = :genreId, country_code = :country, release_year = :year
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("genreId", update.genreId())
            .addValue("country", country)
            .addValue("year", year)
            .addValue("id", trackId));
    return new AdminChange<>(before, getView(trackId));
  }

  @Transactional(readOnly = true)
  void requireGenre(@Nullable UUID genreId) {
    if (genreId == null) {
      return;
    }
    Integer genres =
        jdbc.queryForObject(
            "SELECT count(*) FROM genre WHERE id = :id",
            new MapSqlParameterSource("id", genreId),
            Integer.class);
    if (genres == null || genres == 0) {
      throw new CatalogAdminException(Reason.NOT_FOUND, "Genre not found: " + genreId);
    }
  }

  @Transactional(readOnly = true)
  List<AdminTrackView> getViews(Collection<UUID> trackIds) {
    if (trackIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        SELECT_TRACK + " WHERE t.id IN (:ids) ORDER BY t.created_at, t.id",
        new MapSqlParameterSource("ids", trackIds),
        TRACK_MAPPER);
  }

  private AdminTrackView getView(UUID trackId) {
    List<AdminTrackView> views =
        jdbc.query(
            SELECT_TRACK + " WHERE t.id = :id",
            new MapSqlParameterSource("id", trackId),
            TRACK_MAPPER);
    if (views.isEmpty()) {
      throw new CatalogAdminException(Reason.NOT_FOUND, "Track not found: " + trackId);
    }
    return views.getFirst();
  }

  /** Reads the track view while holding a row lock so before/after cannot interleave. */
  private AdminTrackView lockTrack(UUID trackId) {
    List<UUID> locked =
        jdbc.queryForList(
            "SELECT id FROM track WHERE id = :id FOR UPDATE",
            new MapSqlParameterSource("id", trackId),
            UUID.class);
    if (locked.isEmpty()) {
      throw new CatalogAdminException(Reason.NOT_FOUND, "Track not found: " + trackId);
    }
    return getView(trackId);
  }
}
