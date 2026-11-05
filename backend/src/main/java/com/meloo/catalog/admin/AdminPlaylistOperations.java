package com.meloo.catalog.admin;

import com.meloo.catalog.api.AdminChange;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.catalog.api.CatalogAdminException.Reason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Playlist part of the catalog admin API (BR-09: a playlist needs enough valid tracks). */
@Component
@Transactional
class AdminPlaylistOperations {

  private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,59}");
  private static final int MAX_NAME_LENGTH = 120;

  private final NamedParameterJdbcTemplate jdbc;
  private final int minTracks;

  AdminPlaylistOperations(
      NamedParameterJdbcTemplate jdbc,
      @Value("${meloo.catalog.admin.min-playlist-tracks:5}") int minTracks) {
    this.jdbc = jdbc;
    this.minTracks = minTracks;
  }

  int minTracks() {
    return minTracks;
  }

  @Transactional(readOnly = true)
  List<AdminPlaylistView> list() {
    List<AdminPlaylistView> heads =
        jdbc.query(
            "SELECT id, code, name_vi, name_en, kind, enabled FROM playlist"
                + " ORDER BY kind, created_at, id",
            (rs, i) ->
                new AdminPlaylistView(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("name_vi"),
                    rs.getString("name_en"),
                    "CURATED".equals(rs.getString("kind")),
                    rs.getBoolean("enabled"),
                    List.of(),
                    0));
    if (heads.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = heads.stream().map(AdminPlaylistView::id).toList();
    MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);

    Map<UUID, List<UUID>> tracksByPlaylist = new HashMap<>();
    jdbc.query(
        "SELECT playlist_id, track_id FROM playlist_track WHERE playlist_id IN (:ids)"
            + " ORDER BY playlist_id, position, track_id",
        p,
        rs -> {
          tracksByPlaylist
              .computeIfAbsent(rs.getObject("playlist_id", UUID.class), k -> new ArrayList<>())
              .add(rs.getObject("track_id", UUID.class));
        });
    Map<UUID, Integer> enabledByPlaylist = new HashMap<>();
    jdbc.query(
        """
        SELECT pt.playlist_id, count(*) AS cnt
        FROM playlist_track pt JOIN track t ON t.id = pt.track_id
        WHERE pt.playlist_id IN (:ids) AND t.enabled = true
        GROUP BY pt.playlist_id
        """,
        p,
        rs -> {
          enabledByPlaylist.put(rs.getObject("playlist_id", UUID.class), rs.getInt("cnt"));
        });

    return heads.stream()
        .map(
            h ->
                new AdminPlaylistView(
                    h.id(),
                    h.code(),
                    h.nameVi(),
                    h.nameEn(),
                    h.curated(),
                    h.enabled(),
                    tracksByPlaylist.getOrDefault(h.id(), List.of()),
                    enabledByPlaylist.getOrDefault(h.id(), 0)))
        .toList();
  }

  AdminChange<AdminPlaylistView> create(
      String code, String nameVi, String nameEn, List<UUID> trackIds) {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new CatalogAdminException(
          Reason.INVALID_ARGUMENT, "code must be lowercase letters, digits and dashes (max 60)");
    }
    checkName(nameVi);
    checkName(nameEn);
    requireNotNull(trackIds, "trackIds");
    LinkedHashSet<UUID> ids = new LinkedHashSet<>(trackIds);
    requireValidTracks(ids);
    if (ids.size() < minTracks) {
      throw notEnough(ids.size());
    }
    Integer existing =
        jdbc.queryForObject(
            "SELECT count(*) FROM playlist WHERE code = :code",
            new MapSqlParameterSource("code", code),
            Integer.class);
    if (existing != null && existing > 0) {
      throw new CatalogAdminException(Reason.CONFLICT, "Playlist code already exists: " + code);
    }

    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO playlist (id, code, name_vi, name_en, kind, enabled)
        VALUES (:id, :code, :nameVi, :nameEn, 'CURATED', true)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("code", code)
            .addValue("nameVi", nameVi.strip())
            .addValue("nameEn", nameEn.strip()));
    insertTracks(id, List.copyOf(ids), 0);
    return new AdminChange<>(null, view(id));
  }

  AdminChange<AdminPlaylistView> update(
      UUID playlistId, String nameVi, String nameEn, boolean enabled) {
    AdminPlaylistView before = lockCurated(playlistId);
    checkName(nameVi);
    checkName(nameEn);
    if (enabled && before.enabledTrackCount() < minTracks) {
      throw notEnough(before.enabledTrackCount());
    }
    jdbc.update(
        "UPDATE playlist SET name_vi = :nameVi, name_en = :nameEn, enabled = :enabled"
            + " WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("nameVi", nameVi.strip())
            .addValue("nameEn", nameEn.strip())
            .addValue("enabled", enabled)
            .addValue("id", playlistId));
    return new AdminChange<>(before, view(playlistId));
  }

  AdminChange<AdminPlaylistView> addTracks(UUID playlistId, Collection<UUID> trackIds) {
    requireNotNull(trackIds, "trackIds");
    AdminPlaylistView before = lockCurated(playlistId);
    LinkedHashSet<UUID> toAdd = new LinkedHashSet<>(trackIds);
    toAdd.removeAll(before.trackIds());
    requireValidTracks(toAdd);
    insertTracks(playlistId, List.copyOf(toAdd), before.trackIds().size());
    return new AdminChange<>(before, view(playlistId));
  }

  AdminChange<AdminPlaylistView> removeTracks(UUID playlistId, Collection<UUID> trackIds) {
    requireNotNull(trackIds, "trackIds");
    AdminPlaylistView before = lockCurated(playlistId);
    if (!trackIds.isEmpty()) {
      jdbc.update(
          "DELETE FROM playlist_track WHERE playlist_id = :pid AND track_id IN (:ids)",
          new MapSqlParameterSource().addValue("pid", playlistId).addValue("ids", trackIds));
      jdbc.update(
          """
          UPDATE playlist_track pt SET position = r.rn - 1
          FROM (SELECT track_id, row_number() OVER (ORDER BY position, track_id) AS rn
                FROM playlist_track WHERE playlist_id = :pid) r
          WHERE pt.playlist_id = :pid AND pt.track_id = r.track_id
          """,
          new MapSqlParameterSource("pid", playlistId));
    }
    AdminPlaylistView after = view(playlistId);
    if (after.enabled() && after.enabledTrackCount() < minTracks) {
      throw notEnough(after.enabledTrackCount());
    }
    return new AdminChange<>(before, after);
  }

  AdminChange<AdminPlaylistView> delete(UUID playlistId) {
    AdminPlaylistView before = lockCurated(playlistId);
    jdbc.update("DELETE FROM playlist WHERE id = :id", new MapSqlParameterSource("id", playlistId));
    return new AdminChange<>(before, null);
  }

  private AdminPlaylistView lockCurated(UUID playlistId) {
    List<String> kinds =
        jdbc.queryForList(
            "SELECT kind FROM playlist WHERE id = :id FOR UPDATE",
            new MapSqlParameterSource("id", playlistId),
            String.class);
    if (kinds.isEmpty()) {
      throw new CatalogAdminException(Reason.NOT_FOUND, "Playlist not found: " + playlistId);
    }
    if (!"CURATED".equals(kinds.getFirst())) {
      throw new CatalogAdminException(
          Reason.INVALID_ARGUMENT, "Only curated playlists can be changed");
    }
    return view(playlistId);
  }

  private void requireValidTracks(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return;
    }
    Map<UUID, Boolean> found = new HashMap<>();
    jdbc.query(
        "SELECT id, enabled FROM track WHERE id IN (:ids)",
        new MapSqlParameterSource("ids", ids),
        rs -> {
          found.put(rs.getObject("id", UUID.class), rs.getBoolean("enabled"));
        });
    for (UUID id : ids) {
      Boolean enabled = found.get(id);
      if (enabled == null) {
        throw new CatalogAdminException(Reason.NOT_FOUND, "Track not found: " + id);
      }
      if (!enabled) {
        throw new CatalogAdminException(
            Reason.INVALID_ARGUMENT, "Track is disabled and cannot be added: " + id);
      }
    }
  }

  private void insertTracks(UUID playlistId, List<UUID> trackIds, int startPosition) {
    List<MapSqlParameterSource> batch = new ArrayList<>();
    int position = startPosition;
    for (UUID trackId : trackIds) {
      batch.add(
          new MapSqlParameterSource()
              .addValue("pid", playlistId)
              .addValue("tid", trackId)
              .addValue("pos", position++));
    }
    if (!batch.isEmpty()) {
      jdbc.batchUpdate(
          "INSERT INTO playlist_track (playlist_id, track_id, position)"
              + " VALUES (:pid, :tid, :pos)",
          batch.toArray(MapSqlParameterSource[]::new));
    }
  }

  private AdminPlaylistView view(UUID id) {
    MapSqlParameterSource p = new MapSqlParameterSource("id", id);
    AdminPlaylistView head =
        jdbc.queryForObject(
            "SELECT code, name_vi, name_en, kind, enabled FROM playlist WHERE id = :id",
            p,
            (rs, i) ->
                new AdminPlaylistView(
                    id,
                    rs.getString("code"),
                    rs.getString("name_vi"),
                    rs.getString("name_en"),
                    "CURATED".equals(rs.getString("kind")),
                    rs.getBoolean("enabled"),
                    List.of(),
                    0));
    List<UUID> tracks =
        jdbc.queryForList(
            "SELECT track_id FROM playlist_track WHERE playlist_id = :id ORDER BY position, track_id",
            p,
            UUID.class);
    Integer enabledCount =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM playlist_track pt JOIN track t ON t.id = pt.track_id
            WHERE pt.playlist_id = :id AND t.enabled = true
            """,
            p,
            Integer.class);
    return new AdminPlaylistView(
        id,
        head.code(),
        head.nameVi(),
        head.nameEn(),
        head.curated(),
        head.enabled(),
        tracks,
        enabledCount == null ? 0 : enabledCount);
  }

  private static void requireNotNull(Object value, String name) {
    if (value == null) {
      throw new CatalogAdminException(Reason.INVALID_ARGUMENT, name + " must not be null");
    }
  }

  private void checkName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > MAX_NAME_LENGTH) {
      throw new CatalogAdminException(
          Reason.INVALID_ARGUMENT, "name must be 1-" + MAX_NAME_LENGTH + " characters");
    }
  }

  private CatalogAdminException notEnough(int have) {
    return new CatalogAdminException(
        Reason.PLAYLIST_NOT_ENOUGH_TRACKS,
        "Playlist needs at least " + minTracks + " valid tracks, has " + have);
  }
}
