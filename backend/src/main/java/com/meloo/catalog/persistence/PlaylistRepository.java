package com.meloo.catalog.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

  @Query(
      value = PlaylistInfoSql.SELECT_FROM + "WHERE p.code = :code\n" + PlaylistInfoSql.GROUP_BY,
      nativeQuery = true)
  Optional<PlaylistInfoProjection> findInfoByCode(@Param("code") String code);

  @Query(
      value = PlaylistInfoSql.SELECT_FROM + "WHERE p.id = :id\n" + PlaylistInfoSql.GROUP_BY,
      nativeQuery = true)
  Optional<PlaylistInfoProjection> findInfoById(@Param("id") UUID id);

  interface PlaylistInfoProjection {

    UUID getId();

    String getCode();

    String getName();

    boolean getEnabled();

    Long getEnabledTrackCount();
  }
}

final class PlaylistInfoSql {

  static final String SELECT_FROM =
      """
      SELECT p.id AS id,
             p.code AS code,
             p.name_vi AS name,
             p.enabled AS enabled,
             COUNT(t.id) AS "enabledTrackCount"
      FROM playlist p
      LEFT JOIN playlist_track pt ON pt.playlist_id = p.id
      LEFT JOIN track t ON t.id = pt.track_id AND t.enabled = true
      """;

  static final String GROUP_BY = "GROUP BY p.id, p.code, p.name_vi, p.enabled";

  private PlaylistInfoSql() {}
}
