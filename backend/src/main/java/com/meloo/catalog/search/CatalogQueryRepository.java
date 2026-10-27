package com.meloo.catalog.search;

import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.ArtistDto;
import com.meloo.generated.model.FilterOptionsDto;
import com.meloo.generated.model.GenreDto;
import com.meloo.generated.model.PlaylistDto;
import com.meloo.generated.model.PlaylistKindDto;
import com.meloo.generated.model.PlaylistPageDto;
import com.meloo.generated.model.TrackPageDto;
import com.meloo.generated.model.TrackSuggestionDto;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogQueryRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public CatalogQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Searches enabled tracks ranking: prefix match > word boundary match > trigram similarity.
   *
   * <p>Optimized with CTE/UNION to separate track title matching and artist name matching, allowing
   * PostgreSQL planner to use GIN indexes (idx_track_title_trgm and idx_artist_name_trgm). For the
   * initial page (page=0), if results are fewer than limit, skips count query.
   */
  public TrackPageDto searchTracks(String normalizedQuery, int page, int size) {
    if (normalizedQuery.isBlank() || normalizedQuery.length() < 2) {
      return new TrackPageDto()
          .page(page)
          .size(size)
          .totalItems(0L)
          .totalPages(0)
          .items(Collections.emptyList());
    }

    String norm = normalizedQuery;
    String prefix = norm + "%";
    String wordPrefix = "% " + norm + "%";
    String contains = "%" + norm + "%";

    int limit = Math.max(1, Math.min(size, 50));
    long offset = (long) Math.max(0, page) * limit;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("norm", norm)
            .addValue("prefix", prefix)
            .addValue("wordPrefix", wordPrefix)
            .addValue("contains", contains)
            .addValue("limit", limit)
            .addValue("offset", offset);

    String selectSql =
        """
        WITH matching_artists AS (
          SELECT id, name, name_normalized
          FROM artist
          WHERE name_normalized LIKE :contains
             OR name_normalized % :norm
             OR replace(name_normalized, '-', ' ') LIKE :contains
        ),
        matching_tracks AS (
          SELECT t.id, t.title, t.title_normalized, t.primary_artist_id, t.popularity_rank
          FROM track t
          WHERE t.enabled = true
            AND (t.title_normalized LIKE :contains OR t.title_normalized % :norm)
          UNION
          SELECT t.id, t.title, t.title_normalized, t.primary_artist_id, t.popularity_rank
          FROM track t
          JOIN matching_artists ma ON t.primary_artist_id = ma.id
          WHERE t.enabled = true
        )
        SELECT mt.id, mt.title, a.name AS artist_name,
          CASE
            WHEN mt.title_normalized LIKE :prefix
              OR a.name_normalized LIKE :prefix
              OR replace(a.name_normalized, '-', ' ') LIKE :prefix THEN 1
            WHEN mt.title_normalized LIKE :wordPrefix
              OR a.name_normalized LIKE :wordPrefix
              OR replace(a.name_normalized, '-', ' ') LIKE :wordPrefix
              OR mt.title_normalized LIKE :contains
              OR a.name_normalized LIKE :contains
              OR replace(a.name_normalized, '-', ' ') LIKE :contains THEN 2
            ELSE 3
          END AS tier,
          GREATEST(
            similarity(mt.title_normalized, :norm),
            similarity(a.name_normalized, :norm),
            similarity(replace(a.name_normalized, '-', ' '), :norm)
          ) AS sim
        FROM matching_tracks mt
        JOIN artist a ON mt.primary_artist_id = a.id
        ORDER BY tier ASC, sim DESC, mt.popularity_rank ASC NULLS LAST, mt.title ASC
        LIMIT :limit OFFSET :offset
        """;

    List<TrackSuggestionDto> items =
        jdbcTemplate.query(
            selectSql,
            params,
            (rs, rowNum) ->
                new TrackSuggestionDto()
                    .id(rs.getObject("id", UUID.class))
                    .title(rs.getString("title"))
                    .artistName(rs.getString("artist_name")));

    long total;
    if (page == 0 && items.size() < limit) {
      total = items.size();
    } else {
      String countSql =
          """
          WITH matching_artists AS (
            SELECT id
            FROM artist
            WHERE name_normalized LIKE :contains
               OR name_normalized % :norm
               OR replace(name_normalized, '-', ' ') LIKE :contains
          ),
          matching_tracks AS (
            SELECT t.id
            FROM track t
            WHERE t.enabled = true
              AND (t.title_normalized LIKE :contains OR t.title_normalized % :norm)
            UNION
            SELECT t.id
            FROM track t
            JOIN matching_artists ma ON t.primary_artist_id = ma.id
            WHERE t.enabled = true
          )
          SELECT count(*) FROM matching_tracks
          """;
      Long totalItems = jdbcTemplate.queryForObject(countSql, params, Long.class);
      total = totalItems == null ? 0L : totalItems;
    }

    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limit);

    if (total == 0 || offset >= total) {
      return new TrackPageDto()
          .page(page)
          .size(limit)
          .totalItems(total)
          .totalPages(totalPages)
          .items(Collections.emptyList());
    }

    return new TrackPageDto()
        .page(page)
        .size(limit)
        .totalItems(total)
        .totalPages(totalPages)
        .items(items);
  }

  /** Lists enabled playlists with count of enabled tracks. */
  public PlaylistPageDto listPlaylists(int page, int size) {
    int limit = Math.max(1, Math.min(size, 50));
    long offset = (long) Math.max(0, page) * limit;

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);

    String selectSql =
        """
        SELECT p.id, p.code, p.name_vi, p.name_en, p.kind,
               COUNT(t.id) FILTER (WHERE t.enabled = true) AS track_count
        FROM playlist p
        LEFT JOIN playlist_track pt ON p.id = pt.playlist_id
        LEFT JOIN track t ON pt.track_id = t.id AND t.enabled = true
        WHERE p.enabled = true
        GROUP BY p.id, p.code, p.name_vi, p.name_en, p.kind, p.created_at
        ORDER BY CASE WHEN p.kind = 'STANDARD' THEN 0 ELSE 1 END, p.created_at ASC
        LIMIT :limit OFFSET :offset
        """;

    List<PlaylistDto> items =
        jdbcTemplate.query(
            selectSql,
            params,
            (rs, rowNum) ->
                new PlaylistDto()
                    .id(rs.getObject("id", UUID.class))
                    .code(rs.getString("code"))
                    .nameVi(rs.getString("name_vi"))
                    .nameEn(rs.getString("name_en"))
                    .kind(PlaylistKindDto.fromValue(rs.getString("kind")))
                    .trackCount(rs.getInt("track_count")));

    long total;
    if (page == 0 && items.size() < limit) {
      total = items.size();
    } else {
      String countSql = "SELECT count(*) FROM playlist WHERE enabled = true";
      Long totalItems = jdbcTemplate.queryForObject(countSql, params, Long.class);
      total = totalItems == null ? 0L : totalItems;
    }

    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limit);

    if (total == 0 || offset >= total) {
      return new PlaylistPageDto()
          .page(page)
          .size(limit)
          .totalItems(total)
          .totalPages(totalPages)
          .items(Collections.emptyList());
    }

    return new PlaylistPageDto()
        .page(page)
        .size(limit)
        .totalItems(total)
        .totalPages(totalPages)
        .items(items);
  }

  /** Retrieves filter options populated only with values that have at least one enabled track. */
  public FilterOptionsDto getFilters() {
    String genresSql =
        """
        SELECT DISTINCT g.code, g.name_vi, g.name_en
        FROM genre g
        JOIN track t ON t.genre_id = g.id
        WHERE t.enabled = true
        ORDER BY g.name_vi ASC
        """;
    List<GenreDto> genres =
        jdbcTemplate.query(
            genresSql,
            (rs, rowNum) ->
                new GenreDto()
                    .code(rs.getString("code"))
                    .nameVi(rs.getString("name_vi"))
                    .nameEn(rs.getString("name_en")));

    String countriesSql =
        """
        SELECT DISTINCT t.country_code
        FROM track t
        WHERE t.enabled = true AND t.country_code IS NOT NULL
        ORDER BY t.country_code ASC
        """;
    List<String> countries =
        jdbcTemplate.query(countriesSql, (rs, rowNum) -> rs.getString("country_code").trim());

    String decadesSql =
        """
        SELECT DISTINCT CAST((t.release_year / 10) * 10 AS INTEGER) AS decade
        FROM track t
        WHERE t.enabled = true AND t.release_year IS NOT NULL
        ORDER BY decade ASC
        """;
    List<Integer> decades = jdbcTemplate.query(decadesSql, (rs, rowNum) -> rs.getInt("decade"));

    String artistsSql =
        """
        SELECT DISTINCT a.id, a.name
        FROM artist a
        JOIN track t ON t.primary_artist_id = a.id
        WHERE t.enabled = true
        ORDER BY a.name ASC
        """;
    List<ArtistDto> artists =
        jdbcTemplate.query(
            artistsSql,
            (rs, rowNum) ->
                new ArtistDto().id(rs.getObject("id", UUID.class)).name(rs.getString("name")));

    return new FilterOptionsDto()
        .genres(genres)
        .countries(countries)
        .decades(decades)
        .artists(artists);
  }

  /** Selects up to N random enabled tracks belonging to a playlist. */
  public List<TrackInfo> selectRandomTracksByPlaylist(
      UUID playlistId, int count, Set<UUID> excludedTrackIds) {
    if (count <= 0) {
      return Collections.emptyList();
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
                   t.cover_url, t.genre_id, t.country_code, t.release_year
            FROM playlist_track pt
            JOIN playlist p ON pt.playlist_id = p.id
            JOIN track t ON pt.track_id = t.id
            JOIN artist a ON t.primary_artist_id = a.id
            WHERE p.id = :playlistId
              AND p.enabled = true
              AND t.enabled = true
            """);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("playlistId", playlistId).addValue("limit", count);

    if (excludedTrackIds != null && !excludedTrackIds.isEmpty()) {
      sql.append(" AND t.id NOT IN (:excludedIds)");
      params.addValue("excludedIds", excludedTrackIds);
    }
    sql.append(" ORDER BY RANDOM() LIMIT :limit");

    return jdbcTemplate.query(sql.toString(), params, trackInfoRowMapper);
  }

  /** Selects up to N random enabled tracks belonging to a playlist code. */
  public List<TrackInfo> selectRandomTracksByPlaylistCode(
      String playlistCode, int count, Set<UUID> excludedTrackIds) {
    if (count <= 0) {
      return Collections.emptyList();
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
                   t.cover_url, t.genre_id, t.country_code, t.release_year
            FROM playlist_track pt
            JOIN playlist p ON pt.playlist_id = p.id
            JOIN track t ON pt.track_id = t.id
            JOIN artist a ON t.primary_artist_id = a.id
            WHERE p.code = :playlistCode
              AND p.enabled = true
              AND t.enabled = true
            """);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("playlistCode", playlistCode).addValue("limit", count);

    if (excludedTrackIds != null && !excludedTrackIds.isEmpty()) {
      sql.append(" AND t.id NOT IN (:excludedIds)");
      params.addValue("excludedIds", excludedTrackIds);
    }
    sql.append(" ORDER BY RANDOM() LIMIT :limit");

    return jdbcTemplate.query(sql.toString(), params, trackInfoRowMapper);
  }

  /** Selects up to N random enabled tracks matching the given filter. */
  public List<TrackInfo> selectRandomTracks(
      @Nullable TrackFilter filter, int count, Set<UUID> excludedTrackIds) {
    if (count <= 0) {
      return Collections.emptyList();
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
                   t.cover_url, t.genre_id, t.country_code, t.release_year
            FROM track t
            JOIN artist a ON t.primary_artist_id = a.id
            LEFT JOIN genre g ON t.genre_id = g.id
            WHERE t.enabled = true
            """);

    MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", count);

    if (excludedTrackIds != null && !excludedTrackIds.isEmpty()) {
      sql.append(" AND t.id NOT IN (:excludedIds)");
      params.addValue("excludedIds", excludedTrackIds);
    }

    if (filter != null) {
      if (filter.genreCodes() != null && !filter.genreCodes().isEmpty()) {
        sql.append(" AND g.code IN (:genreCodes)");
        params.addValue("genreCodes", filter.genreCodes());
      }
      if (filter.countryCodes() != null && !filter.countryCodes().isEmpty()) {
        sql.append(" AND t.country_code IN (:countryCodes)");
        params.addValue("countryCodes", filter.countryCodes());
      }
      if (filter.decades() != null && !filter.decades().isEmpty()) {
        sql.append(" AND CAST((t.release_year / 10) * 10 AS INTEGER) IN (:decades)");
        params.addValue("decades", filter.decades());
      }
      if (filter.artistIds() != null && !filter.artistIds().isEmpty()) {
        sql.append(" AND a.id IN (:artistIds)");
        params.addValue("artistIds", filter.artistIds());
      }
    }

    sql.append(" ORDER BY RANDOM() LIMIT :limit");

    return jdbcTemplate.query(sql.toString(), params, trackInfoRowMapper);
  }

  /** Finds track information by ID (enabled tracks only). */
  public Optional<TrackInfo> findTrackById(UUID trackId) {
    String sql =
        """
        SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
               t.cover_url, t.genre_id, t.country_code, t.release_year
        FROM track t
        JOIN artist a ON t.primary_artist_id = a.id
        WHERE t.id = :id AND t.enabled = true
        """;
    List<TrackInfo> result =
        jdbcTemplate.query(sql, new MapSqlParameterSource("id", trackId), trackInfoRowMapper);
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  /** Finds track information for multiple IDs (enabled tracks only). */
  public Map<UUID, TrackInfo> findTracksByIds(Collection<UUID> trackIds) {
    if (trackIds.isEmpty()) {
      return Collections.emptyMap();
    }
    String sql =
        """
        SELECT t.id, t.title, a.name AS artist_name, t.provider, t.provider_track_id,
               t.cover_url, t.genre_id, t.country_code, t.release_year
        FROM track t
        JOIN artist a ON t.primary_artist_id = a.id
        WHERE t.id IN (:ids) AND t.enabled = true
        """;
    List<TrackInfo> list =
        jdbcTemplate.query(sql, new MapSqlParameterSource("ids", trackIds), trackInfoRowMapper);
    Map<UUID, TrackInfo> map = new HashMap<>();
    for (TrackInfo info : list) {
      map.put(info.id(), info);
    }
    return map;
  }

  private static final RowMapper<TrackInfo> trackInfoRowMapper =
      (rs, rowNum) -> {
        Short releaseYear = null;
        short rawYear = rs.getShort("release_year");
        if (!rs.wasNull()) {
          releaseYear = rawYear;
        }
        return new TrackInfo(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("artist_name"),
            rs.getString("provider"),
            rs.getString("provider_track_id"),
            rs.getString("cover_url"),
            rs.getObject("genre_id", UUID.class),
            rs.getString("country_code") != null ? rs.getString("country_code").trim() : null,
            releaseYear);
      };
}
