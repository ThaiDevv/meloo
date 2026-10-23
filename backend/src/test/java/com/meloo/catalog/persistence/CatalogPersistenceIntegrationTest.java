package com.meloo.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogPersistenceIntegrationTest extends IntegrationTest {

  @Autowired private ArtistRepository artistRepository;
  @Autowired private TrackRepository trackRepository;

  @Test
  @DisplayName("Artist and Track round-trip with MusicProvider enum and char(2) countryCode")
  void artistAndTrackRoundTrip() {
    UUID artistId = UUID.randomUUID();
    Artist artist =
        Artist.builder()
            .id(artistId)
            .name("Son Tung M-TP")
            .nameNormalized("son tung m-tp")
            .provider(MusicProvider.DEEZER)
            .providerArtistId("art-12345")
            .build();
    Artist savedArtist = artistRepository.saveAndFlush(artist);
    assertThat(savedArtist.getCreatedAt()).isNotNull();

    UUID trackId = UUID.randomUUID();
    Track track =
        Track.builder()
            .id(trackId)
            .provider(MusicProvider.DEEZER)
            .providerTrackId("trk-67890")
            .title("Chung Ta Cua Tuong Lai")
            .titleNormalized("chung ta cua tuong lai")
            .primaryArtistId(artistId)
            .countryCode("VN")
            .build();
    Track savedTrack = trackRepository.saveAndFlush(track);
    assertThat(savedTrack.isEnabled()).isTrue();
    assertThat(savedTrack.getCreatedAt()).isNotNull();

    Track loaded = trackRepository.findById(trackId).orElseThrow();
    assertThat(loaded.getTitle()).isEqualTo("Chung Ta Cua Tuong Lai");
    assertThat(loaded.getCountryCode().trim()).isEqualTo("VN");
    assertThat(loaded.getProvider()).isEqualTo(MusicProvider.DEEZER);
  }
}
