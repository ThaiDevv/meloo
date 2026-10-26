package com.meloo.catalog.provider;

import java.util.List;
import java.util.Optional;

public interface MusicProvider {

  String getProviderName();

  List<ProviderTrack> search(String query, int limit);

  default List<ProviderTrack> search(String query) {
    return search(query, 20);
  }

  Optional<ProviderTrack> getTrack(String providerTrackId);

  List<ProviderTrack> chart(int limit);

  default List<ProviderTrack> chart() {
    return chart(20);
  }

  List<ProviderTrack> playlist(String playlistId, int limit);

  default List<ProviderTrack> playlist(String playlistId) {
    return playlist(playlistId, 50);
  }

  List<ProviderTrack> artistTopTracks(String artistId, int limit);

  default List<ProviderTrack> artistTopTracks(String artistId) {
    return artistTopTracks(artistId, 10);
  }
}
