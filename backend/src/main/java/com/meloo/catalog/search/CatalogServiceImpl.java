package com.meloo.catalog.search;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.PlaylistInfo;
import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.catalog.persistence.PlaylistRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

  private final CatalogQueryRepository catalogQueryRepository;
  private final PlaylistRepository playlistRepository;

  public CatalogServiceImpl(
      CatalogQueryRepository catalogQueryRepository, PlaylistRepository playlistRepository) {
    this.catalogQueryRepository = catalogQueryRepository;
    this.playlistRepository = playlistRepository;
  }

  @Override
  public List<TrackInfo> selectRandomTracksByPlaylist(
      UUID playlistId, int count, Set<UUID> excludedTrackIds) {
    return catalogQueryRepository.selectRandomTracksByPlaylist(playlistId, count, excludedTrackIds);
  }

  @Override
  public List<TrackInfo> selectRandomTracksByPlaylistCode(
      String playlistCode, int count, Set<UUID> excludedTrackIds) {
    return catalogQueryRepository.selectRandomTracksByPlaylistCode(
        playlistCode, count, excludedTrackIds);
  }

  @Override
  public List<TrackInfo> selectRandomTracks(
      @Nullable TrackFilter filter, int count, Set<UUID> excludedTrackIds) {
    return catalogQueryRepository.selectRandomTracks(filter, count, excludedTrackIds);
  }

  @Override
  public List<TrackInfo> selectPopularTracks(int count) {
    return catalogQueryRepository.selectPopularTracks(count);
  }

  @Override
  public Optional<TrackInfo> getTrackById(UUID trackId) {
    return catalogQueryRepository.findTrackById(trackId);
  }

  @Override
  public Optional<PlaylistInfo> findPlaylistByCode(String code) {
    return playlistRepository.findInfoByCode(code).map(this::toPlaylistInfo);
  }

  @Override
  public Optional<PlaylistInfo> findPlaylistById(UUID id) {
    return playlistRepository.findInfoById(id).map(this::toPlaylistInfo);
  }

  @Override
  public Map<UUID, TrackInfo> getTracksByIds(Collection<UUID> trackIds) {
    return catalogQueryRepository.findTracksByIds(trackIds);
  }

  private PlaylistInfo toPlaylistInfo(PlaylistRepository.PlaylistInfoProjection projection) {
    return new PlaylistInfo(
        projection.getId(),
        projection.getCode(),
        projection.getName(),
        projection.getEnabled(),
        Math.toIntExact(projection.getEnabledTrackCount()));
  }
}
