package com.meloo.catalog.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CachedMusicProvider implements MusicProvider {

  private final MusicProvider delegate;
  private final Cache<String, Optional<ProviderTrack>> trackCache;
  private final Cache<String, List<ProviderTrack>> searchCache;
  private final Cache<Integer, List<ProviderTrack>> chartCache;
  private final Cache<String, List<ProviderTrack>> playlistCache;
  private final Cache<String, List<ProviderTrack>> artistTopCache;

  public CachedMusicProvider(MusicProvider delegate, Duration ttl, long maxSize) {
    this.delegate = delegate;
    this.trackCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    this.searchCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    this.chartCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    this.playlistCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    this.artistTopCache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
  }

  public CachedMusicProvider(MusicProvider delegate, CatalogProviderProperties properties) {
    this(delegate, properties.cache().ttl(), properties.cache().maxSize());
  }

  @Override
  public String getProviderName() {
    return delegate.getProviderName();
  }

  @Override
  public List<ProviderTrack> search(String query, int limit) {
    String key = (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + ":" + limit;
    return searchCache.get(key, k -> delegate.search(query, limit));
  }

  @Override
  public Optional<ProviderTrack> getTrack(String providerTrackId) {
    return trackCache.get(providerTrackId, delegate::getTrack);
  }

  @Override
  public List<ProviderTrack> chart(int limit) {
    return chartCache.get(limit, delegate::chart);
  }

  @Override
  public List<ProviderTrack> playlist(String playlistId, int limit) {
    String key = playlistId + ":" + limit;
    return playlistCache.get(key, k -> delegate.playlist(playlistId, limit));
  }

  @Override
  public List<ProviderTrack> artistTopTracks(String artistId, int limit) {
    String key = artistId + ":" + limit;
    return artistTopCache.get(key, k -> delegate.artistTopTracks(artistId, limit));
  }

  public MusicProvider getDelegate() {
    return delegate;
  }

  public void invalidateAll() {
    trackCache.invalidateAll();
    searchCache.invalidateAll();
    chartCache.invalidateAll();
    playlistCache.invalidateAll();
    artistTopCache.invalidateAll();
  }

  public long getCachedTrackCount() {
    return trackCache.estimatedSize();
  }
}
