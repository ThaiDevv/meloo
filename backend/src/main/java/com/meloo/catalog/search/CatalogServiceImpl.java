package com.meloo.catalog.search;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
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

  public CatalogServiceImpl(CatalogQueryRepository catalogQueryRepository) {
    this.catalogQueryRepository = catalogQueryRepository;
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
  public Optional<TrackInfo> getTrackById(UUID trackId) {
    return catalogQueryRepository.findTrackById(trackId);
  }

  @Override
  public Map<UUID, TrackInfo> getTracksByIds(Collection<UUID> trackIds) {
    return catalogQueryRepository.findTracksByIds(trackIds);
  }
}
