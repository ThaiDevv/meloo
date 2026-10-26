package com.meloo.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackTest {

  @Test
  @DisplayName("Track entity builder sets defaults and stores coverUrl and lastPreviewCheckedAt")
  void trackEntityBuilderAndAccessors() {
    UUID id = UUID.randomUUID();
    UUID artistId = UUID.randomUUID();
    UUID genreId = UUID.randomUUID();
    Instant checkedAt = Instant.now();

    Track track =
        Track.builder()
            .id(id)
            .provider(MusicProvider.DEEZER)
            .providerTrackId("dz-test-1")
            .title("Test Title")
            .titleNormalized("test title")
            .primaryArtistId(artistId)
            .genreId(genreId)
            .countryCode("VN")
            .releaseYear((short) 2024)
            .popularityRank(85)
            .coverUrl("https://cdns.dzcdn.net/cover/test.jpg")
            .lastPreviewCheckedAt(checkedAt)
            .build();

    assertThat(track.getId()).isEqualTo(id);
    assertThat(track.getProvider()).isEqualTo(MusicProvider.DEEZER);
    assertThat(track.getProviderTrackId()).isEqualTo("dz-test-1");
    assertThat(track.getTitle()).isEqualTo("Test Title");
    assertThat(track.getTitleNormalized()).isEqualTo("test title");
    assertThat(track.getPrimaryArtistId()).isEqualTo(artistId);
    assertThat(track.getGenreId()).isEqualTo(genreId);
    assertThat(track.getCountryCode()).isEqualTo("VN");
    assertThat(track.getReleaseYear()).isEqualTo((short) 2024);
    assertThat(track.getPopularityRank()).isEqualTo(85);
    assertThat(track.getCoverUrl()).isEqualTo("https://cdns.dzcdn.net/cover/test.jpg");
    assertThat(track.getLastPreviewCheckedAt()).isEqualTo(checkedAt);
    assertThat(track.isEnabled()).isTrue();
    assertThat(track.getDisabledReason()).isNull();
    assertThat(track.isNew()).isTrue();

    track.prePersist();
    assertThat(track.getCreatedAt()).isNotNull();

    track.markNotNew();
    assertThat(track.isNew()).isFalse();

    track.setEnabled(false);
    track.setDisabledReason("Preview broken");
    assertThat(track.isEnabled()).isFalse();
    assertThat(track.getDisabledReason()).isEqualTo("Preview broken");
  }
}
