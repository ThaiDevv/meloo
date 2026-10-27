package com.meloo.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meloo.IntegrationTest;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Genre;
import com.meloo.catalog.persistence.GenreRepository;
import com.meloo.catalog.persistence.MusicProvider;
import com.meloo.catalog.persistence.Playlist;
import com.meloo.catalog.persistence.PlaylistKind;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.PlaylistTrack;
import com.meloo.catalog.persistence.PlaylistTrackRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class CatalogControllerIntegrationTest extends IntegrationTest {

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "MELOO_JWT_SECRET", () -> "test-meloo-jwt-secret-key-with-minimum-32-bytes-length-xyz");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;

  private String guestAccessToken;

  @BeforeEach
  void setUp() throws Exception {
    playlistTrackRepository.deleteAll();
    trackRepository.deleteAll();
    playlistRepository.deleteAll();
    artistRepository.deleteAll();
    genreRepository.deleteAll();

    MvcResult guestResult =
        mockMvc
            .perform(
                post("/api/v1/auth/guest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\": \"MelooTester\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    guestAccessToken =
        JsonPath.read(guestResult.getResponse().getContentAsString(), "$.accessToken");

    Artist artist =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Sơn Tùng M-TP")
                .nameNormalized("son tung m tp")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-st-web")
                .build());

    Genre genre =
        genreRepository.saveAndFlush(
            Genre.builder()
                .id(UUID.randomUUID())
                .code("vpop")
                .nameVi("V-Pop")
                .nameEn("V-Pop")
                .build());

    Track track =
        trackRepository.saveAndFlush(
            Track.builder()
                .id(UUID.randomUUID())
                .title("Lạc Trôi")
                .titleNormalized("lac troi")
                .primaryArtistId(artist.getId())
                .genreId(genre.getId())
                .countryCode("VN")
                .releaseYear((short) 2017)
                .provider(MusicProvider.DEEZER)
                .providerTrackId("trk-st-web")
                .enabled(true)
                .coverUrl("https://cdn.example.com/lactroi.jpg")
                .build());

    Playlist playlist =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("standard")
                .nameVi("Bộ chuẩn")
                .nameEn("Standard Set")
                .kind(PlaylistKind.STANDARD)
                .enabled(true)
                .build());

    playlistTrackRepository.saveAndFlush(
        PlaylistTrack.builder()
            .playlistId(playlist.getId())
            .trackId(track.getId())
            .position(1)
            .build());
  }

  @Test
  @DisplayName("Unauthenticated requests to catalog endpoints return 401 Unauthorized")
  void unauthenticatedEndpointsReturn401() throws Exception {
    mockMvc
        .perform(get("/api/v1/tracks/search").param("q", "lac troi"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/playlists")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/filters")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /api/v1/tracks/search with auth returns matching track suggestions")
  void searchTracksEndpointWithAuth() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/tracks/search")
                .param("q", "Lạc Trôi")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Lạc Trôi"))
        .andExpect(jsonPath("$.items[0].artistName").value("Sơn Tùng M-TP"));
  }

  @Test
  @DisplayName("GET /api/v1/playlists with auth returns playlists matching contract")
  void listPlaylistsEndpointWithAuth() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/playlists")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].code").value("standard"))
        .andExpect(jsonPath("$.items[0].kind").value("STANDARD"))
        .andExpect(jsonPath("$.items[0].trackCount").value(1));
  }

  @Test
  @DisplayName("GET /api/v1/filters with auth returns filter options matching contract")
  void getFiltersEndpointWithAuth() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/filters").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.genres[0].code").value("vpop"))
        .andExpect(jsonPath("$.countries[0]").value("VN"))
        .andExpect(jsonPath("$.decades[0]").value(2010))
        .andExpect(jsonPath("$.artists[0].name").value("Sơn Tùng M-TP"));
  }
}
