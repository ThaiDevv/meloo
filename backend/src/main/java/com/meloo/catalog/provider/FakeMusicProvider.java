package com.meloo.catalog.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FakeMusicProvider implements MusicProvider {

  public static final String PROVIDER_NAME = "FAKE";

  private final Map<String, ProviderTrack> trackMap;
  private final List<ProviderTrack> allTracks;

  public FakeMusicProvider() {
    this(CatalogProviderProperties.FakeProperties.class.getSimpleName());
  }

  public FakeMusicProvider(String mediaLocation) {
    this.allTracks =
        List.of(
            ProviderTrack.builder()
                .providerTrackId("fake-1")
                .title("See Tình")
                .artistName("Hoàng Thùy Linh")
                .artistId("art-1")
                .albumTitle("LINK")
                .durationSeconds(185)
                .previewUrl("/fake-media/sample1.mp3")
                .coverUrl("https://fake-media.meloo.internal/covers/see-tinh.jpg")
                .popularityRank(1)
                .releaseYear((short) 2022)
                .countryCode("VN")
                .build(),
            ProviderTrack.builder()
                .providerTrackId("fake-2")
                .title("Waiting For You")
                .artistName("MONO")
                .artistId("art-2")
                .albumTitle("22")
                .durationSeconds(265)
                .previewUrl("/fake-media/sample2.mp3")
                .coverUrl(null)
                .popularityRank(2)
                .releaseYear((short) 2022)
                .countryCode("VN")
                .build(),
            ProviderTrack.builder()
                .providerTrackId("fake-3")
                .title("Cắt Đôi Nỗi Sầu")
                .artistName("Tăng Duy Tân")
                .artistId("art-3")
                .albumTitle(null)
                .durationSeconds(191)
                .previewUrl("/fake-media/sample3.mp3")
                .coverUrl("https://fake-media.meloo.internal/covers/cat-doi-noi-sau.jpg")
                .popularityRank(3)
                .releaseYear((short) 2023)
                .countryCode("VN")
                .build(),
            ProviderTrack.builder()
                .providerTrackId("fake-4")
                .title("Shape of You")
                .artistName("Ed Sheeran")
                .artistId("art-4")
                .albumTitle("Divide")
                .durationSeconds(233)
                .previewUrl("/fake-media/sample1.mp3")
                .coverUrl(null)
                .popularityRank(4)
                .releaseYear((short) 2017)
                .countryCode("US")
                .build(),
            ProviderTrack.builder()
                .providerTrackId("fake-5")
                .title("Dynamite")
                .artistName("BTS")
                .artistId("art-5")
                .albumTitle("BE")
                .durationSeconds(199)
                .previewUrl("/fake-media/sample2.mp3")
                .coverUrl("https://fake-media.meloo.internal/covers/dynamite.jpg")
                .popularityRank(5)
                .releaseYear((short) 2020)
                .countryCode("KR")
                .build(),
            ProviderTrack.builder()
                .providerTrackId("fake-6")
                .title("Nơi Này Có Anh")
                .artistName("Sơn Tùng M-TP")
                .artistId("art-6")
                .albumTitle("m-tp M-TP")
                .durationSeconds(260)
                .previewUrl("/fake-media/sample3.mp3")
                .coverUrl("https://fake-media.meloo.internal/covers/noi-nay-co-anh.jpg")
                .popularityRank(6)
                .releaseYear((short) 2017)
                .countryCode("VN")
                .build());

    this.trackMap =
        allTracks.stream()
            .collect(Collectors.toMap(ProviderTrack::providerTrackId, track -> track));
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public List<ProviderTrack> search(String query, int limit) {
    if (query == null || query.isBlank() || "*".equals(query.trim())) {
      return allTracks.stream().limit(Math.max(1, limit)).toList();
    }
    String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
    return allTracks.stream()
        .filter(
            track ->
                track.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || track.artistName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
        .limit(Math.max(1, limit))
        .toList();
  }

  @Override
  public Optional<ProviderTrack> getTrack(String providerTrackId) {
    return Optional.ofNullable(trackMap.get(providerTrackId));
  }

  @Override
  public List<ProviderTrack> chart(int limit) {
    return allTracks.stream()
        .sorted(
            Comparator.comparingInt(
                track -> track.popularityRank() != null ? track.popularityRank() : 999))
        .limit(Math.max(1, limit))
        .toList();
  }

  @Override
  public List<ProviderTrack> playlist(String playlistId, int limit) {
    return allTracks.stream().limit(Math.max(1, limit)).toList();
  }

  @Override
  public List<ProviderTrack> artistTopTracks(String artistId, int limit) {
    String normalizedArtist = artistId.trim().toLowerCase(Locale.ROOT);
    return allTracks.stream()
        .filter(
            track ->
                (track.artistId() != null && track.artistId().equalsIgnoreCase(normalizedArtist))
                    || track.artistName().toLowerCase(Locale.ROOT).contains(normalizedArtist))
        .limit(Math.max(1, limit))
        .toList();
  }
}
