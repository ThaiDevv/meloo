package com.meloo.catalog.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CachedMusicProviderTest {

  private MusicProvider delegate;
  private CachedMusicProvider cachedProvider;

  @BeforeEach
  void setUp() {
    delegate = mock(MusicProvider.class);
    when(delegate.getProviderName()).thenReturn("MOCK");
    cachedProvider = new CachedMusicProvider(delegate, Duration.ofMinutes(10), 100);
  }

  @Test
  @DisplayName("getTrack caches result and does not call delegate twice")
  void getTrack_cachesResult() {
    ProviderTrack track =
        ProviderTrack.builder()
            .providerTrackId("t-1")
            .title("Test Track")
            .artistName("Artist")
            .build();

    when(delegate.getTrack("t-1")).thenReturn(Optional.of(track));

    Optional<ProviderTrack> first = cachedProvider.getTrack("t-1");
    Optional<ProviderTrack> second = cachedProvider.getTrack("t-1");

    assertThat(first).isPresent().contains(track);
    assertThat(second).isPresent().contains(track);

    verify(delegate, times(1)).getTrack("t-1");
    assertThat(cachedProvider.getCachedTrackCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("search caches result by query and limit")
  void search_cachesResult() {
    ProviderTrack track =
        ProviderTrack.builder().providerTrackId("t-1").title("Hit").artistName("Artist").build();

    when(delegate.search("Hit", 5)).thenReturn(List.of(track));

    List<ProviderTrack> first = cachedProvider.search("Hit", 5);
    List<ProviderTrack> second = cachedProvider.search("Hit", 5);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);

    verify(delegate, times(1)).search("Hit", 5);
  }

  @Test
  @DisplayName("chart caches result by limit")
  void chart_cachesResult() {
    ProviderTrack track =
        ProviderTrack.builder()
            .providerTrackId("c-1")
            .title("Chart 1")
            .artistName("Artist")
            .build();

    when(delegate.chart(10)).thenReturn(List.of(track));

    List<ProviderTrack> first = cachedProvider.chart(10);
    List<ProviderTrack> second = cachedProvider.chart(10);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);

    verify(delegate, times(1)).chart(10);
  }

  @Test
  @DisplayName("playlist caches result by playlistId and limit")
  void playlist_cachesResult() {
    ProviderTrack track =
        ProviderTrack.builder()
            .providerTrackId("p-1")
            .title("Playlist 1")
            .artistName("Artist")
            .build();

    when(delegate.playlist("pl-1", 20)).thenReturn(List.of(track));

    List<ProviderTrack> first = cachedProvider.playlist("pl-1", 20);
    List<ProviderTrack> second = cachedProvider.playlist("pl-1", 20);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);

    verify(delegate, times(1)).playlist("pl-1", 20);
  }

  @Test
  @DisplayName("artistTopTracks caches result by artistId and limit")
  void artistTopTracks_cachesResult() {
    ProviderTrack track =
        ProviderTrack.builder().providerTrackId("a-1").title("Top Song").artistName("Star").build();

    when(delegate.artistTopTracks("art-1", 10)).thenReturn(List.of(track));

    List<ProviderTrack> first = cachedProvider.artistTopTracks("art-1", 10);
    List<ProviderTrack> second = cachedProvider.artistTopTracks("art-1", 10);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);

    verify(delegate, times(1)).artistTopTracks("art-1", 10);
  }

  @Test
  @DisplayName("invalidateAll clears cache and triggers delegate again")
  void invalidateAll_clearsCache() {
    ProviderTrack track =
        ProviderTrack.builder().providerTrackId("t-1").title("Test").artistName("Artist").build();

    when(delegate.getTrack("t-1")).thenReturn(Optional.of(track));

    cachedProvider.getTrack("t-1");
    cachedProvider.invalidateAll();
    cachedProvider.getTrack("t-1");

    verify(delegate, times(2)).getTrack("t-1");
  }

  @Test
  @DisplayName("cache item expires after TTL")
  void cacheTtl_expiresItem() {
    CachedMusicProvider shortLived = new CachedMusicProvider(delegate, Duration.ofMillis(50), 10);

    ProviderTrack track =
        ProviderTrack.builder().providerTrackId("t-ttl").title("TTL").artistName("Artist").build();

    when(delegate.getTrack("t-ttl")).thenReturn(Optional.of(track));

    shortLived.getTrack("t-ttl");

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              shortLived.getTrack("t-ttl");
              verify(delegate, times(2)).getTrack("t-ttl");
            });
  }
}
